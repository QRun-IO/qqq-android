/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*******************************************************************************
 ** The visible scan must survive acknowledgment and retry unchanged, never invent a symbology, and
 ** claim success only on the same receipt the process requires.
 *******************************************************************************/
class ScannerFeedbackTest {
   private val event = ScanEvent(ScannerCapture.of("00123\u001d45", "LABEL-TYPE-CODE128"), "zebra-emdk")
   private val receipt = mapOf("acknowledgedEventId" to event.eventId, "scanSaved" to true, "scanId" to "7")

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun derive(last: LastScan?, pending: Map<String, Any?>?, response: Map<String, Any?>, waiting: Boolean = false, cancelling: Boolean = false) =
      ScannerFeedback.derive(last, pending, response, waiting, cancelling)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun nothingScannedYieldsNoFeedbackSoThePanelShowsWaiting() {
      assertNull(derive(null, null, emptyMap()))
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun lastCaptureStaysVisibleAfterFullReceiptClearsPendingState() {
      val feedback = derive(LastScan.of(event), null, receipt)!!
      assertEquals("00123\u001d45", feedback.barcode)
      assertEquals("LABEL-TYPE-CODE128", feedback.symbology)
      assertEquals("Confirmed by server", feedback.status)
      assertEquals("8 characters, 1 control character", feedback.details)
   }

   /*******************************************************************************
    ** The event id alone is not a receipt: scanSaved=true and a non-blank scanId are also required.
    *******************************************************************************/
   @Test
   fun incompleteReceiptsAreNeverReportedAsConfirmed() {
      val partial = listOf(
         mapOf("acknowledgedEventId" to event.eventId),
         receipt + ("scanSaved" to false),
         receipt + ("scanId" to " "),
         receipt - "scanId",
      )
      for (response in partial) assertEquals(
         "Not confirmed. Follow the process message before submitting again.", derive(LastScan.of(event), null, response)!!.status
      )
      assertEquals(
         "Not confirmed. Retry the same request or leave the process; do not assume it was saved.",
         derive(null, event.values(), partial[1])!!.status
      )
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun retryShowsTheSameValueAndTypeFromThePendingRequest() {
      val pending = event.values()
      val sending = derive(null, pending, emptyMap(), waiting = true)!!
      val failed = derive(null, pending, emptyMap())!!
      assertEquals("Sending scan…", sending.status)
      assertTrue(failed.status.startsWith("Not confirmed. Retry the same request"))
      for (feedback in listOf(sending, failed)) {
         assertEquals(event.barcode, feedback.barcode)
         assertEquals(event.symbology, feedback.symbology)
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun nextCaptureReplacesAnEarlierConfirmedOne() {
      val next = ScanEvent(ScannerCapture.of("999", "QRCODE"), "zebra-emdk")
      val feedback = derive(LastScan.of(event), next.values(), receipt, waiting = true)!!
      assertEquals("999", feedback.barcode)
      assertEquals("QRCODE", feedback.symbology)
      assertEquals("Sending scan…", feedback.status)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun cancellationIsNotReportedAsConfirmation() {
      assertEquals("Cancelling. Scan was not confirmed.", derive(LastScan.of(event), event.values(), emptyMap(), cancelling = true)!!.status)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun manualEntryNeverReceivesAGuessedSymbology() {
      val manual = ScanEvent(ScannerCapture.manual("00042"), "manual")
      assertNull(LastScan.of(manual).symbology)
      assertEquals(ScannerFeedback.MANUAL_SYMBOLOGY, derive(LastScan.of(manual), null, emptyMap())!!.symbology)
      val pending = derive(null, manual.values(), emptyMap())!!
      assertEquals(ScannerFeedback.MANUAL_SYMBOLOGY, pending.symbology)
      assertEquals("00042", pending.barcode)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun scanContentIsRedactedFromToString() {
      assertFalse(LastScan.of(event).toString().contains("00123"))
      assertFalse(derive(LastScan.of(event), null, receipt)!!.toString().contains("00123"))
   }
}
