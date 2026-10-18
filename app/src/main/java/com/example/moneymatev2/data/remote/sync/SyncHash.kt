package com.example.moneymatev2.data.remote.sync

import java.security.MessageDigest

fun String.safeHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
    return digest.take(4).joinToString("") { "%02x".format(it) }
}
