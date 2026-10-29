/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
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

   suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult

   suspend fun resource(path: String): ByteArray?

   fun getURI(path: String): String
}

