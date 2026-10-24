package com.example.moneymatev2.presentation.security

import com.example.moneymatev2.data.security.PinCheck
import com.example.moneymatev2.data.security.PinCredentialStore
import com.example.moneymatev2.data.security.PinRecordStatus
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class LocalSecurityManagerTest {
    private val auth = mock(AuthRepository::class.java)
    private val store = mock(PinCredentialStore::class.java)
    private val account = MutableStateFlow<UserModel?>(null)
    private var currentUid: String? = null

    private fun manager(): LocalSecurityManager {
        `when`(auth.observeAuthState()).thenReturn(account)
        `when`(auth.getCurrentUserId()).thenAnswer { currentUid }
        return LocalSecurityManager(auth, store)
    }

    private suspend fun LocalSecurityManager.active(uid: String): LocalSecurityState.Active =
        withTimeout(5_000) { state.first { it is LocalSecurityState.Active && it.uid == uid } as LocalSecurityState.Active }

    @Test fun createChangeDeleteAndWrongPinDoNotBypassVerification() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.None)
        `when`(store.create("a", "1237")).thenReturn(true)
        `when`(store.check("a", "0000")).thenReturn(PinCheck.WRONG)
        `when`(store.check("a", "1237")).thenReturn(PinCheck.SUCCESS)
        `when`(store.check("a", "9873")).thenReturn(PinCheck.SUCCESS)
        `when`(store.replace("a", "9873")).thenReturn(true)
        `when`(store.remove("a")).thenReturn(true)
        `when`(store.setBiometric("a", true)).thenReturn(true)
        val manager = manager()
        assertFalse(manager.active("a").hasPin)
        assertEquals(SecurityResult.INVALID_PIN, manager.create("123", "123"))
        assertEquals(SecurityResult.MISMATCH, manager.create("1237", "1238"))
        assertEquals(SecurityResult.INVALID_PIN, manager.create("123789", "123789"))
        assertEquals(SecurityResult.SUCCESS, manager.create("1237", "1237"))
        assertTrue(manager.active("a").hasPin)
        assertEquals(SecurityResult.SUCCESS, manager.setBiometric(true))
        assertEquals(SecurityResult.WRONG_PIN, manager.verify("0000"))
        assertEquals(SecurityResult.WRONG_PIN, manager.change("0000", "9873", "9873"))
        assertEquals(SecurityResult.SUCCESS, manager.change("1237", "9873", "9873"))
        assertEquals(SecurityResult.WRONG_PIN, manager.delete("0000"))
        assertEquals(SecurityResult.SUCCESS, manager.delete("9873"))
        assertFalse(manager.active("a").hasPin)
        assertFalse(manager.active("a").biometricEnabled)
        verify(store, never()).replace("a", "0000")
        Unit
    }

    @Test fun processStartAndAccountSwitchRequireEachAccountsOwnUnlock() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.Present(true))
        `when`(store.status("b")).thenReturn(PinRecordStatus.Present(false))
        `when`(store.check("a", "4444")).thenReturn(PinCheck.SUCCESS)
        `when`(store.check("b", "4444")).thenReturn(PinCheck.WRONG)
        val manager = manager()
        assertFalse(manager.active("a").unlocked)
        assertEquals(SecurityResult.SUCCESS, manager.verify("4444"))
        assertTrue(manager.active("a").unlocked)
        manager.onBackground(1_000L)
        manager.onForeground(30_999L)
        assertTrue(manager.active("a").unlocked)
        manager.onBackground(50_000L)
        manager.onForeground(80_000L)
        assertFalse(manager.active("a").unlocked)
        assertTrue(manager.unlockWithBiometric("a"))
        currentUid = "b"
        account.value = UserModel("b", "b@example.com", null, null)
        val b = manager.active("b")
        assertFalse(b.unlocked)
        assertFalse(manager.unlockWithBiometric("a"))
        assertEquals(SecurityResult.WRONG_PIN, manager.verify("4444"))
        currentUid = null
        account.value = null
        withTimeout(5_000) { manager.state.first { it is LocalSecurityState.Guest } }
        assertFalse(manager.unlockWithBiometric("a"))
        Unit
    }

    @Test fun legacySixDigitPinMustBeVerifiedBeforeReplacingWithFourDigits() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.Present(false, 6))
        `when`(store.check("a", "123456")).thenReturn(PinCheck.SUCCESS)
        `when`(store.check("a", "1234")).thenReturn(PinCheck.WRONG)
        `when`(store.replace("a", "2583")).thenReturn(true)
        val manager = manager()
        assertEquals(6, manager.active("a").pinLength)
        assertEquals(SecurityResult.WRONG_PIN, manager.verify("1234"))
        assertFalse(manager.active("a").unlocked)
        assertEquals(SecurityResult.INVALID_PIN, manager.change("123456", "258369", "258369"))
        assertEquals(SecurityResult.SUCCESS, manager.change("123456", "2583", "2583"))
        assertEquals(4, manager.active("a").pinLength)
        verify(store).replace("a", "2583")
        Unit
    }

    @Test fun missingKeystoreLocksUiUntilRecovery() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.Unavailable)
        val manager = manager()
        val state = manager.active("a")
        assertTrue(state.hasPin)
        assertTrue(state.needsRecovery)
        assertFalse(state.unlocked)
        assertFalse(manager.unlockWithBiometric("a"))
        Unit
    }

    @Test fun biometricUnlockRequiresStoredOptInAndOnlyWorksForCurrentOwner() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.Present(false))
        `when`(store.check("a", "1472")).thenReturn(PinCheck.SUCCESS)
        `when`(store.setBiometric("a", true)).thenReturn(true)
        `when`(store.setBiometric("a", false)).thenReturn(true)
        val manager = manager()
        manager.active("a")
        assertFalse(manager.unlockWithBiometric("a"))
        assertEquals(SecurityResult.WRONG_ACCOUNT, manager.setBiometric(true))
        assertEquals(SecurityResult.SUCCESS, manager.verify("1472"))
        assertEquals(SecurityResult.SUCCESS, manager.setBiometric(true))
        manager.onBackground(1_000L)
        manager.onForeground(31_000L)
        assertFalse(manager.active("a").unlocked)
        assertTrue(manager.unlockWithBiometric("a"))
        assertEquals(SecurityResult.SUCCESS, manager.setBiometric(false))
        manager.onBackground(32_000L)
        manager.onForeground(62_000L)
        assertFalse(manager.unlockWithBiometric("a"))
        Unit
    }

    @Test fun newProcessManagerDoesNotReuseOldUnlockSession() = runBlocking {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", null, null)
        `when`(store.status("a")).thenReturn(PinRecordStatus.Present(false))
        `when`(store.check("a", "1472")).thenReturn(PinCheck.SUCCESS)
        val first = manager()
        first.active("a")
        assertEquals(SecurityResult.SUCCESS, first.verify("1472"))
        assertTrue(first.active("a").unlocked)
        val restarted = manager()
        assertFalse(restarted.active("a").unlocked)
        Unit
    }
}
