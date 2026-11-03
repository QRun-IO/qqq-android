/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.viewmodel

import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFieldType

/*******************************************************************************
 ** A supplied null is an intentional value; defaults apply only to absent fields.
 *******************************************************************************/
internal fun semanticInitialValue(field: QFieldMetaData, values: Map<String, Any?>): Any? =
   if (values.containsKey(field.name)) values[field.name] else field.defaultValue

/*******************************************************************************
 ** Convert input using the server field type while preserving decimal precision and intentional null values.
 *******************************************************************************/
internal fun semanticFieldValue(field: QFieldMetaData, raw: Any?): Any? {
   if (raw == null || raw == "") return null
   val text = raw.toString()
   return when (field.type) {
      QFieldType.INTEGER -> text.toIntOrNull()
      QFieldType.LONG -> text.toLongOrNull()
      // Preserve decimal precision; the semantic API accepts numeric strings.
      QFieldType.DECIMAL -> text
      QFieldType.BOOLEAN -> if (raw is Boolean) raw else text.toBooleanStrictOrNull()
      else -> raw
   }
}

/*******************************************************************************
 ** Apply the advertised field constraints before submission and return an operator-facing validation message.
 *******************************************************************************/
internal fun validateSemanticField(field: QFieldMetaData, raw: Any?): String? {
   val text = raw?.toString().orEmpty()
   val rules = field.validation
   if (text.isEmpty()) return if (field.isRequired || rules?.required == true) "${field.label} is required." else null
   if (field.type in setOf(QFieldType.INTEGER, QFieldType.LONG, QFieldType.BOOLEAN) && semanticFieldValue(field, raw) == null)
      return "Enter a valid ${field.type.name.lowercase()} value."
   val numeric = text.toBigDecimalOrNull()
   if (field.type == QFieldType.DECIMAL && numeric == null) return "Enter a valid decimal value."
   val validDate = when (field.type) {
      QFieldType.DATE -> runCatching { java.time.LocalDate.parse(text) }.isSuccess
      QFieldType.TIME -> runCatching { java.time.LocalTime.parse(text) }.isSuccess
      QFieldType.DATE_TIME -> runCatching { java.time.OffsetDateTime.parse(text) }.isSuccess
      else -> true
   }
   if (!validDate) return semanticFieldHint(field) ?: "Enter a valid date or time."
   if (rules?.maxLength != null && text.length > rules.maxLength!! && rules.tooLongBehavior == "ERROR")
      return "${field.label} must contain at most ${rules.maxLength} characters."
   if (numeric != null) {
      rules?.minValue?.toBigDecimalOrNull()?.let { if (numeric < it || (numeric == it && rules.minInclusive == false)) return "${field.label} is below the allowed minimum." }
      rules?.maxValue?.toBigDecimalOrNull()?.let { if (numeric > it || (numeric == it && rules.maxInclusive == false)) return "${field.label} exceeds the allowed maximum." }
   }
   rules?.pattern?.let { pattern ->
      val regex = runCatching { Regex(pattern) }.getOrNull()
      if (regex != null && !regex.matches(text)) return rules.patternMessage ?: "${field.label} does not match the required format."
   }
   if (!field.inlinePossibleValueSource.isNullOrEmpty() && field.inlinePossibleValueSource!!.none { it.id.toString() == text })
      return "Choose one of the available values for ${field.label}."
   return null
}

/*******************************************************************************
 ** Describe the wire date/time syntax accepted by semantic validation rather than a locale-dependent format.
 *******************************************************************************/
internal fun semanticFieldHint(field: QFieldMetaData): String? = when (field.type) {
   QFieldType.DATE -> "Use YYYY-MM-DD."
   QFieldType.TIME -> "Use HH:mm:ss."
   QFieldType.DATE_TIME -> "Use YYYY-MM-DDTHH:mm:ssZ or a UTC offset, for example 2026-10-30T09:00:00-05:00."
   else -> null
}
