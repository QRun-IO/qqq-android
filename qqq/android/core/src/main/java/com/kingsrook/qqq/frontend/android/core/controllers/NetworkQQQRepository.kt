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

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogout
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutResponse
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.NativeLogoutData
import com.kingsrook.qqq.frontend.android.core.model.processes.ProcessStepResult
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap


/***************************************************************************
 **
 ***************************************************************************/
class NetworkQQQRepository(private var baseUrl: String, @Volatile var requestObserver: (RequestOutcome) -> Unit = {}, private val compatibility: UiApiCompatibility? = null, private val additionalHeaders: (String) -> Map<String, String> = { emptyMap() }) : QQQRepository
{
   private val sessionUUIDs = ConcurrentHashMap<String, String>()
   private val retroJson = Json { ignoreUnknownKeys = true }
   private val compatibilityByServer = ConcurrentHashMap<String, UiApiCompatibility>()

   private val qqqApiServiceMap: MutableMap<String, QQQApiService> = mutableMapOf()

   /***************************************************************************
    **
    ***************************************************************************/
   override fun setEnvironment(environment: Environment)
   {
      baseUrl = environment.baseUrl

      if(qqqApiServiceMap[baseUrl] == null)
      {
         val thisRetrofit = buildRetrofit(baseUrl)
         val thisQQQApiService = thisRetrofit.create(QQQApiService::class.java)
         qqqApiServiceMap[baseUrl] = thisQQQApiService
      }

      // retrofit = buildRetrofit()
      // retrofit.create(QQQApiService::class.java)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun buildRetrofit(baseUrl: String): Retrofit
   {
      return Retrofit.Builder()
         .addConverterFactory(retroJson.asConverterFactory("application/json".toMediaType()))
         .client(
            OkHttpClient.Builder()
               .followRedirects(false)
               .followSslRedirects(false)
               .addInterceptor { chain ->
                  compatibilityByServer.getOrPut(baseUrl) {
                     compatibility?.takeIf { it.server == baseUrl } ?: UiApiCompatibility(baseUrl, observer = { requestObserver(it) })
                  }.verify()
                  chain.proceed(chain.request())
               }
               .addInterceptor(UiApiErrorInterceptor())
               .addInterceptor(RequestOutcomeInterceptor { requestObserver(it) })
               .addInterceptor { chain ->
                  val request = chain.request().newBuilder()
                  val deferredSession = chain.request().tag(DeferredSessionCookie::class.java) != null
                  if(!deferredSession) sessionUUIDs[baseUrl]?.takeIf { it.isNotBlank() }?.let { request.header("Cookie", "sessionUUID=$it") }
                  additionalHeaders(baseUrl).forEach { (name, value) -> request.header(name, value) }
                  request.header("X-QQQ-Client", ANDROID_CLIENT)
                  request.header("X-QQQ-UserTimezone", TimeZone.getDefault().toZoneId().toString())
                  val response = chain.proceed(request.build())
                  response.headers("Set-Cookie").forEach { header ->
                     Cookie.parse(response.request.url, header)?.let { cookie ->
                        if(!deferredSession && cookie.name == "sessionUUID" && cookie.matches(response.request.url))
                        {
                           sessionUUIDs[baseUrl] = cookie.value
                        }
                     }
                  }
                  response
               }
               .build()
         )
         .baseUrl(baseUrl)
         .build()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override fun setSessionUUID(sessionUUID: String)
   {
      sessionUUIDs[baseUrl] = sessionUUID
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getAuthenticationMetaData(): BaseAuthenticationMetaData
   {
      // return qqqApiService.getAuthenticationMetaData()
      return qqqApiServiceMap[baseUrl]!!.getAuthenticationMetaData()
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun manageSession(body: ManageSessionRequest): ManageSessionResponse
   {
      val service = requireNotNull(qqqApiServiceMap[baseUrl])
      return resolveSession(service.manageSession(body, DeferredSessionCookie()))
   }

   override suspend fun verifyNativeSessionLogin() = verifyNativeSessionLoginAt(baseUrl)

   private suspend fun verifyNativeSessionLoginAt(server: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)
   {
      val version = compatibilityByServer.getOrPut(server) {
         compatibility?.takeIf { it.server == server } ?: UiApiCompatibility(server, observer = { requestObserver(it) })
      }.verify()
      UiApiCompatibility.validateNativeSessionLogin(version)
   }

   override suspend fun exchangeNativeSession(body: ManageSessionRequest): ManageSessionResponse
   {
      require(body.authenticationFlow == com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.OAuth2AuthenticationMetaData.SESSION_FLOW &&
         com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.isValidExpectedNonce(body.expectedNonce)) {
         "Native sign-in requires its original nonce"
      }
      val server = baseUrl
      val service = requireNotNull(qqqApiServiceMap[server])
      verifyNativeSessionLoginAt(server)
      val response = service.exchangeNativeSession(body, DeferredSessionCookie())
      return resolveSession(response)
   }

   /** Login cookies remain provisional until the initiating ViewModel accepts the response. */
   private fun resolveSession(response: retrofit2.Response<ManageSessionResponse>): ManageSessionResponse
   {
      if(!response.isSuccessful) throw retrofit2.HttpException(response)
      val result = response.body() ?: throw IllegalStateException("Sign-in response did not establish a session")
      val requestUrl = response.raw().request.url
      val cookie = response.headers().values("Set-Cookie").mapNotNull { Cookie.parse(requestUrl, it) }
         .lastOrNull { it.name == "sessionUUID" && it.matches(requestUrl) }?.value?.takeIf { it.isNotBlank() }
      return result.copy(uuid = cookie ?: result.uuid?.takeIf { it.isNotBlank() }
         ?: throw IllegalStateException("Sign-in response did not establish a session"))
   }

   override suspend fun discardNativeSession(server: String, session: String)
   {
      val service = qqqApiServiceMap[server] ?: return
      service.discardNativeSession("sessionUUID=$session", DeferredSessionCookie()).close()
   }

   /** Send credentials only to the session endpoint, never to subsequent requests. */
   override suspend fun loginWithPassword(username: String, password: String): ManageSessionResponse
   {
      val service = requireNotNull(qqqApiServiceMap[baseUrl])
      return resolveSession(service.loginWithPassword(Credentials.basic(username, password, Charsets.UTF_8), emptyMap(), DeferredSessionCookie()))
   }

   /** Preserve the cookie until the server has received the revocation request. */
   override suspend fun logout()
   {
      val server = baseUrl
      val session = sessionUUIDs[server]
      try { if(!session.isNullOrBlank()) discardNativeSession(server, session) }
      finally { if(session != null) sessionUUIDs.remove(server, session) }
   }

   /** Capture the original origin/cookie; neither a response cookie nor a server switch can adopt a session. */
   override suspend fun logoutWithProvider(postLogoutRedirectUri: String): NativeLogoutData?
   {
      val server = baseUrl
      val session = sessionUUIDs[server] ?: return null
      try
      {
         val supported = supportsNativeSessionLogoutAt(server)
         if(!supported)
         {
            discardNativeSession(server, session)
            return null
         }
         return requireNotNull(qqqApiServiceMap[server]).logoutWithProvider("sessionUUID=$session",
            NativeLogoutRequest(NativeLogout(postLogoutRedirectUri)), DeferredSessionCookie()).use { response ->
            // Parse privately: serialization errors may include input fragments, so never propagate them.
            val source = response.source()
            source.request(65537)
            if(source.buffer.size > 65536) return@use null
            try { retroJson.decodeFromString<NativeLogoutResponse>(source.readUtf8()).nativeLogoutData }
            catch(exception: Exception) { null }
         }
      }
      catch(exception: UiApiException) { throw java.io.IOException("Company sign-out request failed") }
      finally { sessionUUIDs.remove(server, session) }
   }

   override suspend fun supportsNativeSessionLogout(): Boolean = supportsNativeSessionLogoutAt(baseUrl)

   private suspend fun supportsNativeSessionLogoutAt(server: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)
   {
      try
      {
         val version = compatibilityByServer.getOrPut(server) {
            compatibility?.takeIf { it.server == server } ?: UiApiCompatibility(server, observer = { requestObserver(it) })
         }.verify()
         UiApiCompatibility.validateNativeSessionLogout(version)
         true
      }
      catch(exception: UiApiCompatibilityException) { false }
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getMetaData(
      frontendName: String, frontendVersion: String, applicationName: String, applicationVersion: String
   ): QInstance
   {
      return qqqApiServiceMap[baseUrl]!!.getMetaData(frontendName, frontendVersion, applicationName, applicationVersion)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun getProcessMetaData(processName: String): QProcessMetaData
   {
      return qqqApiServiceMap[baseUrl]!!.getProcessMetaData(processName)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processInit(processName: String, values: Map<String, Any?>, recordsParam: String?, recordIds: String?, filterJSON: String?, stepTimeoutMillis: Int?): ProcessStepResult
   {
      return qqqApiServiceMap[baseUrl]!!.processInit(processName, buildProcessInitOrStepRequestBodyV1(values, recordsParam, recordIds, filterJSON, stepTimeoutMillis))
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processStep(processName: String, processUUID: String, stepName: String, values: Map<String, Any?>, stepTimeoutMillis: Int?): ProcessStepResult
   {
      return qqqApiServiceMap[baseUrl]!!.processStep(processName, processUUID, stepName, buildProcessInitOrStepRequestBodyV1(values, stepTimeoutMillis = stepTimeoutMillis))
   }

   /***************************************************************************
    **
    ***************************************************************************/
   override suspend fun processCancel(processName: String, processUUID: String)
   {
      qqqApiServiceMap[baseUrl]!!.processCancel(processName, processUUID).close()
   }

   override suspend fun processJobStatus(processName: String, processUUID: String, jobUUID: String): ProcessStepResult
   {
      return qqqApiServiceMap[baseUrl]!!.processJobStatus(processName, processUUID, jobUUID)
   }

   /***************************************************************************
    **
    ***************************************************************************/
   private fun buildProcessInitOrStepRequestBodyV1(values: Map<String, Any?>, recordsParam: String? = null, recordIds: String? = null, filterJSON: String? = null, stepTimeoutMillis: Int? = null): RequestBody
   {
      val bodyBuilder = MultipartBody.Builder()
         .setType(MultipartBody.FORM)

      if(!values.isEmpty())
      {
         val valuesJson = JSONObject()
         for(entry in values.entries)
         {
            valuesJson.put(entry.key, entry.value ?: JSONObject.NULL)
         }
         bodyBuilder.addFormDataPart("values", valuesJson.toString())
      }

      if(recordsParam != null)
      {
         bodyBuilder.addFormDataPart("recordsParam", recordsParam)
      }

      if(recordIds != null)
      {
         bodyBuilder.addFormDataPart("recordIds", recordIds)
      }

      if(filterJSON != null)
      {
         bodyBuilder.addFormDataPart("filterJSON", filterJSON)
      }

      if(stepTimeoutMillis != null)
      {
         bodyBuilder.addFormDataPart("stepTimeoutMillis", stepTimeoutMillis.toString())
      }

      val requestBody: RequestBody = bodyBuilder.build()
      return requestBody
   }

   /***************************************************************************
    **
    ***************************************************************************/


   /***************************************************************************
    **
    ***************************************************************************/
   override fun getURI(path: String): String
   {
      val uri = java.net.URI(baseUrl).resolve(path)
      require(uri.scheme in setOf("https", "http") && uri.userInfo == null && uri.host != null) { "Unsupported media URL" }
      return uri.toASCIIString()
   }

   /***************************************************************************
    **
    ***************************************************************************/
}
