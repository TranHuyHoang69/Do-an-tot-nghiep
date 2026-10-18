package com.example.moneymatev2.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoryIdsTest {

    @Test
    fun defaultIdsAreAccountScoped() {
        val accountA = DefaultCategoryIds.forUser("account-a", "spend_food")
        val accountB = DefaultCategoryIds.forUser("account-b", "spend_food")

        assertNotEquals(accountA, accountB)
    }

    @Test
    fun defaultIdsAreStableForSameAccountAndStableId() {
        val first = DefaultCategoryIds.forUser("account-a", "spend_food")
        val second = DefaultCategoryIds.forUser("account-a", "spend_food")

        assertEquals(first, second)
    }

    @Test
    fun defaultIdsDoNotReuseLegacyGlobalIds() {
        val id = DefaultCategoryIds.forUser("account-a", "spend_food")

        assertNotEquals(DefaultCategoryIds.SPEND_FOOD, id)
        assertTrue(id.startsWith("default_spend_food_"))
    }
}
