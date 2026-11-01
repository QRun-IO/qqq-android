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

import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

const val BASE_PATH = "/qqq/v1/"

/*******************************************************************************
 ** A login response is adopted only after its initiating ViewModel accepts it.
 *******************************************************************************/
class DeferredSessionCookie

/*******************************************************************************
 ** Class (interface?) that instructs retrofit how to make HTTP requests
 ** to a QQQ middleware server.
 *******************************************************************************/
interface QQQApiService {
   //////////////////////////////
   // authentication endpoints //
   //////////////////////////////
   /*******************************************************************************
    ** Discover the versioned server authentication contract before submitting credentials.
    *******************************************************************************/
   @GET(BASE_PATH + "metaData/authentication")
   suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData

   /*******************************************************************************
    ** Exchange authentication data for a provisional QQQ session without adopting a cookie implicitly.
    *******************************************************************************/
   @POST(BASE_PATH + "manageSession")
   suspend fun manageSession(
      @Body body: ManageSessionRequest,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie
   ): retrofit2.Response<ManageSessionResponse>

   /*******************************************************************************
    ** Send the native authorization exchange only to the versioned session endpoint.
    *******************************************************************************/
   @POST(BASE_PATH + "manageSession")
   suspend fun exchangeNativeSession(
      @Body body: ManageSessionRequest,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie
   ): retrofit2.Response<ManageSessionResponse>

   /*******************************************************************************
    ** Keep password credentials confined to the session endpoint rather than ordinary process requests.
    *******************************************************************************/
   @POST(BASE_PATH + "manageSession")
   suspend fun loginWithPassword(
      @Header("Authorization") authorization: String, @Body body: Map<String, String>,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie
   ): retrofit2.Response<ManageSessionResponse>

   /*******************************************************************************
    ** Revoke the supplied QQQ session before its caller discards local credentials.
    *******************************************************************************/
   @POST(BASE_PATH + "logout")
   suspend fun logout(): ResponseBody

   /*******************************************************************************
    ** Request optional company sign-out using the existing session and a registered callback.
    *******************************************************************************/
   @POST(BASE_PATH + "logout")
   @retrofit2.http.Streaming
   suspend fun logoutWithProvider(
      @Header("Cookie") cookie: String,
      @Body body: com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutRequest,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie
   ): ResponseBody

   /*******************************************************************************
    ** Revoke a captured abandoned session without installing its cookie in the active repository.
    *******************************************************************************/
   @POST(BASE_PATH + "logout")
   suspend fun discardNativeSession(
      @Header("Cookie") cookie: String,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie
   ): ResponseBody

   ///////////////////
   // qqq meta data //
   ///////////////////
   /*******************************************************************************
    ** Load semantic metadata with explicit frontend and application versions for compatibility decisions.
    *******************************************************************************/
   @GET(BASE_PATH + "metaData")
   suspend fun getMetaData(
      @Query("frontendName") frontendName: String,
      @Query("frontendVersion") frontendVersion: String,
      @Query("applicationName") applicationName: String,
      @Query("applicationVersion") applicationVersion: String,
   ): QInstance

   ///////////////
   // processes //
   ///////////////
   /*******************************************************************************
    ** Read the server-defined process contract rather than deriving behavior from its name.
    *******************************************************************************/
   @GET(BASE_PATH + "metaData/process/{processName}")
   suspend fun getProcessMetaData(@Path(value = "processName") processName: String): QProcessMetaData

   /*******************************************************************************
    ** Start process execution with explicit record selection and values, returning authoritative server step state.
    *******************************************************************************/
   @POST(BASE_PATH + "processes/{processName}/init")
   suspend fun processInit(
      @Path(value = "processName") processName: String,
      @Body body: RequestBody
   ): ProcessStepResult

   /*******************************************************************************
    ** Submit values to the identified server process instance and step.
    *******************************************************************************/
   @POST(BASE_PATH + "processes/{processName}/{processUUID}/step/{stepName}")
   suspend fun processStep(
      @Path(value = "processName") processName: String,
      @Path(value = "processUUID") processUUID: String,
      @Path(value = "stepName") stepName: String,
      @Body body: RequestBody
   ): ProcessStepResult

   /*******************************************************************************
    ** Poll the identified server job within its owning process instance.
    *******************************************************************************/
   @GET(BASE_PATH + "processes/{processName}/{processUUID}/status/{jobUUID}")
   suspend fun processJobStatus(
      @Path(value = "processName") processName: String,
      @Path(value = "processUUID") processUUID: String,
      @Path(value = "jobUUID") jobUUID: String,
   ): ProcessStepResult

   /*******************************************************************************
    ** Cancel the identified server process explicitly instead of dropping only client navigation.
    *******************************************************************************/
   @POST(BASE_PATH + "processes/{processName}/{processUUID}/cancel")
   suspend fun processCancel(@Path("processName") processName: String, @Path("processUUID") processUUID: String): ResponseBody


}

