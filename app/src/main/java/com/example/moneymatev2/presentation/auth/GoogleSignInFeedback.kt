package com.example.moneymatev2.presentation.auth

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes

/** Only the SDK's explicit cancellation status is silent; other failures need feedback. */
internal fun isGoogleSignInCancelled(statusCode: Int): Boolean =
    statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED
