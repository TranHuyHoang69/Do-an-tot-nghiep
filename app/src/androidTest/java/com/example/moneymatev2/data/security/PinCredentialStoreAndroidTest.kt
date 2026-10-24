package com.example.moneymatev2.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Uses a random test UID; never reads or changes a real account's PIN. */
@RunWith(AndroidJUnit4::class)
class PinCredentialStoreAndroidTest {
    @Test fun verifierPersistsWithoutPlaintextAndRecoveryRequiresSignOut() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = "security-test-${UUID.randomUUID()}"
        val store = PinCredentialStore(context)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        val record = File(context.noBackupFilesDir, "pin_$digest.json")
        try {
            assertEquals(PinRecordStatus.None, store.status(uid))
            assertTrue(store.create(uid, "1472"))
            assertFalse(record.readText().contains("1472"))
            assertEquals(PinRecordStatus.Present(false), PinCredentialStore(context).status(uid))
            assertEquals(PinCheck.WRONG, store.check(uid, "0000"))
            assertEquals(PinCheck.SUCCESS, store.check(uid, "1472"))
            assertTrue(store.setBiometric(uid, true))
            assertEquals(PinRecordStatus.Present(true), store.status(uid))
            assertTrue(store.replace(uid, "2583"))
            assertEquals(PinCheck.WRONG, store.check(uid, "1472"))
            assertEquals(PinCheck.SUCCESS, store.check(uid, "2583"))
            repeat(5) { assertEquals(PinCheck.WRONG, store.check(uid, "0000", now = 1_000L)) }
            assertEquals(PinCheck.LOCKED, store.check(uid, "2583", now = 1_001L))
            assertEquals(PinCheck.SUCCESS, store.check(uid, "2583", now = 31_001L))
            assertTrue(store.requestRecovery(uid))
            assertFalse(store.consumeRecovery(uid))
            assertEquals(PinRecordStatus.Present(true), store.status(uid))
            store.markRecoverySignedOut()
            assertTrue(store.consumeRecovery(uid))
            assertEquals(PinRecordStatus.None, store.status(uid))
            assertFalse(store.recoveryPending())
        } finally {
            store.remove(uid)
            store.cancelRecovery()
        }
    }

    @Test fun sameDeviceAccountsCannotUseEachOthersPin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val a = "security-test-${UUID.randomUUID()}"
        val b = "security-test-${UUID.randomUUID()}"
        val store = PinCredentialStore(context)
        try {
            assertTrue(store.create(a, "1472"))
            assertTrue(store.create(b, "2583"))
            assertEquals(PinCheck.WRONG, store.check(a, "2583"))
            assertEquals(PinCheck.WRONG, store.check(b, "1472"))
            assertTrue(store.remove(a))
            assertEquals(PinRecordStatus.Present(false), store.status(b))
            assertEquals(PinCheck.SUCCESS, store.check(b, "2583"))
        } finally {
            store.remove(a)
            store.remove(b)
        }
    }

    @Test fun existingSixDigitRecordCanBeVerifiedAndMigratedWithoutReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uid = "security-test-${UUID.randomUUID()}"
        val store = PinCredentialStore(context)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        val record = File(context.noBackupFilesDir, "pin_$digest.json")
        try {
            assertTrue(store.create(uid, "1472"))
            val json = JSONObject(record.readText())
            val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
            val secret = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey("moneymate_pin_$digest", null) as SecretKey
            val spec = PBEKeySpec("147258".toCharArray(), salt, json.getInt("iterations"), 256)
            val derived = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally { spec.clearPassword() }
            val verifier = try {
                Mac.getInstance("HmacSHA256").apply { init(secret) }.doFinal(salt + derived)
            } finally { derived.fill(0) }
            json.put("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
            json.remove("pinLength") // The pre-migration record has no length field.
            record.writeText(json.toString())

            assertEquals(PinRecordStatus.Present(false, 6), store.status(uid))
            assertEquals(PinCheck.WRONG, store.check(uid, "1472"))
            assertEquals(PinCheck.SUCCESS, store.check(uid, "147258"))
            assertTrue(store.replace(uid, "2583"))
            assertEquals(PinRecordStatus.Present(false, 4), store.status(uid))
            assertEquals(PinCheck.SUCCESS, store.check(uid, "2583"))
            assertEquals(PinCheck.WRONG, store.check(uid, "147258"))
        } finally {
            store.remove(uid)
        }
    }
}
