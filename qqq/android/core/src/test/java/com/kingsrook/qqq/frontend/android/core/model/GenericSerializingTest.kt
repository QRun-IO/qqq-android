/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qqq.frontend.android.core.model


import com.kingsrook.qqq.frontend.android.core.model.metadata.QComponentType
import com.kingsrook.qqq.frontend.android.core.model.metadata.QFrontendComponent
import com.kingsrook.qqq.frontend.android.core.model.processes.QJobComplete
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*******************************************************************************
 **
 *******************************************************************************/
class GenericSerializingTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testThatCameWithCode() {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Serializable
      data class MySerializableData(
         val mapValues: Map<String, @Serializable(with = AnyValueSerializer::class) Any?>
      )

      /*******************************************************************************
       **
       *******************************************************************************/
      @Serializable
      @ExperimentalSerializationApi
      data class OtherDataClass(
         val intVal: Int,
         val strVal: String,
         @Serializable(with = AnyValueSerializer::class)
         val anyVal: Any? = null,
      )


      val map = mapOf<String, Any?>(
         "Foo" to "bar",
         "One" to 1L, // note, Int comes through as Long, so, Long...
         "Two dot zero" to 2.0,
         "True" to true,
         "None" to null,
         "Data" to mapOf("intVal" to 5L, "strVal" to "Cinque")
      )
      val mapData = MySerializableData(map)

      val encoded = Json.encodeToString(mapData)

      val decoded = Json.decodeFromString<MySerializableData>(encoded)

      assertEquals(mapData, decoded)

      val mapStringAny = MapStringAny(map)
      val encoded2 = Json.encodeToString(mapStringAny)

      val decoded2 = Json.decodeFromString<MapStringAny>(encoded2)

      assertEquals(mapStringAny, decoded2)
   }

   /*******************************************************************************
    ** Business discriminator keys remain data, including class-like strings and nested nulls.
    *******************************************************************************/
   @Test
   fun typeAndValueObjectsNeverSelectRuntimeClasses() {
      val payload = """{"processUUID":"p","values":{"weight":{"type":"weight","value":12},"nested":{"type":"java.lang.Runtime","value":{"type":7,"value":null}}}}"""
      val decoded = Json.decodeFromString<QJobComplete>(payload)
      assertEquals(mapOf("type" to "weight", "value" to 12L), decoded.values["weight"])
      assertEquals(mapOf("type" to "java.lang.Runtime", "value" to mapOf("type" to 7L, "value" to null)), decoded.values["nested"])
      val roundTrip = Json.decodeFromString<QJobComplete>(Json.encodeToString(decoded))
      assertEquals(decoded, roundTrip)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testQFrontendComponent() {
      val component = Json.decodeFromString<QFrontendComponent>(
         """
         {
            "type":  "HELP_TEXT",
            "values":
            {
               "helpText": "Lorem ipsum"
            }
         }"""
      )

      assertEquals(QComponentType.HELP_TEXT, component.type)
      assertEquals(mapOf("helpText" to "Lorem ipsum"), component.values)
      assertEquals(1, component.values.size)
   }

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun testQJobCompleteWithArray() {
      val jobComplete = Json.decodeFromString<QJobComplete>(
         """
         {
            "processUUID":  "123-123-123-123",
            "nextStep": "Underpants",
            "values":
            {
               "someString": "Nylon",
               "someArray":
               [
                  {"foo": "bar", "one": 1}
               ],
               "someObject": {"foo": "bar", "one": 1},
               "someNull": null
            }
         }"""
      )

      assertEquals("123-123-123-123", jobComplete.processUUID)
      assertEquals("Underpants", jobComplete.nextStep)
      assertEquals("Nylon", jobComplete.values["someString"])
      assertEquals(listOf(mapOf("foo" to "bar", "one" to 1L)), jobComplete.values["someArray"])
      assertEquals(mapOf("foo" to "bar", "one" to 1L), jobComplete.values["someObject"])
      assertNull(jobComplete.values["someNull"])

   }
}
