package com.example.moneymatev2.presentation.security

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(private val security: LocalSecurityManager) : ViewModel() {
    val securityState = security.state
    suspend fun create(pin: String, confirm: String) = security.create(pin, confirm)
    suspend fun change(old: String, new: String, confirm: String) = security.change(old, new, confirm)
    suspend fun delete(pin: String) = security.delete(pin)
    suspend fun verify(pin: String) = security.verify(pin)
    suspend fun toggleBiometric(enabled: Boolean) = security.setBiometric(enabled)
    fun unlockWithBiometric(uid: String) = security.unlockWithBiometric(uid)
    suspend fun beginRecovery() = security.beginRecovery()
}
