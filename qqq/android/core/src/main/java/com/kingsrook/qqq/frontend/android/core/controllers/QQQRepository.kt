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

package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult

/*******************************************************************************
 ** interface that defines the interaction points we have with a QQQ backend.
 *******************************************************************************/
interface QQQRepository {
   /*******************************************************************************
    ** Select the server environment before requests; implementations must isolate session state by server.
    *******************************************************************************/
   fun setEnvironment(environment: Environment)

   /*******************************************************************************
    ** Activate only a session accepted by the current login attempt, never an abandoned provisional response.
    *******************************************************************************/
   fun setSessionUUID(sessionUUID: String)

   /*******************************************************************************
    ** Discover the server's authentication contract without submitting credentials.
    *******************************************************************************/
   suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData

   /*******************************************************************************
    ** Return a provisional session; the initiating login must accept it before calling setSessionUUID.
    *******************************************************************************/
   suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse

   /*******************************************************************************
    ** Native browser sign-in requires explicit version/feature discovery before authorization.
    *******************************************************************************/
   suspend fun verifyNativeSessionLogin() {
      throw UnsupportedOperationException("Native browser sign-in is unavailable")
   }

   /*******************************************************************************
    ** Return the new session without activating it; the caller must validate its pending login first.
    *******************************************************************************/
   suspend fun exchangeNativeSession(body: ManageSessionRequest): ManageSessionResponse =
      throw UnsupportedOperationException("Native browser sign-in is unavailable")

   /*******************************************************************************
    ** Revoke an abandoned exchange at its original server without activating its cookie.
    *******************************************************************************/
   suspend fun discardNativeSession(server: String, session: String) {}

   /*******************************************************************************
    ** Authenticate without retaining the password or adopting the provisional response cookie.
    *******************************************************************************/
   suspend fun loginWithPassword(username: String, password: String): ManageSessionResponse =
      throw UnsupportedOperationException("Password authentication is unavailable")

   /*******************************************************************************
    ** End the server session before discarding local credentials.
    *******************************************************************************/
   suspend fun logout() {}

   /*******************************************************************************
    ** Public discovery only. Absence retains ordinary local sign-out.
    *******************************************************************************/
   suspend fun supportsNativeSessionLogout(): Boolean = false

   /*******************************************************************************
    ** Explicit optional company sign-out. Unsupported repositories still perform ordinary logout.
    *******************************************************************************/
   suspend fun logoutWithProvider(postLogoutRedirectUri: String): com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData? {
      logout()
      return null
   }

   /*******************************************************************************
    ** Load the semantic application model with explicit client identity for server compatibility decisions.
    *******************************************************************************/
   suspend fun getMetaData(frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String): QInstance

   /*******************************************************************************
    ** Resolve the server-defined process contract instead of deriving actions from its name.
    *******************************************************************************/
   suspend fun getProcessMetaData(processName: String): QProcessMetaData

   /*******************************************************************************
    ** Start server-defined process execution with explicit values and record selection; return authoritative step state.
    *******************************************************************************/
   suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String? = null, recordIds: String? = null, filterJSON: String? = null, stepTimeoutMillis: Int? = 3000): ProcessStepResult

   /*******************************************************************************
    ** Submit to the existing process instance and server-named step, preserving the caller's timeout policy.
    *******************************************************************************/
   suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult

   /*******************************************************************************
    ** Cancel the existing server process explicitly; unsupported implementations must fail visibly.
    *******************************************************************************/
   suspend fun processCancel(processName: String, processUUID: String) {
      throw UnsupportedOperationException("Process cancellation is unavailable")
   }

   /*******************************************************************************
    ** Poll the server-issued job within its process instance instead of inferring asynchronous completion.
    *******************************************************************************/
   suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult


   /*******************************************************************************
    ** Resolve a repository resource path against the currently selected server environment.
    *******************************************************************************/
   fun getURI(path: String): String
}

