/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.time.Instant

internal data class LocationReport(val status: String = "UNAVAILABLE", val fix: LocationFix? = null)
internal data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val capturedAt: String)

/** Optional foreground-only telemetry. Denying permission never prevents scanning. */
internal class ForegroundLocationState
{
   var report by mutableStateOf(LocationReport())
   var permissionRevision by mutableStateOf(0)
}

@Composable
internal fun rememberForegroundLocation(active: Boolean): ForegroundLocationState
{
   val context = LocalContext.current
   val state = remember { ForegroundLocationState() }
   val manager = remember { context.getSystemService(LocationManager::class.java) }
   val permissionRevision = state.permissionRevision
   val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
   DisposableEffect(active, permitted, permissionRevision)
   {
      val listener = object : LocationListener
      {
         override fun onLocationChanged(location: Location)
         {
            if(!location.hasAccuracy()) return
            state.report = LocationReport("AVAILABLE", LocationFix(location.latitude, location.longitude,
               location.accuracy.toDouble(), Instant.ofEpochMilli(location.time).toString()))
         }
         override fun onProviderEnabled(provider: String) { state.report = LocationReport() }
         override fun onProviderDisabled(provider: String)
         {
            state.report = LocationReport(if(manager.getProviders(true).any { it == "gps" || it == "network" }) "UNAVAILABLE" else "DISABLED")
         }
         @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
      }
      state.report = LocationReport(if(!permitted) "PERMISSION_DENIED" else "UNAVAILABLE")
      if(active && permitted)
      {
         val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
         if(providers.isEmpty()) state.report = LocationReport("DISABLED")
         for(provider in providers)
         {
            try { manager.requestLocationUpdates(provider, 10000L, 10f, listener) }
            catch(exception: SecurityException) { state.report = LocationReport("PERMISSION_DENIED") }
         }
      }
      onDispose { manager.removeUpdates(listener) }
   }
   return state
}

@Composable
internal fun LocationPermissionControl(state: ForegroundLocationState)
{
   val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
   { state.permissionRevision++ }
   if(state.report.status == "PERMISSION_DENIED")
   {
      TextButton(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) })
      { Text("Enable device location (optional)") }
   }
}
