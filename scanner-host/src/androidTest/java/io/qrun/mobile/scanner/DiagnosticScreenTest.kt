/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticScreenTest
{
   @get:Rule val ui = createAndroidComposeRule<ScannerActivity>()

   @Test fun filtersPausesClearsAndReturnsWithoutNetworkOrCredentials()
   {
      val log = DiagnosticLog()
      log.record(DiagnosticCode.CONNECTION_OK, 200, 15)
      log.record(DiagnosticCode.DEVICE_RESPONSE, 403, 25)
      var returned = false
      ui.runOnUiThread { ui.activity.setContent { MaterialTheme { DiagnosticScreen(log) { returned = true } } } }
      ui.onNodeWithText("Reachable").assertIsDisplayed()
      ui.onNodeWithText("Problems").performScrollTo().performClick()
      ui.onNodeWithText("Device request failed").performScrollTo().assertIsDisplayed()
      ui.onNodeWithText("Server reachable").assertDoesNotExist()
      ui.onNodeWithText("Pause updates").performScrollTo().performClick()
      log.record(DiagnosticCode.SESSION_RESPONSE, 500, 50)
      ui.onNodeWithText("Session request failed").assertDoesNotExist()
      ui.onNodeWithText("Resume live updates").performScrollTo().performClick()
      ui.waitForIdle()
      ui.onNodeWithText("Session request failed").performScrollTo().assertIsDisplayed()
      ui.onNodeWithText("Clear").performClick()
      ui.onNodeWithText("No activity recorded yet. New events appear here while this screen is live.").performScrollTo().assertIsDisplayed()
      assertTrue(log.snapshot().isEmpty())
      ui.onNodeWithText("Back").performClick()
      ui.runOnIdle { assertTrue(returned) }
   }
}
