package com.example.moneymatev2.presentation.budget

internal object BudgetLimitParser {
    sealed interface Result {
        data class Valid(val amount: Long) : Result
        data object Empty : Result
        data object Invalid : Result
        data object NonPositive : Result
        data object OutOfRange : Result
    }

    fun parse(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Empty
        val digits = when {
            text.all { it in '0'..'9' } -> text
            text.matches(Regex("[0-9]{1,3}(\\.[0-9]{3})+")) -> text.replace(".", "")
            else -> return Result.Invalid
        }
        val significant = digits.trimStart('0')
        if (significant.isEmpty()) return Result.NonPositive
        if (significant.length > 19) return Result.OutOfRange
        return Result.Valid(significant.toLongOrNull() ?: return Result.OutOfRange)
    }
}
