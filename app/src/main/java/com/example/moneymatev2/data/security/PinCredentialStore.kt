package com.example.moneymatev2.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

internal const val CURRENT_PIN_LENGTH = 4
internal const val LEGACY_PIN_LENGTH = 6

internal sealed interface PinRecordStatus {
    data object None : PinRecordStatus
    data class Present(val biometricEnabled: Boolean, val pinLength: Int = CURRENT_PIN_LENGTH) : PinRecordStatus
    data object Unavailable : PinRecordStatus
}

internal enum class PinCheck { SUCCESS, WRONG, LOCKED, NO_PIN, UNAVAILABLE }

/** Only the salted, Keystore-peppered verifier is stored; the record is excluded from backup. */
@Singleton
class PinCredentialStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val random = SecureRandom()
    private val iterations = 180_000
    private val recoveryFile get() = File(context.noBackupFilesDir, "pin_recovery")

    private data class Record(
        val salt: ByteArray, val verifier: ByteArray, val iterations: Int,
        val biometric: Boolean, val failures: Int, val lockedUntil: Long, val pinLength: Int
    )

    internal fun status(uid: String): PinRecordStatus {
        if (!recordExists(uid)) return PinRecordStatus.None
        return try {
            val record = read(uid)
            if (key(uid) == null) PinRecordStatus.Unavailable
            else PinRecordStatus.Present(record.biometric, record.pinLength)
        } catch (_: Exception) {
            PinRecordStatus.Unavailable
        }
    }

    fun create(uid: String, pin: String): Boolean {
        if (!validPin(pin) || recordExists(uid)) return false
        val salt = ByteArray(32).also(random::nextBytes)
        return try {
            val secret = createKey(uid)
            write(uid, Record(salt, verifier(pin, salt, iterations, secret), iterations, false, 0, 0L,
                CURRENT_PIN_LENGTH))
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun check(uid: String, pin: String): PinCheck = check(uid, pin, System.currentTimeMillis())

    internal fun check(uid: String, pin: String, now: Long): PinCheck {
        if (!recordExists(uid)) return PinCheck.NO_PIN
        val record = try { read(uid) } catch (_: Exception) { return PinCheck.UNAVAILABLE }
        if (record.lockedUntil > now) return PinCheck.LOCKED
        val secret = try { key(uid) } catch (_: Exception) { null } ?: return PinCheck.UNAVAILABLE
        val matches = try {
            validPin(pin, record.pinLength) &&
                MessageDigest.isEqual(record.verifier, verifier(pin, record.salt, record.iterations, secret))
        } catch (_: Exception) { return PinCheck.UNAVAILABLE }
        return try {
            if (matches) {
                write(uid, record.copy(failures = 0, lockedUntil = 0L))
                PinCheck.SUCCESS
            } else {
                val failures = (record.failures + 1).coerceAtMost(100)
                val delay = if (failures >= 5) (30_000L * (1L shl (failures - 5).coerceAtMost(7))).coerceAtMost(3_600_000L) else 0L
                write(uid, record.copy(failures = failures, lockedUntil = now + delay))
                PinCheck.WRONG
            }
        } catch (_: Exception) { PinCheck.UNAVAILABLE }
    }

    fun replace(uid: String, newPin: String): Boolean {
        if (!validPin(newPin)) return false
        return try {
            val current = read(uid)
            val secret = key(uid) ?: return false
            val salt = ByteArray(32).also(random::nextBytes)
            write(uid, current.copy(salt = salt, verifier = verifier(newPin, salt, iterations, secret),
                iterations = iterations, failures = 0, lockedUntil = 0L, pinLength = CURRENT_PIN_LENGTH))
            true
        } catch (_: Exception) { false }
    }

    fun setBiometric(uid: String, enabled: Boolean): Boolean = try {
        val record = read(uid)
        write(uid, record.copy(biometric = enabled))
        true
    } catch (_: Exception) { false }

    fun remove(uid: String): Boolean {
        try { AtomicFile(file(uid)).delete() } catch (_: Exception) { return false }
        if (recordExists(uid)) return false
        return try {
            val store = keystore()
            if (store.containsAlias(alias(uid))) store.deleteEntry(alias(uid))
            true
        } catch (_: Exception) {
            // No verifier remains; an orphaned key cannot unlock another account.
            true
        }
    }

    /** Recovery is completed only after Firebase signs in the same account again. */
    private fun writeRecovery(value: String): Boolean = try {
        val atomic = AtomicFile(recoveryFile)
        val stream = atomic.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.US_ASCII))
            atomic.finishWrite(stream)
            true
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    } catch (_: Exception) { false }

    fun requestRecovery(uid: String): Boolean = writeRecovery("pending:${digest(uid)}")

    fun recoveryPending(): Boolean = recoveryFile.exists() || File(recoveryFile.path + ".bak").exists()

    fun cancelRecovery() { AtomicFile(recoveryFile).delete() }

    fun markRecoverySignedOut() {
        if (!recoveryPending()) return
        val value = try { AtomicFile(recoveryFile).readFully().toString(Charsets.US_ASCII) }
            catch (_: Exception) { return }
        if (value.startsWith("pending:")) writeRecovery("ready:${value.removePrefix("pending:")}")
    }

    fun consumeRecovery(uid: String): Boolean {
        if (!recoveryPending()) return false
        val owner = try { AtomicFile(recoveryFile).readFully().toString(Charsets.US_ASCII) }
            catch (_: Exception) { return false }
        if (owner != "ready:${digest(uid)}" || !remove(uid)) return false
        cancelRecovery()
        return true
    }

    private fun validPin(pin: String, length: Int = CURRENT_PIN_LENGTH) =
        pin.length == length && pin.all { it in '0'..'9' }
    private fun file(uid: String) = File(context.noBackupFilesDir, "pin_${digest(uid)}.json")
    private fun recordExists(uid: String): Boolean {
        val base = file(uid)
        return base.exists() || File(base.path + ".bak").exists()
    }
    private fun alias(uid: String) = "moneymate_pin_${digest(uid)}"
    private fun digest(uid: String) = MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(uid: String): SecretKey? = keystore().getKey(alias(uid), null) as? SecretKey
    private fun createKey(uid: String): SecretKey {
        val store = keystore()
        if (store.containsAlias(alias(uid))) store.deleteEntry(alias(uid))
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias(uid), KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256).build())
        return generator.generateKey()
    }

    private fun verifier(pin: String, salt: ByteArray, rounds: Int, secret: SecretKey): ByteArray {
        if (rounds !in 100_000..500_000) error("Invalid PIN record")
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, 256)
        val derived = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            Mac.getInstance("HmacSHA256").apply { init(secret) }.doFinal(salt + derived)
        } finally { derived.fill(0) }
    }

    private fun read(uid: String): Record {
        val json = JSONObject(AtomicFile(file(uid)).readFully().toString(Charsets.UTF_8))
        val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
        val verifier = Base64.decode(json.getString("verifier"), Base64.NO_WRAP)
        require(salt.size == 32 && verifier.size == 32)
        val pinLength = json.optInt("pinLength", LEGACY_PIN_LENGTH)
        require(pinLength == CURRENT_PIN_LENGTH || pinLength == LEGACY_PIN_LENGTH)
        return Record(salt, verifier, json.getInt("iterations"), json.optBoolean("biometric", false),
            json.optInt("failures", 0), json.optLong("lockedUntil", 0L), pinLength)
    }

    private fun write(uid: String, record: Record) {
        val json = JSONObject().put("salt", Base64.encodeToString(record.salt, Base64.NO_WRAP))
            .put("verifier", Base64.encodeToString(record.verifier, Base64.NO_WRAP))
            .put("iterations", record.iterations).put("biometric", record.biometric)
            .put("failures", record.failures).put("lockedUntil", record.lockedUntil)
            .put("pinLength", record.pinLength)
        val atomic = AtomicFile(file(uid))
        val output = atomic.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }
}
