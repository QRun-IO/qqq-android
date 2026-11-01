/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** The optional presentation hints published by mobile-scanners 1.x.
 *******************************************************************************/
internal data class ScannerMetaData(val manualLabel: String = "Manual barcode", val submitLabel: String = "Submit barcode", val maxLength: Int = 4096, val acknowledgmentLabel: String = "Saved scan", val manualEntryEnabled: Boolean = true) {
   /*******************************************************************************
    ** Centralize scanner metadata validation before the widget accepts server-provided controls.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Validate scanner length limits and read optional display labels from server widget metadata with local defaults.
       *******************************************************************************/
      fun parse(values: Map<String, Any?>): ScannerMetaData {
         val suppliedLength = values["maxLength"]
         val maxLength = if (!values.containsKey("maxLength")) 4096 else {
            require(suppliedLength is Number) { "SCANNER maxLength must be an integer between 1 and 4096." }
            val number = suppliedLength.toDouble()
            require(number.isFinite() && number in 1.0..4096.0 && number == number.toInt().toDouble())
            { "SCANNER maxLength must be an integer between 1 and 4096." }
            number.toInt()
         }
         val acknowledgmentLabel = if (!values.containsKey("acknowledgmentLabel")) "Saved scan" else {
            val label = values["acknowledgmentLabel"]
            require(label is String && label.length in 1..80 && label.isNotBlank() && label == label.trim() && label.none {
               it.isISOControl() || it.category == CharCategory.FORMAT || it.category == CharCategory.LINE_SEPARATOR ||
                  it.category == CharCategory.PARAGRAPH_SEPARATOR || it == '<' || it == '>'
            }) { "SCANNER acknowledgmentLabel must be a plain, single-line label between 1 and 80 characters." }
            label
         }
         val manualEntryEnabled = if (!values.containsKey("manualEntryEnabled")) true else {
            val enabled = values["manualEntryEnabled"]
            require(enabled is Boolean) { "SCANNER manualEntryEnabled must be a boolean." }
            enabled
         }
         return ScannerMetaData(
            manualLabel = (values["manualLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Manual barcode",
            submitLabel = (values["submitLabel"] as? String)?.takeIf { it.isNotBlank() } ?: "Submit barcode",
            maxLength = maxLength,
            acknowledgmentLabel = acknowledgmentLabel,
            manualEntryEnabled = manualEntryEnabled,
         )
      }
   }
}
