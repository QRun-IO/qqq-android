/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*******************************************************************************
 ** Managed configuration is a bootstrap only: HTTPS origins and exact known orientation names, never silently defaulted.
 *******************************************************************************/
class ManagedConfigurationTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun noRestrictionsLeavesEverythingUnmanaged() {
      assertEquals(ManagedValues(), ManagedConfiguration.parse(null, null))
      assertEquals(ManagedValues(), ManagedConfiguration.parse("", "  "))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun acceptsOnlyANormalizedHttpsOrigin() {
      assertEquals("https://qqq.example.com/", ManagedConfiguration.parse(" https://QQQ.example.com ", null).serverUrl)
      assertEquals("https://qqq.example.com:8443/", ManagedConfiguration.parse("https://qqq.example.com:8443/", null).serverUrl)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun rejectsHttpCredentialsPathsQueriesAndGarbageWithoutRecordingTheValue() {
      listOf(
         "http://qqq.example.com", "http://127.0.0.1:8080/", "https://user:pw@qqq.example.com", "https://qqq.example.com/app",
         "https://qqq.example.com/?t=1", "https://qqq.example.com/#f", "ftp://qqq.example.com", "not a url", "https://", "https://" + "a".repeat(2100)
      ).forEach { value ->
         val parsed = ManagedConfiguration.parse(value, null)
         assertNull(value, parsed.serverUrl)
         assertEquals(listOf(MANAGED_SERVER_URL), parsed.rejectedKeys)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun orientationAcceptsEveryExistingNameExactly() {
      OrientationPolicy.entries.forEach { assertEquals(it, ManagedConfiguration.parse(null, it.name).orientation) }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun unknownOrientationIsRejectedNotDefaultedToPortrait() {
      listOf("portrait", "Landscape", "AUTO", "SENSOR", "90", "REVERSE-PORTRAIT").forEach { value ->
         val parsed = ManagedConfiguration.parse(null, value)
         assertNull(value, parsed.orientation)
         assertEquals(listOf(MANAGED_ORIENTATION), parsed.rejectedKeys)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun aBadValueDoesNotBlockTheGoodOne() {
      val parsed = ManagedConfiguration.parse("http://bad.example.com", "LANDSCAPE")
      assertNull(parsed.serverUrl)
      assertEquals(OrientationPolicy.LANDSCAPE, parsed.orientation)
      assertEquals(listOf(MANAGED_SERVER_URL), parsed.rejectedKeys)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun serverIsNeverSwitchedSilently() {
      val managed = "https://managed.example.com/"
      assertEquals(ManagedServerAction.NONE, ManagedConfiguration.serverAction("https://other.example.com/", null))
      assertEquals(ManagedServerAction.NONE, ManagedConfiguration.serverAction(null, null))
      assertEquals(ManagedServerAction.PREFILL, ManagedConfiguration.serverAction(null, managed))
      assertEquals(ManagedServerAction.NONE, ManagedConfiguration.serverAction(managed, managed))
      assertEquals(ManagedServerAction.OFFER_CHANGE, ManagedConfiguration.serverAction("https://other.example.com/", managed))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun orientationIsAppliedOnlyWhenTheManagedValueChanges() {
      assertNull(ManagedConfiguration.orientationToApply(null, null))
      assertNull(ManagedConfiguration.orientationToApply(null, "LANDSCAPE"))
      assertEquals(OrientationPolicy.LANDSCAPE, ManagedConfiguration.orientationToApply(OrientationPolicy.LANDSCAPE, null))
      assertNull(ManagedConfiguration.orientationToApply(OrientationPolicy.LANDSCAPE, "LANDSCAPE"))
      assertEquals(OrientationPolicy.SYSTEM, ManagedConfiguration.orientationToApply(OrientationPolicy.SYSTEM, "LANDSCAPE"))
   }
}
