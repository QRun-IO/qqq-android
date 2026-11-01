/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPolicyTest
{
   @Test fun missingSavedValueDefaultsToPortrait()
   {
      assertEquals(OrientationPolicy.PORTRAIT, OrientationPolicy.parse(null))
   }

   @Test fun unrecognizedSavedValuesDefaultToPortrait()
   {
      listOf("unknown", "", " ", "portrait").forEach { value ->
         assertEquals(OrientationPolicy.PORTRAIT, OrientationPolicy.parse(value))
      }
   }

   @Test fun explicitSavedChoicesIncludingSystemArePreserved()
   {
      assertEquals(OrientationPolicy.SYSTEM, OrientationPolicy.parse("SYSTEM"))
      OrientationPolicy.entries.forEach { assertEquals(it, OrientationPolicy.parse(it.name)) }
   }

   @Test fun choicesRequestThePlatformOrientationWithoutSensorOverrides()
   {
      assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
         ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
         ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE), OrientationPolicy.entries.map { it.requested })
   }
}
