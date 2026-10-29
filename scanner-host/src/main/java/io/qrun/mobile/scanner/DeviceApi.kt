/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.os.Build
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcome
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcomeInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** The server already knows this installation, but this app never received its secret. */
class EnrollmentConflict : IllegalStateException("Registration already exists but its credentials were not received. Start a new registration and ask the administrator to approve it.")

/** The native QQQ session also authenticates device management. */
class DeviceApi(private val server: String, private val credentials: DeviceCredentials, onRequestOutcome: (RequestOutcome) -> Unit = {})
{
   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .addInterceptor(RequestOutcomeInterceptor(onRequestOutcome)).build()

   /** Check enrollment on demand; approval remains a server-side decision. */
   suspend fun status(session: String, displayName: String): String = withContext(Dispatchers.IO)
   {
      val enrolling = credentials.deviceId.isBlank()
      val request = Request.Builder().url(server + if(enrolling) "mobile/v1/devices/enroll" else "mobile/v1/devices/current")
         .header("Cookie", "sessionUUID=$session")
      credentials.headers().forEach { (name, value) -> request.header(name, value) }
      if(enrolling)
      {
         val body = JSONObject().put("installationId", credentials.installationId).put("displayName", displayName)
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("osVersion", Build.VERSION.RELEASE).put("appVersion", BuildConfig.VERSION_NAME)
         request.post(body.toString().toRequestBody("application/json".toMediaType()))
      }
      client.newCall(request.build()).execute().use { response ->
         if(enrolling && response.code == 409) throw EnrollmentConflict()
         if(!response.isSuccessful) throw IllegalStateException("Device check failed (${response.code}). Sign in again or contact your administrator.")
         val body = JSONObject(response.body?.string() ?: error("Empty device response"))
         if(enrolling) credentials.enroll(body)
         body.getString("status")
      }
   }
}
