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

/** A login response is adopted only after its initiating ViewModel accepts it. */
class DeferredSessionCookie

/***************************************************************************
 ** Class (interface?) that instructs retrofit how to make HTTP requests
 ** to a QQQ middleware server.
 ***************************************************************************/
interface QQQApiService
{
   //////////////////////////////
   // authentication endpoints //
   //////////////////////////////
   @GET(BASE_PATH + "metaData/authentication")
   suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData

   @POST(BASE_PATH + "manageSession")
   suspend fun manageSession(@Body body: ManageSessionRequest,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie): retrofit2.Response<ManageSessionResponse>

   @POST(BASE_PATH + "manageSession")
   suspend fun exchangeNativeSession(@Body body: ManageSessionRequest,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie): retrofit2.Response<ManageSessionResponse>

   @POST(BASE_PATH + "manageSession")
   suspend fun loginWithPassword(@Header("Authorization") authorization: String, @Body body: Map<String, String>,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie): retrofit2.Response<ManageSessionResponse>

   @POST(BASE_PATH + "logout")
   suspend fun logout(): ResponseBody

   @POST(BASE_PATH + "logout")
   suspend fun discardNativeSession(@Header("Cookie") cookie: String,
      @retrofit2.http.Tag cookiePolicy: DeferredSessionCookie): ResponseBody

   ///////////////////
   // qqq meta data //
   ///////////////////
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
   @GET(BASE_PATH + "metaData/process/{processName}")
   suspend fun getProcessMetaData(@Path(value = "processName") processName: String): QProcessMetaData

   @POST(BASE_PATH + "processes/{processName}/init")
   suspend fun processInit(
      @Path(value = "processName") processName: String,
      @Body body: RequestBody
   ): ProcessStepResult

   @POST(BASE_PATH + "processes/{processName}/{processUUID}/step/{stepName}")
   suspend fun processStep(
      @Path(value = "processName") processName: String,
      @Path(value = "processUUID") processUUID: String,
      @Path(value = "stepName") stepName: String,
      @Body body: RequestBody
   ): ProcessStepResult

   @GET(BASE_PATH + "processes/{processName}/{processUUID}/status/{jobUUID}")
   suspend fun processJobStatus(
      @Path(value = "processName") processName: String,
      @Path(value = "processUUID") processUUID: String,
      @Path(value = "jobUUID") jobUUID: String,
   ): ProcessStepResult

   @POST(BASE_PATH + "processes/{processName}/{processUUID}/cancel")
   suspend fun processCancel(@Path("processName") processName: String, @Path("processUUID") processUUID: String): ResponseBody


}

