/*
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

/***************************************************************************
 **
 ***************************************************************************/
@kotlinx.serialization.Serializable(with = QAppNodeTypeSerializer::class)
enum class QAppNodeType
{
   UNKNOWN,
   TABLE,
   PROCESS,
   REPORT,
   APP,
}

object QAppNodeTypeSerializer : kotlinx.serialization.KSerializer<QAppNodeType>
{
   override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("QAppNodeType", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
   override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): QAppNodeType =
      decoder.decodeString().let { value -> QAppNodeType.entries.firstOrNull { it.name == value } ?: QAppNodeType.UNKNOWN }
   override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: QAppNodeType) = encoder.encodeString(value.name)
}
