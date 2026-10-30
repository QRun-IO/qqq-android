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

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.QInstance
import com.kingsrook.qqq.frontend.android.core.model.metadata.QProcessMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.BaseAuthenticationMetaData
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionResponse
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
                  sessionUUIDs[baseUrl]?.takeIf { it.isNotBlank() }?.let { request.header("Cookie", "sessionUUID=$it") }
                  additionalHeaders(baseUrl).forEach { (name, value) -> request.header(name, value) }
                  request.header("X-QQQ-Client", ANDROID_CLIENT)
                  request.header("X-QQQ-UserTimezone", TimeZone.getDefault().toZoneId().toString())
                  val response = chain.proceed(request.build())
                  response.headers("Set-Cookie").forEach { header ->
                     Cookie.parse(response.request.url, header)?.let { cookie ->
                        if(cookie.name == "sessionUUID" && cookie.matches(response.request.url))
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
      return resolveSession(qqqApiServiceMap[baseUrl]!!.manageSession(body))
   }

   /** Send credentials only to the session endpoint, never to subsequent requests. */
   override suspend fun loginWithPassword(username: String, password: String): ManageSessionResponse =
      resolveSession(qqqApiServiceMap[baseUrl]!!.loginWithPassword(Credentials.basic(username, password, Charsets.UTF_8), emptyMap()))

   /** Modern QQQ deliberately omits the UUID body field and returns an HttpOnly session cookie. */
   private fun resolveSession(response: ManageSessionResponse): ManageSessionResponse
   {
      val session = response.uuid?.takeIf { it.isNotBlank() } ?: sessionUUIDs[baseUrl]?.takeIf { it.isNotBlank() }
         ?: throw IllegalStateException("Sign-in response did not establish a session")
      return response.copy(uuid = session)
   }

   /** Preserve the cookie until the server has received the revocation request. */
   override suspend fun logout()
   {
      qqqApiServiceMap[baseUrl]!!.logout().close()
      sessionUUIDs.remove(baseUrl)
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
