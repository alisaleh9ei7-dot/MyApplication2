package com.example.myapplication.data.v8.error

import kotlinx.coroutines.CancellationException

/** Parses only the explicit PHARMACY_V8 token; it never guesses from localized text. */
object V8DatabaseErrorParser {
    private val tokenRegex = Regex("(?:^|[^A-Z0-9_])PHARMACY_V8:(E_[A-Z0-9_]+)(?:$|[^A-Z0-9_])")

    fun parse(throwable: Throwable): SaleError {
        val chain = generateSequence(throwable) { it.cause }.toList()
        chain.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
        val rawMessage = chain.mapNotNull { it.message }.joinToString(" | ")
            .ifBlank { throwable::class.qualifiedName ?: "Unknown throwable" }

        val token = chain.asSequence()
            .mapNotNull { it.message }
            .mapNotNull { message -> tokenRegex.find(message)?.groupValues?.get(1) }
            .firstOrNull()

        val code = token?.let(V8ErrorCode::fromToken)
        return if (code != null) {
            SaleError.Known(code = code, rawMessage = rawMessage, cause = throwable)
        } else {
            SaleError.Unknown(rawMessage = rawMessage, cause = throwable)
        }
    }
}
