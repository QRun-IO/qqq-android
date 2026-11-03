/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
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

package com.kingsrook.qqq.frontend.android.core.model.metadata

/*******************************************************************************
 *******************************************************************************/
@kotlinx.serialization.Serializable(with = QFieldTypeSerializer::class)
enum class QFieldType {
   UNKNOWN,
   BOOLEAN,
   STRING,
   INTEGER,
   LONG,
   DECIMAL,
   DATE,
   TIME,
   DATE_TIME,
   TEXT,
   HTML,
   PASSWORD,
   BLOB,
}

/*******************************************************************************
 ** Keep unknown server enum values representable so additive metadata fails safely at the UI boundary.
 *******************************************************************************/
object QFieldTypeSerializer : kotlinx.serialization.KSerializer<QFieldType> {
   override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("QFieldType", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

   /*******************************************************************************
    ** Map known wire names explicitly and preserve unsupported values through the enum's UNKNOWN member.
    *******************************************************************************/
   override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): QFieldType =
      decoder.decodeString().let { value -> QFieldType.entries.firstOrNull { it.name == value } ?: QFieldType.UNKNOWN }

   /*******************************************************************************
    ** Write the stable enum wire name rather than a JVM implementation detail.
    *******************************************************************************/
   override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: QFieldType) = encoder.encodeString(value.name)
}
