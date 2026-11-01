/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.sampleandroidmobileapp

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.kingsrook.qqq.frontend.android.core.controllers.QQQRepository
import com.kingsrook.qqq.frontend.android.core.model.widgets.WidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.R
import com.kingsrook.qqq.frontend.android.mobileapp.data.MockPreferencesDataStore
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.ImageWidgetBlock
import com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks.WidgetBlockParameters
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Decode and draw local bytes through the production widget, including its configured SVG decoder. */
class CoilRenderingTests : BaseTest()
{
   @get:Rule val ui = createComposeRule()
   private val files = mutableListOf<File>()
   private val models = ViewModelStore()
   private val visible = mutableStateOf(true)
   private val path = mutableStateOf("")
   private var contentInstalled = false
   private val left = Color(0xFFC92B1F)
   private val right = Color(0xFF04AACC)

   @After fun disposeFixtures()
   {
      if(contentInstalled)
      {
         ui.runOnIdle { visible.value = false; models.clear() }
         ui.waitForIdle()
      }
      files.forEach { it.delete() }
   }

   @Test fun rasterBytesDecodeAndRenderAtSemanticDimensions()
   {
      val png = fixture(".png")
      val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
      try
      {
         for(y in 0 until bitmap.height)
         {
            for(x in 0 until bitmap.width)
            {
               bitmap.setPixel(x, y, if(x < 16) 0xFFC92B1F.toInt() else 0xFF04AACC.toInt())
            }
         }
         png.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
      }
      finally { bitmap.recycle() }

      render(png)
      assertDecodedRegions()
   }

   @Test fun svgBytesRenderAndInvalidSourceShowsTheProductionErrorPainter()
   {
      val svg = fixture(".svg").apply {
         writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="32" height="16" viewBox="0 0 32 16"><rect width="16" height="16" fill="#c92b1f"/><rect x="16" width="16" height="16" fill="#04aacc"/></svg>""")
      }
      render(svg)
      assertDecodedRegions()

      val invalid = fixture(".png").apply { writeText("This is a deliberately invalid local image.") }
      ui.runOnIdle { path.value = Uri.fromFile(invalid).toString() }
      ui.waitUntil(10000)
      {
         val actual = pixels("production-image")
         val expected = pixels("error-reference")
         actual.width == expected.width && actual.height == expected.height &&
            samplePoints(actual).all { (x, y) -> sameColor(actual[x, y], expected[x, y]) }
      }
      val actual = pixels("production-image")
      val expected = pixels("error-reference")
      assertEquals(expected.width, actual.width)
      assertEquals(expected.height, actual.height)
      assertTrue("Invalid bytes must replace the previous SVG with the configured error drawable",
         samplePoints(actual).all { (x, y) -> sameColor(actual[x, y], expected[x, y]) })
   }

   private fun render(file: File)
   {
      val repository = object : QQQRepository by SampleAppMockQQQRepository()
      {
         override fun getURI(path: String): String
         {
            check(path.startsWith("file://")) { "Image qualification accepts only its private local fixtures" }
            return path
         }
      }
      lateinit var model: QViewModel
      ui.runOnIdle()
      {
         model = QViewModel(repository, MockPreferencesDataStore())
         models.put("image-rendering", model)
         path.value = Uri.fromFile(file).toString()
      }
      ui.setContent()
      {
         if(visible.value)
         {
            Column()
            {
               Box(Modifier.testTag("production-image"))
               {
                  ImageWidgetBlock(WidgetBlockParameters(
                     WidgetBlock("image", "IMAGE", styles = mutableMapOf("width" to 64, "height" to 32),
                        values = mutableMapOf("path" to path.value)), qViewModel = model))
               }
               Image(painterResource(R.drawable.ic_broken_image), contentDescription = null,
                  contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp, 32.dp).testTag("error-reference"))
            }
         }
      }
      contentInstalled = true
   }

   private fun assertDecodedRegions()
   {
      ui.onNodeWithTag("production-image").assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(32.dp)
      ui.waitUntil(10000)
      {
         val rendered = pixels("production-image")
         sameColor(rendered[rendered.width / 4, rendered.height / 2], left) &&
            sameColor(rendered[rendered.width * 3 / 4, rendered.height / 2], right)
      }
      val rendered = pixels("production-image")
      assertTrue("Decoded fixture must render both distinct source regions",
         sameColor(rendered[rendered.width / 4, rendered.height / 2], left) &&
            sameColor(rendered[rendered.width * 3 / 4, rendered.height / 2], right))
   }

   private fun fixture(suffix: String): File =
      File.createTempFile("qqq-coil-fixture-", suffix, InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
         .also { files.add(it) }

   private fun pixels(tag: String): PixelMap = ui.onNodeWithTag(tag).captureToImage().toPixelMap()

   private fun samplePoints(image: PixelMap): List<Pair<Int, Int>> =
      (1..7).flatMap { x -> (1..7).map { y -> image.width * x / 8 to image.height * y / 8 } }

   private fun sameColor(actual: Color, expected: Color): Boolean =
      abs(actual.red - expected.red) < 0.025f && abs(actual.green - expected.green) < 0.025f &&
         abs(actual.blue - expected.blue) < 0.025f && abs(actual.alpha - expected.alpha) < 0.025f
}
