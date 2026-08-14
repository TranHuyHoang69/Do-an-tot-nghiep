package com.example.moneymatev2.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth
){
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
}