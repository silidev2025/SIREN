package com.siren.mobile.platform

/**
 * Implemented by the host Activity so the platform layer can ask for location permission.
 *
 * The same indirection [SmsPermissionRequester] uses, for the same reason: the launcher has
 * to be registered before the Activity finishes being created, so it cannot live in
 * [AndroidPlatformServices].
 */
interface LocationPermissionRequester {

    /**
     * Requests fine and coarse location together, suspending until the user answers, and
     * returns whether either was granted — Android 12+ lets the user choose "approximate",
     * and an approximate position is still worth sharing with a guardian.
     */
    suspend fun requestLocationPermission(): Boolean
}
