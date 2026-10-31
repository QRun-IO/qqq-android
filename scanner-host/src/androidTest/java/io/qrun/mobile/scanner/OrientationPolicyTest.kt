/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest
{
   @Test fun deviceSelectionSurvivesRecreationAndColdActivityLaunch()
   {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      check(context.packageName == "io.qrun.mobile.scanner.e2e")
      OrientationPolicy.entries.forEach { policy ->
         ActivityScenario.launch(ScannerActivity::class.java).use { scenario ->
            scenario.onActivity { policy.select(it) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.recreate()
            if(policy != OrientationPolicy.SYSTEM)
            {
               val expected = if(policy == OrientationPolicy.PORTRAIT || policy == OrientationPolicy.REVERSE_PORTRAIT)
                  android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
               val deadline = android.os.SystemClock.uptimeMillis() + 15000
               var applied = false
               while(!applied && android.os.SystemClock.uptimeMillis() < deadline)
               {
                  scenario.onActivity { applied = it.resources.configuration.orientation == expected }
                  if(!applied) android.os.SystemClock.sleep(100)
               }
            }
            scenario.onActivity {
               assertEquals(policy, OrientationPolicy.read(it))
               assertEquals(policy.requested, it.requestedOrientation)
               if(policy != OrientationPolicy.SYSTEM)
               {
                  val expected = if(policy == OrientationPolicy.PORTRAIT || policy == OrientationPolicy.REVERSE_PORTRAIT)
                     android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
                  assertEquals("The handheld applies the selected screen axis", expected, it.resources.configuration.orientation)
               }
            }
         }
         ActivityScenario.launch(ScannerActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(policy.requested, it.requestedOrientation) }
         }
      }
      ActivityScenario.launch(ScannerActivity::class.java).use { scenario ->
         scenario.onActivity { OrientationPolicy.SYSTEM.select(it) }
      }
   }
}
