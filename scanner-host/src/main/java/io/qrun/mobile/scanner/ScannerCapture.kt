/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import java.util.Base64

/*******************************************************************************
 ** One immutable decoded read: the SDK's text and symbology, the SDK's raw bytes already encoded once as
 ** canonical padded Base64, and an AIM identifier only when a qualified driver supplied it explicitly.
 ** Raw bytes are exact SDK output (possibly with SDK framing), not a claim of complete symbol capture.
 *******************************************************************************/
internal data class ScannerCapture(
   val barcode: String, val symbology: String, val rawDataBase64: String? = null, val aimIdentifier: String? = null
) {
   init {
      require(aimIdentifier == null || AIM.matches(aimIdentifier)) { "Invalid AIM identifier." }
   }

   /*******************************************************************************
    ** Scan content is business data and must not reach logs or crash reports.
    *******************************************************************************/
   override fun toString(): String = "ScannerCapture(redacted, raw=${if (rawDataBase64 == null) "absent" else "present"}, aim=${if (aimIdentifier == null) "absent" else "present"})"

   /*******************************************************************************
    ** Raw limits live here so every source builds captures under the same transport limits.
    *******************************************************************************/
   companion object {
      const val MAX_RAW_BYTES = 8192
      private val AIM = Regex("\\][A-Za-z][0-9A-Za-z]")

      /*******************************************************************************
       ** Encode SDK bytes once; the caller's array is never retained. Null, empty or oversize raw data is
       ** omitted rather than truncated, so an optional field is either exact SDK output or absent.
       *******************************************************************************/
      fun of(barcode: String, symbology: String, rawData: ByteArray? = null, aimIdentifier: String? = null): ScannerCapture =
         ScannerCapture(
            barcode, symbology,
            if (rawData == null || rawData.isEmpty() || rawData.size > MAX_RAW_BYTES) null else Base64.getEncoder().encodeToString(rawData),
            aimIdentifier
         )

      /*******************************************************************************
       ** Manual entry has no scanner evidence, so it carries neither raw bytes nor an AIM identifier.
       *******************************************************************************/
      fun manual(barcode: String) = ScannerCapture(barcode, "MANUAL")
   }
}
