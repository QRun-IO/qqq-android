/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2024-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qqq.frontend.android.core.model.metadata

/***************************************************************************
 **
 ***************************************************************************/
@kotlinx.serialization.Serializable(with = AdornmentTypeSerializer::class)
enum class AdornmentType
{
   UNKNOWN,
   LINK,
   CHIP,
   SIZE,
   ERROR,
   RENDER_HTML,
   REVEAL,
   CODE_EDITOR,
}

object AdornmentTypeSerializer : kotlinx.serialization.KSerializer<AdornmentType>
{
   override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("AdornmentType", kotlinx.serialization.descriptors.PrimitiveKind.STRING)
   override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): AdornmentType =
      decoder.decodeString().let { value -> AdornmentType.entries.firstOrNull { it.name == value } ?: AdornmentType.UNKNOWN }
   override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: AdornmentType) = encoder.encodeString(value.name)
}
