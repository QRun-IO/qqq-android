/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QRecordSection
import com.kingsrook.qqq.frontend.android.core.model.metadata.QTableMetaData
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordPresentationTest {
   @Test
   fun typedDatesFormatEvenWhenTheirRawJsonRepresentationDiffersFromDisplayText() {
      val record = RecordData(values = mapOf("date" to JsonArray(listOf(JsonPrimitive(2026), JsonPrimitive(1), JsonPrimitive(2)))),
         displayValues = mapOf("date" to JsonPrimitive("2026-01-02"), "time" to JsonPrimitive("2026-01-02T11:30:00Z")))
      // The semantic server supplies %s for fields without a custom display format.
      val date = record.formatted(QFieldMetaData("date", "Date", type = QFieldType.DATE, displayFormat = "%s"))
      assertTrue(date.contains("2026"))
      assertFalse(date.contains("2026-01-02"))
      assertFalse(record.formatted(QFieldMetaData("time", "Time", type = QFieldType.DATE_TIME, displayFormat = "%s")).contains("T11:30"))
   }

   @Test
   fun formattingPreservesIdentifiersAndServerCurrencyAndMakesEmptyValuesExplicit() {
      val record = RecordData(values = mapOf("key" to JsonPrimitive("000026"), "amount" to JsonPrimitive(3.5), "enabled" to JsonPrimitive(true)), displayValues = mapOf("amount" to JsonPrimitive("$3.50")))
      assertEquals("000026", record.formatted(QFieldMetaData("key", "Key", type = QFieldType.STRING)))
      assertEquals("$3.50", record.formatted(QFieldMetaData("amount", "Amount", type = QFieldType.DECIMAL, displayFormat = "$%,.2f")))
      assertEquals("Yes", record.formatted(QFieldMetaData("enabled", "Enabled", type = QFieldType.BOOLEAN)))
      assertEquals("—", record.formatted(QFieldMetaData("missing", "Missing", type = QFieldType.STRING)))
   }

   @Test
   fun searchAndSummaryNeverExposeHiddenSectionFields() {
      val field = QFieldMetaData("visible", "Visible", type = QFieldType.STRING)
      val hidden = field.copy(name = "secret", label = "Secret")
      val table = QTableMetaData("test", "Test", fields = mapOf("visible" to field, "secret" to hidden),
         searchFields = listOf("secret", "visible"), sections = listOf(QRecordSection("hidden", fieldNames = listOf("secret"), hidden = true)))
      val record = RecordData(values = mapOf("visible" to JsonPrimitive("public"), "secret" to JsonPrimitive("private")))
      assertEquals(listOf("visible"), table.recordSearchFields())
      assertEquals("Visible: public", record.summary(table))
   }
}
