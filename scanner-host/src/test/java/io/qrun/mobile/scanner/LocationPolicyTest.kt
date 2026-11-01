/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class LocationPolicyTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun missingPreferenceEnablesReportingAndExplicitChoicesArePreserved() {
      assertTrue(LocationPolicy.enabled(null))
      assertTrue(LocationPolicy.enabled(true))
      assertFalse(LocationPolicy.enabled(false))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun initialPermissionRequestRequiresActiveSessionAndEnabledPreference() {
      assertTrue(LocationPolicy.shouldRequestPermission(true, true, false, false))
      assertFalse(LocationPolicy.shouldRequestPermission(false, true, false, false))
      assertFalse(LocationPolicy.shouldRequestPermission(true, false, false, false))
      assertFalse(LocationPolicy.shouldRequestPermission(true, true, true, false))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun denialDoesNotPromptAgainOnResumeRecreationOrNextSession() {
      listOf(false, true, false, true).forEach { active ->
         assertFalse(LocationPolicy.shouldRequestPermission(active, true, false, true))
      }
      assertTrue(LocationPolicy.enabled(null))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun settingsDistinguishOptOutPermissionProvidersAndAnActualFix() {
      assertEquals("Location reporting is off", LocationPolicy.status(false, true, true, true, "AVAILABLE"))
      assertEquals("Location reporting is on; Android location permission is not granted", LocationPolicy.status(true, false, true, true, "UNAVAILABLE"))
      assertEquals("Location reporting is on; device location providers are off", LocationPolicy.status(true, true, false, true, "AVAILABLE"))
      assertEquals("Location reporting is on; waiting for an approved signed-in foreground session", LocationPolicy.status(true, true, true, false, "AVAILABLE"))
      assertEquals("Location reporting is on; waiting for a foreground location fix", LocationPolicy.status(true, true, true, true, "UNAVAILABLE"))
      assertEquals("Location reporting is on; foreground location is available", LocationPolicy.status(true, true, true, true, "AVAILABLE"))
   }
}
