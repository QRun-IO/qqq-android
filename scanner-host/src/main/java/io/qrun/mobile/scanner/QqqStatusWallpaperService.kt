/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.app.Activity
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*******************************************************************************
 ** The one in-process publisher. The wallpaper engine runs in this application's main process, so the snapshot
 ** is shared by reference and disappears with the process; nothing is written to disk.
 *******************************************************************************/
internal object WallpaperStatus {
   val store = WallpaperStatusStore(SystemClock::elapsedRealtime, System::currentTimeMillis)
}

/*******************************************************************************
 ** Optional live wallpaper showing the company name and, on the unlocked home screen only, the approved session
 ** display name and current IP. Inactive until a person selects it in Android's wallpaper chooser.
 *******************************************************************************/
class QqqStatusWallpaperService : WallpaperService() {
   /*******************************************************************************
    ** One engine per wallpaper surface; the framework destroys it when the wallpaper is replaced.
    *******************************************************************************/
   override fun onCreateEngine(): Engine = StatusEngine()

   /*******************************************************************************
    ** Draws text with a single Canvas pass. Network, keyguard and store observation exist only while visible.
    *******************************************************************************/
   private inner class StatusEngine : Engine(), WallpaperStatusListener {
      private val main = Handler(Looper.getMainLooper())
      private val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      private val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
      private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
      private var observing = false
      private var destroyed = false
      private var networkCallback: ConnectivityManager.NetworkCallback? = null
      private var currentNetwork: Network? = null
      private var networkKnown = false
      private var networkAddresses: String? = null
      private val redraw = Runnable { draw() }
      private val screenReceiver = object : BroadcastReceiver() {
         /*******************************************************************************
          ** Withdraw identity the moment the screen turns off; redraw once the user unlocks.
          *******************************************************************************/
         override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) drawBrandOnly() else draw()
         }
      }

      /*******************************************************************************
       ** Observe only while visible. Becoming invisible first paints brand-only so a stale frame cannot flash on wake.
       *******************************************************************************/
      override fun onVisibilityChanged(visible: Boolean) {
         if (destroyed) return
         if (visible) {
            startObserving()
            draw()
         } else {
            drawBrandOnly()
            stopObserving()
         }
      }

      /*******************************************************************************
       ** Repaint when the surface size changes.
       *******************************************************************************/
      override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
         super.onSurfaceChanged(holder, format, width, height)
         if (observing) draw() else drawBrandOnly()
      }

      /*******************************************************************************
       ** Release the network callback, receiver, store listener and pending timers; nothing outlives the engine.
       *******************************************************************************/
      override fun onDestroy() {
         destroyed = true
         stopObserving()
         super.onDestroy()
      }

      /*******************************************************************************
       ** The store may notify from any thread; repaint on the main looper.
       *******************************************************************************/
      override fun changed() {
         main.post(redraw)
      }

      /*******************************************************************************
       ** Start network, unlock and store observation once per visible period.
       *******************************************************************************/
      private fun startObserving() {
         if (observing) return
         observing = true
         WallpaperStatus.store.addListener(this)
         ContextCompat.registerReceiver(
            this@QqqStatusWallpaperService, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED
         )
         val callback = object : ConnectivityManager.NetworkCallback() {
            /*******************************************************************************
             ** Addresses are reported here after the default network becomes available and whenever they change.
             *******************************************************************************/
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
               val text = formatNetworkAddresses(linkProperties.interfaceName, linkProperties.linkAddresses.mapNotNull { it.address.hostAddress })
               main.post { if (observing) { currentNetwork = network; networkKnown = true; networkAddresses = text; draw() } }
            }

            /*******************************************************************************
             ** Ignore the loss of a network that already stopped being the default.
             *******************************************************************************/
            override fun onLost(network: Network) {
               main.post { if (observing && network == currentNetwork) { currentNetwork = null; networkKnown = true; networkAddresses = null; draw() } }
            }
         }
         try {
            connectivity?.registerDefaultNetworkCallback(callback)
            networkCallback = callback
         } catch (exception: RuntimeException) {
            networkKnown = false
         }
      }

      /*******************************************************************************
       ** Undo everything startObserving did and cancel scheduled work. Safe to call repeatedly.
       *******************************************************************************/
      private fun stopObserving() {
         main.removeCallbacks(redraw)
         if (!observing) return
         observing = false
         WallpaperStatus.store.removeListener(this)
         try {
            unregisterReceiver(screenReceiver)
         } catch (exception: IllegalArgumentException) { /* Already unregistered. */
         }
         networkCallback?.let {
            try {
               connectivity?.unregisterNetworkCallback(it)
            } catch (exception: IllegalArgumentException) { /* Already unregistered. */
            }
         }
         networkCallback = null
         currentNetwork = null
         networkKnown = false
         networkAddresses = null
      }

      /*******************************************************************************
       ** Preview and lock-only wallpaper never show identity. A locked keyguard, or no keyguard service, is treated
       ** as locked. The wallpaper-flag check is additive: only an engine bound to the lock screen alone is certain.
       *******************************************************************************/
      private fun currentContext(): WallpaperContext {
         if (isPreview) return WallpaperContext.PREVIEW
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && getWallpaperFlags() == WallpaperManager.FLAG_LOCK) return WallpaperContext.LOCKED
         return if (keyguard?.isKeyguardLocked == false) WallpaperContext.HOME else WallpaperContext.LOCKED
      }

      /*******************************************************************************
       ** Paint the frame for the current context and schedule the single timer that withdraws an aging name.
       *******************************************************************************/
      private fun draw() {
         if (destroyed) return
         main.removeCallbacks(redraw)
         val snapshot = WallpaperStatus.store.snapshot
         val now = SystemClock.elapsedRealtime()
         val context = currentContext()
         val frame = WallpaperPresentation.render(
            snapshot, context, WallpaperPresentation.networkLine(networkKnown, networkAddresses), now
         ) { timeFormat.format(Instant.ofEpochMilli(it)) }
         paintFrame(frame)
         if (context == WallpaperContext.HOME && observing) WallpaperPresentation.millisUntilStale(snapshot, now)?.let { main.postDelayed(redraw, it + 50) }
      }

      /*******************************************************************************
       ** Brand-only frame used on invisibility, screen off and teardown.
       *******************************************************************************/
      private fun drawBrandOnly() {
         if (destroyed) return
         main.removeCallbacks(redraw)
         val snapshot = WallpaperStatus.store.snapshot
         paintFrame(WallpaperPresentation.render(snapshot, WallpaperContext.LOCKED, "", 0L) { "" })
      }

      /*******************************************************************************
       ** Draw the header and lines centred in the upper third, shortening any line to fit. Never logs text.
       *******************************************************************************/
      private fun paintFrame(frame: WallpaperFrame) {
         val holder = surfaceHolder
         if (!holder.surface.isValid) return
         val canvas: Canvas? = try {
            holder.lockCanvas()
         } catch (exception: RuntimeException) {
            null
         }
         if (canvas == null) return
         try {
            draw(canvas, frame)
         } finally {
            holder.unlockCanvasAndPost(canvas)
         }
      }

      /*******************************************************************************
       ** Canvas layout kept deliberately plain: solid brand colour, bold header, regular status lines.
       *******************************************************************************/
      private fun draw(canvas: Canvas, frame: WallpaperFrame) {
         val width = canvas.width.toFloat()
         canvas.drawColor(Color.parseColor(frame.colorHex))
         paint.color = if (frame.darkText) Color.BLACK else Color.WHITE
         paint.textAlign = Paint.Align.CENTER
         val maxWidth = width * 0.9f
         var y = canvas.height * 0.22f
         paint.typeface = Typeface.DEFAULT_BOLD
         paint.textSize = width * 0.07f
         canvas.drawText(fit(frame.header, maxWidth), width / 2, y, paint)
         paint.typeface = Typeface.DEFAULT
         paint.textSize = width * 0.045f
         frame.lines.forEach { line ->
            y += paint.textSize * 1.6f
            canvas.drawText(fit(line, maxWidth), width / 2, y, paint)
         }
      }

      /*******************************************************************************
       ** Truncate with an ellipsis so long names cannot run off the display.
       *******************************************************************************/
      private fun fit(text: String, maxWidth: Float): String {
         val count = paint.breakText(text, true, maxWidth, null)
         return if (count >= text.length) text else text.take((count - 1).coerceAtLeast(0)) + "…"
      }
   }
}

/*******************************************************************************
 ** The standard Android live-wallpaper chooser, preselected to this wallpaper. Activation still needs the person's
 ** confirmation there; nothing is applied silently.
 *******************************************************************************/
internal fun wallpaperChooserIntent(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
   .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, QqqStatusWallpaperService::class.java))

/*******************************************************************************
 ** Open the chooser; false when the device offers none or refuses the request.
 *******************************************************************************/
internal fun openWallpaperChooser(activity: Activity): Boolean = try {
   activity.startActivity(wallpaperChooserIntent(activity))
   true
} catch (exception: ActivityNotFoundException) {
   false
} catch (exception: SecurityException) {
   false
}

/*******************************************************************************
 ** Settings entry that opens the wallpaper chooser. It states exactly what the wallpaper will and will not show.
 *******************************************************************************/
@Composable
internal fun WallpaperSetting(activity: Activity) {
   var unavailable by remember { mutableStateOf(false) }
   TextButton(onClick = { unavailable = !openWallpaperChooser(activity) }) { Text("Status wallpaper…") }
   Text(
      "Optional live wallpaper: company name only on the lock screen; your name and this device's IP on the unlocked home screen. You confirm it in Android.",
      style = MaterialTheme.typography.bodySmall
   )
   if (unavailable) Text("This device has no live wallpaper chooser.")
}
