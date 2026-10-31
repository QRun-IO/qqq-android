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

package com.kingsrook.qqq.frontend.android.mobileapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kingsrook.qqq.frontend.android.core.model.Environment
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


val SESSION_UUID_KEY = stringPreferencesKey("session_uuid")
val SESSION_USER_FULL_NAME_KEY = stringPreferencesKey("session_user_full_name")

val ENVIRONMENT_JSON_KEY = stringPreferencesKey("environment_json")

/***************************************************************************
 **
 ***************************************************************************/
class SettingsRepository(
   private val dataStore: DataStore<Preferences>,
)
{

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun getSessionUUID(): String
   {
      return (dataStore.data.first()[SESSION_UUID_KEY] ?: "")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun setSessionUUID(sessionUUID: String)
   {
      dataStore.edit { preferences ->
         preferences[SESSION_UUID_KEY] = sessionUUID
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun getSessionUserFullName(): String
   {
      return (dataStore.data.first()[SESSION_USER_FULL_NAME_KEY] ?: "")
   }

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun setSessionUserFullName(value: String)
   {
      dataStore.edit { preferences ->
         preferences[SESSION_USER_FULL_NAME_KEY] = value
      }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun getEnvironment(): Environment?
   {
      val environmentJsonString = (dataStore.data.first()[ENVIRONMENT_JSON_KEY] ?: "")
      if(environmentJsonString == "")
      {
         return null
      }

      return Json.decodeFromString(environmentJsonString)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   suspend fun setEnvironment(value: Environment)
   {
      dataStore.edit { preferences ->
         preferences[ENVIRONMENT_JSON_KEY] = Json.encodeToString(value)
      }
   }

}