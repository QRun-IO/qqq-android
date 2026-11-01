/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class DiagnosticPresentationTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   private fun event(code: DiagnosticCode, status: Int? = 200) = DiagnosticEvent(Instant.parse("2026-11-01T18:00:00Z"), code, status, 12)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun problemsIncludeTransportHttpAndInvalidSuccessfulResponses() {
      val success = event(DiagnosticCode.CONNECTION_OK)
      val forbidden = event(DiagnosticCode.DEVICE_RESPONSE, 403)
      val malformed = event(DiagnosticCode.CONNECTION_FAILED)
      val transport = event(DiagnosticCode.NATIVE_TRANSPORT_FAILED, null)
      assertEquals(listOf(transport, malformed, forbidden), diagnosticRows(listOf(success, forbidden, malformed, transport), true))
      assertEquals(listOf(transport, malformed, forbidden, success), diagnosticRows(listOf(success, forbidden, malformed, transport), false))
      assertTrue(malformed.detail().contains("Unexpected status response"))
      assertTrue(transport.detail().contains("No HTTP response"))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun connectionSummaryNeverUsesAppDeviceOrSessionResponses() {
      val check = event(DiagnosticCode.CONNECTION_FAILED, 503)
      val activity = listOf(event(DiagnosticCode.DEVICE_RESPONSE), event(DiagnosticCode.SESSION_RESPONSE), event(DiagnosticCode.NATIVE_RESPONSE))
      assertNull(latestConnectionCheck(activity))
      assertEquals(check, latestConnectionCheck(listOf(check) + activity))
      assertEquals("Device request completed", activity[0].title())
      assertEquals("Session request completed", activity[1].title())
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun exportUsesFriendlyLabelsLocalDateAndFixedFieldsOnly() {
      val events = DiagnosticCode.values().map { event(it, 401) }
      val report = diagnosticReport(events, false, ZoneId.of("America/Chicago"))
      assertTrue(report.contains("2026-11-01 12:00:00 CST"))
      assertTrue(report.contains("Sign-in required or expired"))
      assertTrue(report.contains("reachability only"))
      DiagnosticCode.values().forEach { assertFalse(report.contains(it.name)) }
      listOf("http://", "https://", "Cookie", "Authorization", "token=", "password=", "?").forEach { assertFalse(report.contains(it)) }
      assertTrue(diagnosticReport(emptyList(), true).endsWith("No events."))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun snapshotsAreIndependentBoundedAndClearable() {
      val log = DiagnosticLog(2)
      log.record(DiagnosticCode.CONNECTION_OK, 200, 1)
      val saved = log.snapshot()
      log.record(DiagnosticCode.DEVICE_RESPONSE, 403, 2)
      log.record(DiagnosticCode.SESSION_RESPONSE, 500, 3)
      assertEquals(1, saved.size)
      assertEquals(listOf(DiagnosticCode.DEVICE_RESPONSE, DiagnosticCode.SESSION_RESPONSE), log.snapshot().map { it.code })
      log.clear()
      assertTrue(log.snapshot().isEmpty())
      assertEquals(1, saved.size)
   }
}
