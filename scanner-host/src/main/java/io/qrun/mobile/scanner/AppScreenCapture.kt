/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Copies only the caller's app-content rectangle, never the display or another app's window. */
internal suspend fun captureAppScreen(window: Window, bounds: Rect, lease: ScreenLease): ScreenFrame? = withContext(Dispatchers.Main)
{
   if(bounds.width() <= 0 || bounds.height() <= 0 || !window.decorView.isAttachedToWindow || !window.decorView.hasWindowFocus()) return@withContext null
   val scale = min(1.0, min(lease.maxWidth.toDouble() / bounds.width(), lease.maxHeight.toDouble() / bounds.height()))
   val width = (bounds.width() * scale).toInt().coerceAtLeast(1)
   val height = (bounds.height() * scale).toInt().coerceAtLeast(1)
   val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
   val copied = suspendCancellableCoroutine<Boolean> { continuation ->
      try
      {
         PixelCopy.request(window, Rect(bounds), bitmap, { result ->
            if(continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS) { _, _, _ -> bitmap.recycle() }
            else bitmap.recycle()
         }, Handler(Looper.getMainLooper()))
      }
      catch(exception: IllegalArgumentException) { continuation.resume(false) }
   }
   try
   {
      if(!copied) return@withContext null
      withContext(Dispatchers.Default)
      {
         var bytes: ByteArray? = null
         for(quality in listOf(70, 50, 30))
         {
            val buffer = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            if(buffer.size() <= lease.maxBytes) { bytes = buffer.toByteArray(); break }
         }
         bytes?.let { ScreenFrame(it, width, height, Instant.now().toString()) }
      }
   }
   finally { if(!bitmap.isRecycled) bitmap.recycle() }
}
