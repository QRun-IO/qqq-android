/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.Image
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nonexported, session-only projection. No consent, credentials or frames are persisted. */
class DeviceScreenCaptureService : Service()
{
   private val handler = Handler(Looper.getMainLooper())
   private var owner: String? = null
   private var projection: MediaProjection? = null
   private var display: VirtualDisplay? = null
   private var reader: ImageReader? = null
   private val latestImage = LatestScreenImage<Image>()
   private var latestCapturedAt = ""
   private var stopped: (() -> Unit)? = null
   private var foregroundReady = false
   private var screenReceiverRegistered = false
   private val stopReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) { finishProjection() }
   }
   private var projectionCallback: MediaProjection.Callback? = null
   private val unboundTimeout = Runnable { if(owner == null) finishProjection() }
   private val leaseTimeout = Runnable { finishProjection() }

   internal inner class LocalBinder : Binder() {
      fun service() = this@DeviceScreenCaptureService
   }

   override fun onBind(intent: Intent): IBinder = LocalBinder()

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
   {
      if(intent?.action == STOP) { finishProjection(); return START_NOT_STICKY }
      try {
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(NotificationChannel(CHANNEL, "Device screen sharing", NotificationManager.IMPORTANCE_LOW))
      if(!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE)
      {
         finishProjection()
         return START_NOT_STICKY
      }
      val stop = PendingIntent.getService(this, 0, Intent(this, DeviceScreenCaptureService::class.java).setAction(STOP),
         PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_scanner)
         .setContentTitle("QQQ device screen sharing").setContentText("Other apps and notifications may be visible to your administrator.")
         .setOngoing(true).addAction(Notification.Action.Builder(null, "Stop sharing", stop).build()).build()
      if(Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
      else startForeground(NOTIFICATION_ID, notification)
      foregroundReady = true
      handler.removeCallbacks(unboundTimeout)
      handler.postDelayed(unboundTimeout, 5000)
      } catch(exception: Exception) { finishProjection() }
      return START_NOT_STICKY
   }

   /** Called only by our same-process binder after a freshly approved system result. */
   internal fun begin(grantOwner: String, resultCode: Int, data: Intent, onStopped: () -> Unit): Boolean
   {
      if(!foregroundReady || owner != null || resultCode != Activity.RESULT_OK || getSystemService(KeyguardManager::class.java).isDeviceLocked) return false
      owner = grantOwner
      stopped = onStopped
      handler.removeCallbacks(unboundTimeout)
      return try {
         val granted = requireNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data))
         projection = granted
         val callback = object : MediaProjection.Callback() {
            override fun onStop() { if(projection === granted) finishProjection() }
            override fun onCapturedContentResize(width: Int, height: Int) {
               if(projection === granted && width > 0 && height > 0) resize(width, height)
            }
         }
         projectionCallback = callback
         granted.registerCallback(callback, handler)
         val (width, height) = displaySize()
         val (scaledWidth, scaledHeight) = scaledSize(width, height)
         reader = newReader(scaledWidth, scaledHeight)
         display = requireNotNull(granted.createVirtualDisplay("QQQ consented device screen", scaledWidth, scaledHeight,
            resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler))
         ContextCompat.registerReceiver(this, stopReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
         screenReceiverRegistered = true
         handler.postDelayed(leaseTimeout, 30000)
         true
      } catch(exception: Exception) { finishProjection(); false }
   }

   internal fun stop(grantOwner: String) { if(owner == grantOwner) finishProjection() }

   internal fun boundLease(grantOwner: String, expiresAt: Long)
   {
      if(owner != grantOwner || projection == null) return
      handler.removeCallbacks(leaseTimeout)
      val remaining = expiresAt - SystemClock.elapsedRealtime()
      if(remaining <= 0) finishProjection() else handler.postDelayed(leaseTimeout, remaining.coerceAtMost(30000))
   }

   internal suspend fun frame(grantOwner: String, lease: ScreenLease): ScreenFrame? = withContext(Dispatchers.Main)
   {
      if(owner != grantOwner || projection == null || lease.captureMode != CaptureMode.DEVICE_SCREEN) return@withContext null
      val capturedProjection = projection
      if(getSystemService(KeyguardManager::class.java).isDeviceLocked) { finishProjection(); return@withContext null }
      val image = latestImage.image ?: return@withContext null
      val capturedAt = latestCapturedAt
      // The main-thread listener cannot replace this image while its pixels are copied.
      val bitmap = run {
         val plane = image.planes.single()
         if(plane.pixelStride != 4 || plane.rowStride < image.width * 4 || plane.rowStride.toLong() * image.height > 8388608L)
            return@withContext null
         val source = plane.buffer.duplicate()
         val rowBytes = image.width * 4
         if(source.remaining().toLong() < plane.rowStride.toLong() * (image.height - 1) + rowBytes) return@withContext null
         val packed = java.nio.ByteBuffer.allocate(rowBytes * image.height)
         val origin = source.position()
         for(row in 0 until image.height) {
            val slice = source.duplicate()
            slice.position(origin + row * plane.rowStride)
            slice.limit(slice.position() + rowBytes)
            packed.put(slice)
         }
         packed.flip()
         val copied = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
         try {
            copied.copyPixelsFromBuffer(packed)
            copied
         } catch(exception: Exception) { copied.recycle(); throw exception }
      }
      try {
         if(bitmap.width > lease.maxWidth || bitmap.height > lease.maxHeight) return@withContext null
         withContext(Dispatchers.Default) {
            var frame: ScreenFrame? = null
            for(quality in listOf(70, 50, 30)) {
               val bytes = ByteArrayOutputStream()
               bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
               if(bytes.size() <= lease.maxBytes) {
                  frame = ScreenFrame(bytes.toByteArray(), bitmap.width, bitmap.height, capturedAt, CaptureMode.DEVICE_SCREEN)
                  break
               }
            }
            frame
         }.takeIf { owner == grantOwner && projection === capturedProjection }
      } finally { bitmap.recycle() }
   }

   override fun onConfigurationChanged(newConfig: Configuration)
   {
      super.onConfigurationChanged(newConfig)
      if(projection != null) { val (width, height) = displaySize(); resize(width, height) }
   }

   private fun displaySize(): Pair<Int, Int>
   {
      val metrics = getSystemService(WindowManager::class.java)
      if(Build.VERSION.SDK_INT >= 30) return metrics.maximumWindowMetrics.bounds.let { it.width() to it.height() }
      @Suppress("DEPRECATION") val size = android.util.DisplayMetrics().also { metrics.defaultDisplay.getRealMetrics(it) }
      return size.widthPixels to size.heightPixels
   }

   private fun scaledSize(width: Int, height: Int): Pair<Int, Int>
   {
      val scale = min(1.0, min(640.0 / width, 1280.0 / height))
      return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
   }

   private fun resize(width: Int, height: Int)
   {
      val current = display ?: return
      val (targetWidth, targetHeight) = scaledSize(width, height)
      if(reader?.width == targetWidth && reader?.height == targetHeight) return
      val previous = reader
      try {
         latestImage.close()
         val replacement = newReader(targetWidth, targetHeight)
         reader = replacement
         current.surface = replacement.surface
         current.resize(targetWidth, targetHeight, resources.configuration.densityDpi)
      } catch(exception: Exception) { finishProjection() }
      finally { runCatching { previous?.close() } }
   }

   /** Drain continuously so first demand gets current pixels; encode only on leased polls. */
   private fun newReader(width: Int, height: Int): ImageReader
   {
      // One retained image plus two free slots lets acquireLatestImage discard older queued frames.
      return ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also { created ->
         created.setOnImageAvailableListener({ source ->
            if(source === reader && projection != null) {
               try {
                  source.acquireLatestImage()?.let { image ->
                     latestImage.replace(image)
                     latestCapturedAt = Instant.now().toString()
                  }
               } catch(exception: Exception) { finishProjection() }
            }
         }, handler)
      }
   }

   private fun finishProjection()
   {
      val callback = stopped
      stopped = null
      owner = null
      handler.removeCallbacks(unboundTimeout)
      handler.removeCallbacks(leaseTimeout)
      val prior = projection
      projection = null
      foregroundReady = false
      if(screenReceiverRegistered) { runCatching { unregisterReceiver(stopReceiver) }; screenReceiverRegistered = false }
      runCatching { display?.release() }; display = null
      runCatching { latestImage.close() }
      latestCapturedAt = ""
      runCatching { reader?.close() }; reader = null
      projectionCallback?.let { callback -> runCatching { prior?.unregisterCallback(callback) } }
      projectionCallback = null
      runCatching { prior?.stop() }
      runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
      stopSelf()
      callback?.invoke()
   }

   override fun onDestroy() { finishProjection(); super.onDestroy() }
   override fun onTaskRemoved(rootIntent: Intent?) { finishProjection(); super.onTaskRemoved(rootIntent) }

   private companion object {
      const val CHANNEL = "qqq-device-screen"
      const val STOP = "io.qrun.mobile.scanner.STOP_DEVICE_SCREEN"
      const val NOTIFICATION_ID = 8716
   }
}
