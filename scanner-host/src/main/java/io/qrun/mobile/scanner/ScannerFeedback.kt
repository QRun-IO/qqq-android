/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.ProcessAcknowledgment

/*******************************************************************************
 ** The most recent submitted read, kept in memory only so it stays visible while awaiting the next scan.
 ** Manual entry has no symbology; it is never labelled with a guessed one.
 *******************************************************************************/
internal data class LastScan(val eventId: String, val barcode: String, val symbology: String?) {
   /*******************************************************************************
    ** Scan content is business data and must not reach logs or crash reports.
    *******************************************************************************/
   override fun toString(): String = "LastScan(redacted, eventId=$eventId)"

   /*******************************************************************************
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Manual submissions carry no scanner evidence, so their symbology is absent.
       *******************************************************************************/
      fun of(event: ScanEvent) = LastScan(event.eventId, event.barcode, if (event.scanSource == "manual") null else event.symbology)
   }
}

/*******************************************************************************
 ** What the read-only scan display shows: the exact value, its symbology, and where the request stands.
 *******************************************************************************/
internal data class ScanFeedback(val barcode: String, val symbology: String, val details: String, val status: String) {
   /*******************************************************************************
    ** Scan content is business data and must not reach logs or crash reports.
    *******************************************************************************/
   override fun toString(): String = "ScanFeedback(redacted, status=$status)"
}

/*******************************************************************************
 ** Derives scan feedback from the process's own pending/acknowledgment state; it adds no state machine.
 *******************************************************************************/
internal object ScannerFeedback {
   const val WAITING = "Waiting for scan"
   const val STATE_KEY = "scanner.lastScan"

   /*******************************************************************************
    ** The one receipt definition: submission and display must agree on what counts as confirmation.
    *******************************************************************************/
   fun acknowledgment(eventId: String) = ProcessAcknowledgment(
      "acknowledgedEventId", eventId, requiredValues = mapOf("scanSaved" to true), nonEmptyStringFields = setOf("scanId")
   )
   const val MANUAL_SYMBOLOGY = "Not applicable (manual entry)"

   /*******************************************************************************
    ** A pending request (including a retryable one) is authoritative because retries resend it unchanged;
    ** otherwise the last submitted read stays visible, whether confirmed, rejected or cancelled.
    *******************************************************************************/
   fun derive(
      last: LastScan?, pendingValues: Map<String, Any?>?, responseValues: Map<String, Any?>, waiting: Boolean, cancelling: Boolean
   ): ScanFeedback? {
      val pendingId = pendingValues?.get("eventId") as? String
      val pendingBarcode = pendingValues?.get("barcode") as? String
      val shown = if (pendingId != null && pendingBarcode != null)
         LastScan(
            pendingId, pendingBarcode,
            if (pendingValues["scanSource"] == "manual") null else pendingValues["symbology"] as? String
         )
      else last
      if (shown == null) return null
      val status = when {
         acknowledgment(shown.eventId).matches(responseValues) -> "Confirmed by server"
         cancelling -> "Cancelling. Scan was not confirmed."
         pendingId == shown.eventId && waiting -> "Sending scan…"
         pendingId == shown.eventId -> "Not confirmed. Retry the same request or leave the process; do not assume it was saved."
         else -> "Not confirmed. Follow the process message before submitting again."
      }
      return ScanFeedback(shown.barcode, shown.symbology ?: MANUAL_SYMBOLOGY, describe(shown.barcode), status)
   }

   /*******************************************************************************
    ** Control characters are invisible in a text field, so report them without altering the value.
    *******************************************************************************/
   fun describe(barcode: String): String {
      val controls = barcode.count { Character.isISOControl(it) }
      val length = "${barcode.length} ${if (barcode.length == 1) "character" else "characters"}"
      return if (controls == 0) length else "$length, $controls control ${if (controls == 1) "character" else "characters"}"
   }
}
