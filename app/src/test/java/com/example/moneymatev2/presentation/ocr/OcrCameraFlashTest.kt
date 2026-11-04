package com.example.moneymatev2.presentation.ocr

import androidx.camera.core.ImageCapture
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCameraFlashTest {
    @Test fun flashStartsOffAndTogglesCaptureMode() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, captureFlashMode(false, true))
        assertEquals(ImageCapture.FLASH_MODE_ON, captureFlashMode(true, true))
        assertEquals(ImageCapture.FLASH_MODE_OFF, captureFlashMode(false, true))
    }

    @Test fun missingFlashUnitForcesCaptureModeOff() {
        assertEquals(ImageCapture.FLASH_MODE_OFF, captureFlashMode(true, false))
    }
}
