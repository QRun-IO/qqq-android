/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest
{
   @Test fun missingOrUnknownSavedValuesKeepSystemBehavior()
   {
      assertEquals(OrientationPolicy.SYSTEM, OrientationPolicy.parse(null))
      assertEquals(OrientationPolicy.SYSTEM, OrientationPolicy.parse("unknown"))
      OrientationPolicy.entries.forEach { assertEquals(it, OrientationPolicy.parse(it.name)) }
   }

   @Test fun choicesRequestThePlatformOrientationWithoutSensorOverrides()
   {
      assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
         ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
         ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE), OrientationPolicy.entries.map { it.requested })
   }
}
