/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** The optional presentation hints published by mobile-scanners 1.x. */
internal data class ScannerMetaData(val manualLabel: String = "Manual barcode", val submitLabel: String = "Submit barcode", val maxLength: Int = 4096)
{
   companion object
   {
      fun parse(values: Map<String, Any?>): ScannerMetaData = ScannerMetaData(
         manualLabel = (values["manualLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Manual barcode",
         submitLabel = (values["submitLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Submit barcode",
         maxLength = (values["maxLength"] as? Number)?.toInt()?.takeIf { it in 1..4096 } ?: 4096,
      )
   }
}
