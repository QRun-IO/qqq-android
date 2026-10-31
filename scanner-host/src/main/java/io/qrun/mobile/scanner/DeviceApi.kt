/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiException
import com.kingsrook.qqq.frontend.android.core.controllers.MOBILE_SCANNERS_PATH
import com.kingsrook.qqq.frontend.android.core.controllers.ANDROID_CLIENT
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
class DeviceApi(private val server: String, private val credentials: DeviceCredentials, onRequestOutcome: (RequestOutcome) -> Unit = {},
   private val compatibility: UiApiCompatibility = UiApiCompatibility(server, requireMobileScanners = true))
{
   var shortName: String = ""
      private set
   var displayName: String = ""
      private set
   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .addInterceptor(RequestOutcomeInterceptor(onRequestOutcome)).build()

   /** Check enrollment on demand; approval remains a server-side decision. */
   suspend fun status(session: String, displayName: String): String = withContext(Dispatchers.IO)
   {
      require(compatibility.server == server) { "Compatibility discovery must match the selected server" }
      compatibility.verify()
      val enrolling = credentials.deviceId.isBlank()
      val request = Request.Builder().url(server.trimEnd('/') + MOBILE_SCANNERS_PATH + if(enrolling) "/devices/enroll" else "/devices/current")
         .header("X-QQQ-Client", ANDROID_CLIENT)
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
         if(!response.isSuccessful) throw UiApiException.from(response)
         val body = JSONObject(response.body?.string() ?: error("Empty device response"))
         shortName = body.optString("shortName").takeUnless { it == "null" }.orEmpty()
         this@DeviceApi.displayName = body.optString("displayName").takeUnless { it == "null" }.orEmpty()
         if(enrolling) credentials.enroll(body)
         body.getString("status")
      }
   }
}
