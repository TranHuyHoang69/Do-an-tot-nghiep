package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.CloudPullReport

/** Offline-first remote reconciliation contract. Domain has no Firebase or Room imports. */
interface CloudPullRepository {
    suspend fun pull(userId: String): CloudPullReport
}
