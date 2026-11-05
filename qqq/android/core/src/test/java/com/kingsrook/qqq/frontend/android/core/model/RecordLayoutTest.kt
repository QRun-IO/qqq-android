/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model

import com.kingsrook.qqq.frontend.android.core.model.metadata.QTableMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.QRecordAssociation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordLayoutTest {
   @Test
   fun sectionsPreserveOrderWithoutLeakingHiddenOrDuplicatingFields() {
      val table = Json.decodeFromString<QTableMetaData>("""{
         "name":"orders","label":"Orders","fields":{
            "id":{"name":"id","label":"ID","type":"INTEGER"},
            "secret":{"name":"secret","label":"Secret","type":"STRING"},
            "other":{"name":"other","label":"Other","type":"STRING"}},
         "sections":[{"name":"hidden","hidden":true,"fieldNames":["secret"]},
            {"name":"overview","label":"Overview","fieldNames":["id","id","missing"]}]
      }""")
      assertEquals(listOf("Overview", "Other fields"), table.recordGroups().map { it.label })
      assertEquals(listOf("id", "other"), table.recordGroups().flatMap { it.fields }.map { it.name })
   }

   @Test
   fun relationshipsRespectDirectionTypesAndMissingParentKeys() {
      val association = Json.decodeFromString<QRecordAssociation>("""{
         "name":"Lines","associatedTableName":"lines",
         "join":{"leftTable":"orders","rightTable":"lines","joinOns":[{"leftField":"key","rightField":"orderKey"}]}
      }""")
      val record = RecordData(values = mapOf("key" to JsonPrimitive("0007")))
      assertEquals(mapOf("orderKey" to JsonPrimitive("0007")), association.parentMatches("orders", record))
      assertNull(association.parentMatches("unrelated", record))
      assertNull(association.parentMatches("orders", RecordData()))
      val reversed = association.copy(join = association.join.copy(leftTable = "lines", rightTable = "orders",
         joinOns = association.join.joinOns.map { it.copy(leftField = it.rightField, rightField = it.leftField) }))
      assertEquals(association.parentMatches("orders", record), reversed.parentMatches("orders", record))
   }
}
