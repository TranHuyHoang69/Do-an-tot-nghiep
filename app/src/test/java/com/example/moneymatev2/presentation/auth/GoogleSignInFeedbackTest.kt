package com.example.moneymatev2.presentation.auth

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInFeedbackTest {
    @Test fun onlyExplicitGoogleCancellationIsSilent() {
        assertTrue(isGoogleSignInCancelled(GoogleSignInStatusCodes.SIGN_IN_CANCELLED))
        assertFalse(isGoogleSignInCancelled(GoogleSignInStatusCodes.SIGN_IN_FAILED))
        assertFalse(isGoogleSignInCancelled(CommonStatusCodes.NETWORK_ERROR))
        assertFalse(isGoogleSignInCancelled(CommonStatusCodes.CANCELED))
    }
}
