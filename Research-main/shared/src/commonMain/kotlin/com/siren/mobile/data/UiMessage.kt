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
