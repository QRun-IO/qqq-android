/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** The optional presentation hints published by mobile-scanners 1.x. */
internal data class ScannerMetaData(val manualLabel: String = "Manual barcode", val submitLabel: String = "Submit barcode", val maxLength: Int = 4096)
{
   companion object
   {
      fun parse(values: Map<String, Any?>): ScannerMetaData
      {
         val suppliedLength = values["maxLength"]
         val maxLength = if(!values.containsKey("maxLength")) 4096 else
         {
            require(suppliedLength is Number) { "SCANNER maxLength must be an integer between 1 and 4096." }
            val number = suppliedLength.toDouble()
            require(number.isFinite() && number in 1.0..4096.0 && number == number.toInt().toDouble())
            { "SCANNER maxLength must be an integer between 1 and 4096." }
            number.toInt()
         }
         return ScannerMetaData(
         manualLabel = (values["manualLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Manual barcode",
         submitLabel = (values["submitLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Submit barcode",
         maxLength = maxLength,
      )
      }
   }
}
