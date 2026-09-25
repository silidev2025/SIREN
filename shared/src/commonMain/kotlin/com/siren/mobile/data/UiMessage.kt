package com.siren.mobile.data

data class UiMessage(
    val text: String,
    val isError: Boolean = false,
)

sealed interface LinkResult {

    data class Requested(val studentName: String) : LinkResult

    data class AlreadyRequested(val studentName: String) : LinkResult

    data class Success(val studentName: String) : LinkResult
    data object NotFound : LinkResult
    data object AlreadyLinked : LinkResult
    data class Failed(val reason: String) : LinkResult
}

/** Outcome of signing in with a phone code. */
enum class PhoneLoginResult {
    SIGNED_IN,

    /** Wrong or expired code, or a network error — the same code screen can try again. */
    RETRY,

    /** The number verified but has no SIREN profile; the user was signed back out. */
    NO_ACCOUNT,
}
