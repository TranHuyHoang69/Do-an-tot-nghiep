package com.example.moneymatev2.domain.model

data class Money(
    val amountMinor: Long,
    val currency: String
){
    operator fun plus(other: Money): Money{
        require(currency == other.currency){"Không thể cộng 2 loại tiền tệ khác nhau: $currency và ${other.currency}"}
        return copy(amountMinor = amountMinor + other.amountMinor)
    }

    operator fun minus(other: Money): Money{
        require(currency == other.currency){"Không thể trừ 2 loại tiền tệ khác nhau: $currency và ${other.currency}"}
        return copy(amountMinor = amountMinor - other.amountMinor)
    }

    companion object{
        fun zero(currency: String) = Money(0, currency)
    }

}