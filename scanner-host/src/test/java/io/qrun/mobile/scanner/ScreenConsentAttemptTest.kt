/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class ScreenConsentAttemptTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun consentIsSingleUseAndCannotBeReplayedAfterCancellation() {
      val attempt = ScreenConsentAttempt()
      val first = attempt.begin()
      assertFalse(attempt.consume(first, false, true))
      assertFalse(attempt.consume(first, true, true))
      val next = attempt.begin()
      assertFalse(attempt.consume(first, true, true))
      assertTrue(attempt.consume(next, true, true))
      assertFalse(attempt.consume(next, true, true))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun logoutServerChangeOrDisposalRejectsPendingAndLateServiceCallbacks() {
      val attempt = ScreenConsentAttempt()
      val ticket = attempt.begin()
      attempt.invalidate()
      assertFalse(attempt.current(ticket))
      assertFalse(attempt.consume(ticket, true, true))
      val fresh = attempt.begin()
      assertTrue(attempt.consume(fresh, true, true))
      attempt.invalidate()
      assertFalse(attempt.current(fresh))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun approvalLossAndProcessRecreationNeverAdoptAnOldConsentResult() {
      val attempt = ScreenConsentAttempt()
      val ticket = attempt.begin()
      assertFalse(attempt.consume(ticket, true, false))
      assertFalse(attempt.consume(ticket, true, true))
      val recreated = ScreenConsentAttempt()
      recreated.begin()
      assertFalse(recreated.consume(ticket, true, true))
   }
}
