/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.time.Instant
import java.util.UUID

/*******************************************************************************
 ** One physical scan remains one event even when its HTTP request is retried.
 *******************************************************************************/
internal data class ScanEvent(
   val capture: ScannerCapture,
   val scanSource: String,
   val eventId: String = UUID.randomUUID().toString(),
   val capturedAt: String = Instant.now().toString()
) {
   constructor(barcode: String, symbology: String, scanSource: String) :
      this(ScannerCapture.of(barcode, symbology), scanSource)

   val barcode: String get() = capture.barcode
   val symbology: String get() = capture.symbology

   /*******************************************************************************
    ** A fixed schema prevents arbitrary intent extras becoming process inputs. The optional fidelity
    ** keys are added only for servers verified to accept them; absent values are omitted, never blank.
    *******************************************************************************/
   fun values(includeFidelity: Boolean = false): Map<String, Any> {
      val values = linkedMapOf<String, Any>(
         "barcode" to barcode, "symbology" to symbology, "scanSource" to scanSource,
         "eventId" to eventId, "capturedAt" to capturedAt
      )
      if (includeFidelity) {
         capture.aimIdentifier?.let { values["aimIdentifier"] = it }
         capture.rawDataBase64?.let { values["rawDataBase64"] = it }
      }
      return values
   }

   /*******************************************************************************
    ** Scan content must not appear in logs; the event identity is an opaque correlation value.
    *******************************************************************************/
   override fun toString(): String = "ScanEvent(redacted, source=$scanSource, eventId=$eventId)"
}
