/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant

internal data class LocationReport(val status: String = "UNAVAILABLE", val fix: LocationFix? = null)
internal data class LocationFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val capturedAt: String)

/** Default-on foreground-only telemetry. Denying permission never prevents scanning. */
internal class ForegroundLocationState
{
   var report by mutableStateOf(LocationReport())
   var permissionRevision by mutableStateOf(0)
   var enabled by mutableStateOf(true)
   var permitted by mutableStateOf(false)
   var precise by mutableStateOf(false)
   var providersEnabled by mutableStateOf(false)
   var active by mutableStateOf(false)
   var setEnabled: (Boolean) -> Unit = {}
}

@Composable
internal fun rememberForegroundLocation(active: Boolean): ForegroundLocationState
{
   val context = LocalContext.current
   val lifecycleOwner = LocalLifecycleOwner.current
   val preferences = remember(context) { context.getSharedPreferences("host", Context.MODE_PRIVATE) }
   val state = remember {
      ForegroundLocationState().apply {
         enabled = LocationPolicy.enabled(if(preferences.contains("locationReportingEnabled")) preferences.getBoolean("locationReportingEnabled", true) else null)
         setEnabled = { value ->
            preferences.edit().putBoolean("locationReportingEnabled", value).apply()
            enabled = value
            if(!value) report = LocationReport("DISABLED")
         }
      }
   }
   val manager = remember { context.getSystemService(LocationManager::class.java) }
   val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
   { state.permissionRevision++ }
   DisposableEffect(lifecycleOwner, context)
   {
      val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) state.permissionRevision++ }
      val receiver = object : BroadcastReceiver()
      {
         override fun onReceive(context: Context?, intent: Intent?) { state.permissionRevision++ }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
         addAction(LocationManager.MODE_CHANGED_ACTION)
         addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
      }, ContextCompat.RECEIVER_NOT_EXPORTED)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
   }
   val permissionRevision = state.permissionRevision
   val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
   LaunchedEffect(active, state.enabled, permitted, permissionRevision)
   {
      if(LocationPolicy.shouldRequestPermission(active, state.enabled, permitted, preferences.getBoolean("locationPermissionRequested", false)))
      {
         // Record before launching: recreation, resume and denial must not prompt again.
         preferences.edit().putBoolean("locationPermissionRequested", true).apply()
         launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
      }
   }
   DisposableEffect(active, state.enabled, permitted, permissionRevision)
   {
      state.active = active
      state.permitted = permitted
      state.precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
      val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
      state.providersEnabled = providers.isNotEmpty()
      var listening = true
      val listener = object : LocationListener
      {
         override fun onLocationChanged(location: Location)
         {
            if(!listening || !active || !state.enabled || !location.hasAccuracy()) return
            state.report = LocationReport("AVAILABLE", LocationFix(location.latitude, location.longitude,
               location.accuracy.toDouble(), Instant.ofEpochMilli(location.time).toString()))
         }
         override fun onProviderEnabled(provider: String) { state.permissionRevision++ }
         override fun onProviderDisabled(provider: String) { state.permissionRevision++ }
         @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
      }
      state.report = LocationReport(when {
         !state.enabled -> "DISABLED"
         !permitted -> "PERMISSION_DENIED"
         !state.providersEnabled -> "DISABLED"
         else -> "UNAVAILABLE"
      })
      if(active && state.enabled && permitted)
      {
         for(provider in providers.filter { state.precise || it != LocationManager.GPS_PROVIDER })
         {
            try { manager.requestLocationUpdates(provider, 10000L, 10f, listener) }
            catch(exception: SecurityException) { state.report = LocationReport("PERMISSION_DENIED"); state.permitted = false }
         }
      }
      onDispose { listening = false; manager.removeUpdates(listener); state.report = LocationReport() }
   }
   return state
}

@Composable
internal fun LocationPermissionControl(state: ForegroundLocationState)
{
   val context = LocalContext.current
   Row {
      Text("Report foreground location")
      Switch(checked = state.enabled, onCheckedChange = state.setEnabled)
   }
   Text(LocationPolicy.status(state.enabled, state.permitted, state.providersEnabled, state.active, state.report.status))
   if(state.permitted) Text(if(state.precise) "Android permission: precise location" else "Android permission: approximate location")
   Text("Default on. Reporting requires Android permission and an approved signed-in foreground session. Scanning works without location.")
   if(state.enabled && !state.permitted)
   {
      TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
      { Text("Open location permission settings") }
   }
   else if(state.enabled && !state.providersEnabled)
   {
      TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
      { Text("Open device location settings") }
   }
}
