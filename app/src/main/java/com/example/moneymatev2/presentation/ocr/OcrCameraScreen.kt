package com.example.moneymatev2.presentation.ocr

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.moneymatev2.R
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal fun captureFlashMode(enabled: Boolean, hasFlash: Boolean): Int =
    if (enabled && hasFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OcrCameraScreen(onClose: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().build().apply {
        flashMode = ImageCapture.FLASH_MODE_OFF
    } }
    val cameraSession = remember { AtomicInteger(0) }
    var hasFlash by remember { mutableStateOf<Boolean?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        File(context.cacheDir, "ocr_capture").listFiles()
            ?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
    }
    LaunchedEffect(flashOn, hasFlash, imageCapture) {
        imageCapture.flashMode = captureFlashMode(flashOn, hasFlash == true)
    }
    LaunchedEffect(hasFlash) {
        if (hasFlash != true) flashOn = false
    }
    DisposableEffect(context, lifecycleOwner, previewView, preview, imageCapture) {
        cameraSession.incrementAndGet()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (disposed) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    preview.surfaceProvider = previewView.surfaceProvider
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                    hasFlash = camera.cameraInfo.hasFlashUnit()
                    ready = true
                } catch (_: Exception) {
                    cameraError = true
                    ready = false
                }
            }, executor)
        } catch (_: Exception) {
            cameraError = true
        }
        onDispose {
            disposed = true
            cameraSession.incrementAndGet()
            ready = false
            provider?.unbind(preview, imageCapture)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.ocr_camera_title)) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ocr_back))
                }
            },
            actions = {
                IconButton(onClick = { flashOn = !flashOn }, enabled = hasFlash == true && !capturing) {
                    Icon(if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        stringResource(if (flashOn) R.string.ocr_flash_on else R.string.ocr_flash_off))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (!ready && !cameraError) CircularProgressIndicator(Modifier.align(Alignment.Center))
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasFlash == false) Text(stringResource(R.string.ocr_flash_unavailable),
                    color = Color.White)
                if (cameraError || captureError) Text(stringResource(R.string.ocr_camera_error),
                    color = MaterialTheme.colorScheme.error)
                FilledIconButton(
                    onClick = {
                        if (!ready || capturing) return@FilledIconButton
                        capturing = true
                        captureError = false
                        var file: File? = null
                        try {
                            val dir = File(context.cacheDir, "ocr_capture").apply { mkdirs() }
                            val capturedFile = File.createTempFile("receipt_", ".jpg", dir)
                            file = capturedFile
                            val captureSession = cameraSession.get()
                            imageCapture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                            imageCapture.flashMode = captureFlashMode(flashOn, hasFlash == true)
                            imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(capturedFile).build(), executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        capturing = false
                                        if (cameraSession.get() == captureSession) onCaptured(capturedFile)
                                        else capturedFile.delete()
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        capturedFile.delete()
                                        capturing = false
                                        captureError = true
                                    }
                                })
                        } catch (_: Exception) {
                            file?.delete()
                            capturing = false
                            captureError = true
                        }
                    },
                    enabled = ready && !capturing,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, stringResource(R.string.ocr_take_photo),
                        modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
