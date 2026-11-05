/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QTableMetaData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The same predicate is used by page and count requests. Parent matches remain mandatory. */
data class RecordSearch(
   val text: String = "",
   val searchFields: List<String> = emptyList(),
   val matches: Map<String, JsonElement> = emptyMap(),
) {
   fun filter(): JSONObject {
      val criteria = JSONArray()
      matches.forEach { (name, value) ->
         require(name.isNotBlank() && value is JsonPrimitive && value != JsonNull)
         criteria.put(JSONObject().put("fieldName", name).put("operator", "EQUALS").put("values", JSONArray("[$value]")))
      }
      val result = JSONObject().put("booleanOperator", "AND").put("criteria", criteria)
      if (text.isNotBlank()) {
         require(searchFields.isNotEmpty() && searchFields.all(String::isNotBlank))
         val search = JSONArray()
         searchFields.forEach { search.put(JSONObject().put("fieldName", it).put("operator", "CONTAINS").put("values", JSONArray().put(text))) }
         result.put("subFilters", JSONArray().put(JSONObject().put("booleanOperator", "OR").put("criteria", search)))
      }
      return result
   }
}

@Serializable
data class RecordCountResponse(val count: Long)

/** Search only visible scalar text fields; server metadata defines preferred fields. */
fun QTableMetaData.textFields(): List<QFieldMetaData> = recordGroups().flatMap { it.fields }
   .filter { it.type == QFieldType.STRING || it.type == QFieldType.TEXT }

fun QTableMetaData.recordSearchFields(): List<String> {
   val visible = textFields().map { it.name }
   return searchFields.orEmpty().filter { it in visible }.ifEmpty { visible.take(3) }
}

/** Preserve server formatting and identifiers. Only typed dates and numbers receive a local fallback. */
fun RecordData.formatted(field: QFieldMetaData): String {
   val value = display(field.name)
   if (value.isBlank()) return "—"
   if (!field.displayFormat.isNullOrBlank() && field.displayFormat != "%s") return value
   return runCatching {
      when (field.type) {
         QFieldType.DATE -> LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
         QFieldType.DATE_TIME -> Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a z"))
         QFieldType.DECIMAL -> NumberFormat.getNumberInstance().apply { maximumFractionDigits = 8 }.format(value.toBigDecimal())
         QFieldType.BOOLEAN -> when (value) { "true" -> "Yes"; "false" -> "No"; else -> value }
         else -> value
      }
   }.getOrDefault(value)
}

/** Compact summaries follow metadata order, omit the primary key and never expose hidden groups. */
fun RecordData.summary(table: QTableMetaData): String = table.recordGroups().flatMap { it.fields }
   .filter { it.name != table.primaryKeyField && display(it.name).isNotBlank() && display(it.name).length <= 80 && recordLabel?.contains(display(it.name)) != true }
   .take(3).joinToString(" · ") { "${it.label}: ${formatted(it)}" }
