/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class ScannerMetaDataTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun serverDefinesLabelsAndLengthWithContractDefaults() {
      assertEquals(ScannerMetaData(), ScannerMetaData.parse(emptyMap()))
      assertEquals("Saved scan", ScannerMetaData.parse(emptyMap()).acknowledgmentLabel)
      assertEquals(true, ScannerMetaData.parse(emptyMap()).manualEntryEnabled)
      assertEquals(ScannerMetaData("Item barcode", "Save item", 30), ScannerMetaData.parse(mapOf("manualLabel" to "Item barcode", "submitLabel" to "Save item", "maxLength" to 30L, "future" to true)))
   }

   /*******************************************************************************
    ** Manual input stays available by default and can be hidden independently of receipt presentation.
    *******************************************************************************/
   @Test
   fun manualEntryVisibilityUsesExplicitBooleanHints() {
      assertEquals(true, ScannerMetaData.parse(mapOf("manualEntryEnabled" to true)).manualEntryEnabled)
      assertEquals(false, ScannerMetaData.parse(mapOf("manualEntryEnabled" to false)).manualEntryEnabled)
      assertEquals(ScannerMetaData(acknowledgmentLabel = "Print queued", manualEntryEnabled = false),
         ScannerMetaData.parse(mapOf("acknowledgmentLabel" to "Print queued", "manualEntryEnabled" to false)))
   }

   /*******************************************************************************
    ** Supplied flags must be JSON booleans, without number or string coercion.
    *******************************************************************************/
   @Test
   fun explicitlyInvalidManualEntryHintsFailClosed() {
      for (value in listOf(null, 0, 1, 1.0, "true", "false", "", listOf(true), mapOf("enabled" to true))) {
         assertThrows(IllegalArgumentException::class.java) { ScannerMetaData.parse(mapOf("manualEntryEnabled" to value)) }
      }
   }

   /*******************************************************************************
    ** A server may describe accepted work without changing the scan receipt contract.
    *******************************************************************************/
   @Test
   fun acknowledgmentLabelsDescribeTheAcceptedOperation() {
      assertEquals("Print queued", ScannerMetaData.parse(mapOf("acknowledgmentLabel" to "Print queued")).acknowledgmentLabel)
      assertEquals("Étiquette prête", ScannerMetaData.parse(mapOf("acknowledgmentLabel" to "Étiquette prête")).acknowledgmentLabel)
      val longestLabel = "Q".repeat(80)
      assertEquals(longestLabel, ScannerMetaData.parse(mapOf("acknowledgmentLabel" to longestLabel)).acknowledgmentLabel)
   }

   /*******************************************************************************
    ** Reject malformed supplied hints rather than silently displaying an unrelated success label.
    *******************************************************************************/
   @Test
   fun explicitlyInvalidAcknowledgmentLabelsFailClosed() {
      for (value in listOf(null, 42, false, "", " ", " Print queued", "Print queued ", "Q".repeat(81),
         "Print\nqueued", "Print\rqueued", "Print\tqueued", "Print\u0000queued", "Print\u2028queued", "Print\u2029queued",
         "Print\u202Equeued", "<b>Print queued</b>")) {
         assertThrows(IllegalArgumentException::class.java) { ScannerMetaData.parse(mapOf("acknowledgmentLabel" to value)) }
      }
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun explicitlyInvalidLengthFailsClosedInsteadOfBroadeningTheConstraint() {
      for (value in listOf(null, -1, 0, 4097, "10", 1.5, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE)) {
         assertThrows(IllegalArgumentException::class.java) { ScannerMetaData.parse(mapOf("maxLength" to value)) }
      }
   }
}
