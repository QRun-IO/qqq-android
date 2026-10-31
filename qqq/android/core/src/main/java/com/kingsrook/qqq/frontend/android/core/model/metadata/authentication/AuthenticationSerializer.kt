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

package com.kingsrook.qqq.frontend.android.core.model.metadata.authentication

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/***************************************************************************
 **
 ***************************************************************************/
object AuthenticationSerializer : JsonContentPolymorphicSerializer<BaseAuthenticationMetaData>(BaseAuthenticationMetaData::class)
{
   /***************************************************************************
    **
    ***************************************************************************/
   override fun selectDeserializer(element: JsonElement) = when (element.jsonObject["type"]?.jsonPrimitive?.content)
   {
      Auth0AuthenticationMetaData.TYPE -> Auth0AuthenticationMetaData.serializer()
      OAuth2AuthenticationMetaData.TYPE -> OAuth2AuthenticationMetaData.serializer()
      FullyAnonymousAuthenticationMetaData.TYPE -> FullyAnonymousAuthenticationMetaData.serializer()
      TableBasedAuthenticationMetaData.TYPE, "tableBased" -> TableBasedAuthenticationMetaData.serializer()
      else -> UnsupportedAuthenticationMetaData.serializer()
   }
}

@kotlinx.serialization.Serializable
data class UnsupportedAuthenticationMetaData(override val type: String, override val name: String = "Unsupported authentication") : BaseAuthenticationMetaData
