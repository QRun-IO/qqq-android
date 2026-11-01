/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
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
 *
 */

package com.kingsrook.qqq.frontend.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer
import kotlin.reflect.full.starProjectedType

typealias MapOfStringAny = Map<String, @Serializable(with = AnyValueSerializer::class) Any?>
typealias MutableMapOfStringAny = MutableMap<String, @Serializable(with = AnyValueSerializer::class) Any?>
typealias ListOfAny = List<@Serializable(with = AnyValueSerializer::class) Any?>

/*******************************************************************************
 ** Legacy public model retained for source compatibility; it has no special meaning in API JSON.
 *******************************************************************************/
@Serializable
data class AnyValueSurrogate(val type: String, @Contextual val value: Any?)

/*******************************************************************************
 ** Legacy null marker retained for callers; API nulls use JSON null.
 *******************************************************************************/
@Serializable
object NoneType

/*******************************************************************************
 ** Wrapper retained for clients using the original generic serializer API.
 *******************************************************************************/
@Serializable(with = AnySerializableValueSerializer::class)
data class AnySerializableValue(val value: Any?)

/*******************************************************************************
 ** Delegate wrapped values to the JSON data serializer without class discovery.
 *******************************************************************************/
object AnySerializableValueSerializer : KSerializer<AnySerializableValue> {
   override val descriptor = JsonElement.serializer().descriptor

   /*******************************************************************************
    ** Delegate framing to the declared JSON serializer without introducing wire-controlled JVM class discovery.
    *******************************************************************************/
   override fun serialize(encoder: Encoder, value: AnySerializableValue) = AnyValueSerializer.serialize(encoder, value.value)

   /*******************************************************************************
    ** Reconstruct the declared wrapper through its JSON delegate instead of resolving classes from input.
    *******************************************************************************/
   override fun deserialize(decoder: Decoder) = AnySerializableValue(AnyValueSerializer.deserialize(decoder))
}

typealias SerializableMapType = MutableMap<String, AnySerializableValue>
typealias SerializableListType = MutableList<AnySerializableValue>

/*******************************************************************************
 ** Legacy collection declarations retain their original public API shape.
 *******************************************************************************/
@Serializable
abstract class SerializableMap : SerializableMapType

/*******************************************************************************
 ** Legacy collection declarations retain their original public API shape.
 *******************************************************************************/
@Serializable
abstract class SerializableList : SerializableListType

/*******************************************************************************
 ** Semantic API values are JSON data; business keys never select JVM classes.
 *******************************************************************************/
object AnyValueSerializer : KSerializer<Any?> {
   override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

   /*******************************************************************************
    ** Encode arbitrary API maps/lists without adding class-name envelopes.
    *******************************************************************************/
   override fun serialize(encoder: Encoder, value: Any?) {
      val json = encoder as? JsonEncoder ?: throw SerializationException("API values require JSON")
      json.encodeJsonElement(toJson(value))
   }

   /*******************************************************************************
    ** Decode objects uniformly, including objects whose keys happen to be type and value.
    *******************************************************************************/
   override fun deserialize(decoder: Decoder): Any? {
      val json = decoder as? JsonDecoder ?: throw SerializationException("API values require JSON")
      return fromJson(json.decodeJsonElement())
   }

   /*******************************************************************************
    ** Only serialization uses a locally supplied object's serializer; wire input never loads classes.
    *******************************************************************************/
   private fun toJson(value: Any?): JsonElement = when (value) {
      null -> JsonNull
      is JsonElement -> value
      is String -> JsonPrimitive(value)
      is Boolean -> JsonPrimitive(value)
      is Number -> JsonPrimitive(value)
      is Map<*, *> -> JsonObject(value.entries.associate { entry ->
         val name = entry.key as? String ?: throw SerializationException("JSON object keys must be strings")
         name to toJson(entry.value)
      })

      is Iterable<*> -> JsonArray(value.map(::toJson))
      else -> Json.encodeToJsonElement(serializer(value::class.starProjectedType), value)
   }

   /*******************************************************************************
    ** Preserve integer values and nulls while keeping arbitrary nested objects as ordinary maps.
    *******************************************************************************/
   private fun fromJson(value: JsonElement): Any? = when (value) {
      JsonNull -> null
      is JsonObject -> value.mapValues { fromJson(it.value) }
      is JsonArray -> value.map(::fromJson)
      is JsonPrimitive -> if (value.isString) value.content else
         value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
         ?: throw SerializationException("Invalid JSON primitive")
   }
}

/*******************************************************************************
 ** Wrapper retained for callers that serialize arbitrary JSON object values.
 *******************************************************************************/
@Serializable(with = MapStringAnySerializer::class)
data class MapStringAny(val entries: Map<String, Any?> = emptyMap())

/*******************************************************************************
 ** Delegate object framing to the standard map serializer.
 *******************************************************************************/
object MapStringAnySerializer : KSerializer<MapStringAny> {
   private val delegate = MapSerializer(String.serializer(), AnyValueSerializer)
   override val descriptor = delegate.descriptor

   /*******************************************************************************
    ** Delegate framing to the declared JSON serializer without introducing wire-controlled JVM class discovery.
    *******************************************************************************/
   override fun serialize(encoder: Encoder, value: MapStringAny) = delegate.serialize(encoder, value.entries)

   /*******************************************************************************
    ** Reconstruct the declared wrapper through its JSON delegate instead of resolving classes from input.
    *******************************************************************************/
   override fun deserialize(decoder: Decoder) = MapStringAny(delegate.deserialize(decoder))
}

/*******************************************************************************
 ** Wrapper retained for callers that serialize arbitrary JSON lists.
 *******************************************************************************/
@Serializable(with = ListAnySerializer::class)
data class ListAny(val entries: List<Any?> = emptyList())

/*******************************************************************************
 ** Delegate list framing to the standard list serializer.
 *******************************************************************************/
object ListAnySerializer : KSerializer<ListAny> {
   private val delegate = ListSerializer(AnyValueSerializer)
   override val descriptor = delegate.descriptor

   /*******************************************************************************
    ** Delegate framing to the declared JSON serializer without introducing wire-controlled JVM class discovery.
    *******************************************************************************/
   override fun serialize(encoder: Encoder, value: ListAny) = delegate.serialize(encoder, value.entries)

   /*******************************************************************************
    ** Reconstruct the declared wrapper through its JSON delegate instead of resolving classes from input.
    *******************************************************************************/
   override fun deserialize(decoder: Decoder) = ListAny(delegate.deserialize(decoder))
}
