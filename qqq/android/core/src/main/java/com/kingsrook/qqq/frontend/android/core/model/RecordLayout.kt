/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QRecordAssociation
import com.kingsrook.qqq.frontend.android.core.model.metadata.QTableMetaData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

data class RecordFieldGroup(val label: String, val fields: List<QFieldMetaData>)

/** Retain metadata order, suppress hidden sections, and show ungrouped fields once. */
fun QTableMetaData.recordGroups(): List<RecordFieldGroup> {
   val sections = sections.orEmpty()
   val hidden = sections.filter { it.isHidden || it.hidden }.flatMap { it.fieldNames.orEmpty() }.toSet()
   val remaining = fields.filterValues { !it.isHidden && !it.isHeavy }.toMutableMap()
   hidden.forEach(remaining::remove)
   val groups = sections.filter { !it.isHidden && !it.hidden }.mapNotNull { section ->
      val members = section.fieldNames.orEmpty().mapNotNull(remaining::remove)
      if (members.isEmpty()) null else RecordFieldGroup(section.label ?: section.name, members)
   }.toMutableList()
   if (remaining.isNotEmpty()) groups.add(RecordFieldGroup(if (groups.isEmpty()) "Details" else "Other fields", remaining.values.toList()))
   return groups
}

/** Resolve only declared, direct equality joins. Missing values must never become an unfiltered query. */
fun QRecordAssociation.parentMatches(parentTable: String, record: RecordData): Map<String, JsonElement>? {
   val forward = join.leftTable == parentTable && join.rightTable == associatedTableName
   val reverse = join.rightTable == parentTable && join.leftTable == associatedTableName
   if ((!forward && !reverse) || join.joinOns.isEmpty()) return null
   val matches = linkedMapOf<String, JsonElement>()
   for (on in join.joinOns) {
      val source = if (forward) on.leftField else on.rightField
      val target = if (forward) on.rightField else on.leftField
      val value = record.values[source]
      if (target.isBlank() || source.isBlank() || value !is JsonPrimitive || value == JsonNull || target in matches) return null
      matches[target] = value
   }
   return matches
}
