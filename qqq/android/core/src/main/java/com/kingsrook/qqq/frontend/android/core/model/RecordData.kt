/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*******************************************************************************
 ** Versioned table responses preserve opaque primary keys and display values.
 *******************************************************************************/
@Serializable
data class RecordData(
   val tableName: String? = null,
   val recordLabel: String? = null,
   val values: Map<String, JsonElement> = emptyMap(),
   val displayValues: Map<String, JsonElement> = emptyMap(),
) {
   fun id(primaryKey: String): String? = (values[primaryKey] as? JsonPrimitive)?.contentOrNull
   fun display(field: String): String = ((displayValues[field] ?: values[field]) as? JsonPrimitive)?.contentOrNull.orEmpty()
}

/*******************************************************************************
 ** Query records are unwrapped; a get response wraps exactly one record.
 *******************************************************************************/
@Serializable
data class RecordQueryResponse(val records: List<RecordData> = emptyList())

@Serializable
data class RecordGetResponse(val record: RecordData)
