package com.siren.mobile.platform

/**
 * Implemented by the host Activity so the platform layer can ask for SEND_SMS.
 *
 * The same indirection profile photos and phone verification use, and for the same reason:
 * `registerForActivityResult` has to be called before the Activity finishes being created,
 * so the launcher cannot live in [AndroidPlatformServices], which is constructed later.
 */
interface SmsPermissionRequester {

    /** True if SEND_SMS is already granted, without prompting. */
    fun hasSmsPermission(): Boolean

    /**
     * Requests SEND_SMS, suspending until the user answers, and returns whether it was
     * granted. Returns the current state immediately if it has already been granted, or if
     * the OS declines to show the dialog at all.
     */
    suspend fun requestSmsPermission(): Boolean
}
