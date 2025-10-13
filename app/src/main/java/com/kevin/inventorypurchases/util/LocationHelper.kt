package com.kevin.inventorypurchases.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    suspend fun getBestEffortLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Android 11+ has getCurrentLocation, which is perfect for a one-shot.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return null

            return suspendCancellableCoroutine { cont ->
                val exec = Executors.newSingleThreadExecutor()
                lm.getCurrentLocation(provider, /* cancellationSignal = */ null, exec) { loc ->
                    cont.resume(loc)
                    exec.shutdown()
                }
                cont.invokeOnCancellation { exec.shutdownNow() }
            }
        }

        // Older devices: fall back to last known (best-effort).
        val candidates = buildList {
            runCatching { add(lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)) }
            runCatching { add(lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)) }
        }.filterNotNull()

        return candidates.maxByOrNull { it.time }
    }

    /** Formats a concise text payload for the zip. */
    fun toLocationTxt(location: Location?): String =
        if (location != null) {
            // ISO-ish timestamp via millis; feel free to pretty-print if you prefer
            "lat=${location.latitude}, lon=${location.longitude}, " +
                    "accuracyMeters=${location.accuracy}, " +
                    "provider=${location.provider}, " +
                    "timestampMs=${location.time}\n"
        } else {
            "UNAVAILABLE\n"
        }
}
