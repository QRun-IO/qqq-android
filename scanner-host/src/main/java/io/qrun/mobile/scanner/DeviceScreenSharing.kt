/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.UUID

/*******************************************************************************
 ** A consent attempt belongs to one authenticated host lifetime, never a saved preference.
 *******************************************************************************/
internal class DeviceScreenSharing(private val context: Context) {
   var active by mutableStateOf(false)
      private set
   var pending by mutableStateOf(false)
      private set
   var connecting by mutableStateOf(false)
      private set
   var message by mutableStateOf("")
      private set
   private val consentAttempt = ScreenConsentAttempt()
   private val owner = UUID.randomUUID().toString()
   private var connection: ServiceConnection? = null
   private var service: DeviceScreenCaptureService? = null
   var onSystemStop: () -> Unit = {}
   var onStarted: () -> Unit = {}

   /*******************************************************************************
    ** Revoke any previous grant before creating a fresh ticket for Android's consent result.
    *******************************************************************************/
   fun beginConsent(): String {
      stop()
      pending = true
      message = ""
      return consentAttempt.begin()
   }

   /*******************************************************************************
    ** Consume consent only for the current authenticated host, then recheck permission when binding completes.
    *******************************************************************************/
   fun accept(ticket: String, resultCode: Int, data: Intent?, allowed: () -> Boolean) {
      if (!consentAttempt.current(ticket) || !pending) return
      pending = false
      if (!consentAttempt.consume(ticket, resultCode == Activity.RESULT_OK && data != null, allowed())) {
         message = "Device screen sharing was not started."
         return
      }
      requireNotNull(data)
      connecting = true
      val serviceIntent = Intent(context, DeviceScreenCaptureService::class.java)
      val binding = object : ServiceConnection {
         /*******************************************************************************
          ** Reject stale bindings and recheck host eligibility before handing Android consent to the service.
          *******************************************************************************/
         override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!consentAttempt.current(ticket) || connection !== this) {
               runCatching { context.unbindService(this) }; return
            }
            if (!allowed()) {
               stop(); return
            }
            val current = (binder as DeviceScreenCaptureService.LocalBinder).service()
            service = current
            active = current.begin(owner, resultCode, data) {
               if (consentAttempt.current(ticket)) {
                  stop(); onSystemStop()
               }
            }
            if (active) onStarted()
            connecting = false
            if (!active) {
               stop(); message = "Device screen sharing could not start. Try again from the app."
            }
         }

         /*******************************************************************************
          ** Invalidate the current binding and notify its host when Android removes the projection service.
          *******************************************************************************/
         private fun disconnected() {
            if (connection === this) {
               stop(); onSystemStop()
            }
         }

         /*******************************************************************************
          ** Treat service loss as revoked sharing rather than automatically reconnecting without consent.
          *******************************************************************************/
         override fun onServiceDisconnected(name: ComponentName) {
            disconnected()
         }

         /*******************************************************************************
          ** A dead binding invalidates this host's projection and requires a new operator action.
          *******************************************************************************/
         override fun onBindingDied(name: ComponentName) {
            disconnected()
         }

         /*******************************************************************************
          ** A rejected service binding ends the attempt rather than retaining a pending consent state.
          *******************************************************************************/
         override fun onNullBinding(name: ComponentName) {
            disconnected()
         }
      }
      connection = binding
      try {
         ContextCompat.startForegroundService(context, serviceIntent)
         if (!context.bindService(serviceIntent, binding, Context.BIND_AUTO_CREATE)) {
            connection = null
            stop()
            message = "Device screen sharing could not start."
         }
      } catch (exception: Exception) {
         stop(); message = "Device screen sharing could not start. Check notification permission."
      }
   }

   /*******************************************************************************
    ** Invalidate the consent ticket before unbinding so late results cannot resume sharing.
    *******************************************************************************/
   fun stop() {
      consentAttempt.invalidate()
      active = false
      pending = false
      connecting = false
      val previous = service
      service = null
      val binding = connection
      connection = null
      previous?.stop(owner)
      if (binding != null) runCatching { context.unbindService(binding) }
   }

   /*******************************************************************************
    ** Delegate capture only while this host still owns an active projection.
    *******************************************************************************/
   suspend fun frame(lease: ScreenLease): ScreenFrame? = if (active) service?.frame(owner, lease) else null

   /*******************************************************************************
    ** Pass the authenticated viewer's deadline only to this host's active projection grant.
    *******************************************************************************/
   fun boundLease(expiresAt: Long) {
      if (active) service?.boundLease(owner, expiresAt)
   }
}

/*******************************************************************************
 ** Only a deliberate device operator action launches Android's fresh consent dialog.
 *******************************************************************************/
@Composable
internal fun DeviceScreenSharingControl(sharing: DeviceScreenSharing, controller: MobileSessionController, allowed: () -> Boolean) {
   val context = LocalContext.current
   val currentAllowed by rememberUpdatedState(allowed)
   var disclosure by remember { mutableStateOf(false) }
   var ticket by remember { mutableStateOf<String?>(null) }
   var permissionRevision by remember { mutableStateOf(0) }
   val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
   val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val currentTicket = ticket
      ticket = null
      if (currentTicket != null) sharing.accept(currentTicket, result.resultCode, result.data) {
         currentAllowed() && controller.supportsDeviceScreen
      }
   }
   if (sharing.active) {
      Text("Sharing this device screen, including other apps and notifications.")
      TextButton(onClick = { controller.pauseViewing() }) { Text("Stop device screen sharing") }
   } else if (controller.supportsDeviceScreen) {
      val canNotify = remember(permissionRevision) {
         Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      }
      if (!canNotify) TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
         Text("Enable sharing notification")
      } else TextButton(enabled = !sharing.pending && !sharing.connecting && allowed(), onClick = { disclosure = true }) { Text("Share device screen…") }
      if (sharing.connecting) Text("Starting device screen sharing…")
      if (sharing.message.isNotBlank()) Text(sharing.message)
   }
   if (disclosure) AlertDialog(
      onDismissRequest = { disclosure = false }, title = { Text("Share this device screen?") },
      text = { Text("An authorized administrator can see other apps and notifications while viewing this session. Android will ask for permission. Sharing stops if the viewer disconnects, you sign out, or this activity restarts. Use Stop sharing in the notification or Android's sharing indicator at any time.") },
      dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } },
      confirmButton = {
         TextButton(onClick = {
            disclosure = false
            if (currentAllowed() && controller.supportsDeviceScreen && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
               ticket = sharing.beginConsent()
               val manager = context.getSystemService(MediaProjectionManager::class.java)
               val intent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
               else manager.createScreenCaptureIntent()
               consent.launch(intent)
            }
         }) { Text("Continue to Android permission") }
      })
}
