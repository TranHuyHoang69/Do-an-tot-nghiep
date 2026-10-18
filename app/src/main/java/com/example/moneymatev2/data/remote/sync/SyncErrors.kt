package com.example.moneymatev2.data.remote.sync

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException

internal fun Exception.isPermanentSyncError(): Boolean = when (this) {
    is CloudPullService.CloudPullException -> isPermanent
    is FirebaseNetworkException -> false
    is FirebaseFirestoreException -> code in setOf(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        FirebaseFirestoreException.Code.INVALID_ARGUMENT,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION
    )
    is IllegalArgumentException -> true
    else -> false
} || suppressed.filterIsInstance<Exception>().any { it.isPermanentSyncError() }

// SDK messages can contain document paths or user-supplied values.
internal fun Exception.syncFailureSummary(): String =
    if (this is CloudPullService.CloudPullException) {
        "type=CloudPullException collections=${failures.joinToString { "${it.collection}:${it.type}:permanent=${it.permanent}" }}"
    } else {
        "type=${javaClass.simpleName} reason=${(this as? FirebaseFirestoreException)?.code?.name ?: "request_failed"}"
    }
