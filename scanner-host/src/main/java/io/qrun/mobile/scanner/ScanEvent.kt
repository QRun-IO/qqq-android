/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.time.Instant
import java.util.UUID

/*******************************************************************************
 ** One physical scan remains one event even when its HTTP request is retried.
 *******************************************************************************/
data class ScanEvent(
   val barcode: String,
   val symbology: String,
   val scanSource: String,
   val eventId: String = UUID.randomUUID().toString(),
   val capturedAt: String = Instant.now().toString()
) {
   /*******************************************************************************
    ** A fixed schema prevents arbitrary intent extras becoming process inputs.
    *******************************************************************************/
   fun values(): Map<String, Any> = mapOf(
      "barcode" to barcode, "symbology" to symbology, "scanSource" to scanSource,
      "eventId" to eventId, "capturedAt" to capturedAt
   )
}
