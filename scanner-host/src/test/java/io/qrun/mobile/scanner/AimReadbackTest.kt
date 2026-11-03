/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/*******************************************************************************
 ** The driver may accept scanner text only in a format proven by read-back; everything else must fail closed.
 *******************************************************************************/
class AimReadbackTest {
   private val aim = "AIM"
   private val original = "NONE"

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun reportedAimIsConfirmedEvenIfTheSetterThrew() {
      assertEquals(AimReadback.CONFIRMED, classifyAimReadback(original, aim, aim))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun aScannerAlreadyReportingAimIsConfirmedNotUnchanged() {
      assertEquals(AimReadback.CONFIRMED, classifyAimReadback(aim, aim, aim))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun exactlyTheOriginalValueIsUnchanged() {
      assertEquals(AimReadback.UNCHANGED, classifyAimReadback(original, aim, original))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun anUnreadableOrForeignValueIsIndeterminate() {
      assertEquals(AimReadback.INDETERMINATE, classifyAimReadback(original, aim, null))
      assertEquals(AimReadback.INDETERMINATE, classifyAimReadback(original, aim, "SYMBOL"))
      assertEquals(AimReadback.INDETERMINATE, classifyAimReadback(aim, aim, null))
   }
}
