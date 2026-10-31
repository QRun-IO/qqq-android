/*
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

package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult

/***************************************************************************
 ** interface that defines the interaction points we have with a QQQ backend.
 ***************************************************************************/
interface QQQRepository
{
   fun setEnvironment(environment: Environment)

   fun setSessionUUID(sessionUUID: String)

   suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData

   suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse

   /** Authenticate table-backed users without retaining their password. */
   suspend fun loginWithPassword(username: String, password: String): ManageSessionResponse =
      throw UnsupportedOperationException("Password authentication is unavailable")

   /** End the server session before discarding local credentials. */
   suspend fun logout() { }

   suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance

   suspend fun getProcessMetaData(processName: String): QProcessMetaData

   suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String? = null, recordIds: String? = null, filterJSON: String? = null, stepTimeoutMillis: Int? = 3000): ProcessStepResult

   suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult

   suspend fun processCancel(processName: String, processUUID: String) { throw UnsupportedOperationException("Process cancellation is unavailable") }

   suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult


   fun getURI(path: String): String
}

