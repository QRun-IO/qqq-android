/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the actual generated APK resources; no server or license download is required. */
@RunWith(AndroidJUnit4::class)
class OpenSourceLicensesTest
{
   @get:Rule val ui = createAndroidComposeRule<ScannerActivity>()

   @Test fun bundledNoticesOpenAndCloseFromSettings()
   {
      ui.runOnUiThread {
         ui.activity.setContent { MaterialTheme { OpenSourceLicensesButton() } }
      }
      ui.onNodeWithText("Open-source licenses").performClick()
      ui.waitUntil(timeoutMillis = 10000) {
         ui.onAllNodesWithText("Apache", substring = true).fetchSemanticsNodes().isNotEmpty()
      }
      ui.onNode(hasScrollAction()).performScrollToNode(hasText("Material Design icons by Google"))
      ui.onNodeWithText("Material Design icons by Google").assertIsDisplayed()
      ui.onNodeWithText("Material Design icons by Google").performClick()
      onView(withText(containsString("Drawing paths match the pinned source"))).check(matches(isDisplayed()))
      ui.onNodeWithText("OK").performClick()
      ui.onNodeWithText("Close").performClick()
      ui.onNodeWithText("Open-source licenses").assertIsDisplayed()
   }
}
