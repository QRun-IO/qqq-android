/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*******************************************************************************
 **
 *******************************************************************************/
@RunWith(AndroidJUnit4::class)
class AppScreenCaptureTest {
   @get:Rule
   val ui = createAndroidComposeRule<ScannerActivity>()

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun pixelCopyCapturesActualAppPixelsAsABoundedJpeg() {
      ui.runOnUiThread {
         ui.activity.setContent {
            Row(Modifier.fillMaxSize()) {
               Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
               Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
            }
         }
      }
      ui.waitForIdle()
      val done = CountDownLatch(1)
      var result: ScreenFrame? = null
      ui.runOnUiThread {
         val view = ui.activity.findViewById<android.view.View>(android.R.id.content)
         val location = IntArray(2)
         view.getLocationInWindow(location)
         val bounds = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
         ui.activity.lifecycleScope.launch {
            try {
               result = captureAppScreen(ui.activity.window, bounds, ScreenLease("test", 30000, 1000, 640, 1280, 262144, 0))
            } finally {
               done.countDown()
            }
         }
      }
      assertTrue("PixelCopy must finish", done.await(20, TimeUnit.SECONDS))
      val frame = requireNotNull(result) { "PixelCopy must capture the foreground app window" }
      assertTrue(frame.jpeg.size <= 262144)
      assertTrue(frame.width <= 640 && frame.height <= 1280)
      val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)
      assertNotNull(bitmap)
      val left = bitmap.getPixel(bitmap.width / 4, bitmap.height / 2)
      val right = bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 2)
      assertTrue("Captured left panel must be red", android.graphics.Color.red(left) > 200 && android.graphics.Color.blue(left) < 40)
      assertTrue("Captured right panel must be blue", android.graphics.Color.blue(right) > 200 && android.graphics.Color.red(right) < 40)
      bitmap.recycle()
   }
}
