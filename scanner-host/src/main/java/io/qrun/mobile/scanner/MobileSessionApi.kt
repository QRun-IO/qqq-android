/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.ANDROID_CLIENT
import com.kingsrook.qqq.frontend.android.core.controllers.MOBILE_SCANNERS_PATH
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcome
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcomeInterceptor
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** Session and screen traffic uses the registered extension and the existing QQQ/device identity. */
internal interface MobileSessionTransport
{
   suspend fun heartbeat(session: String, state: SessionScreen): SessionHeartbeat
   suspend fun end(session: String, reason: String)
   suspend fun screen(session: String, leaseId: String, sequence: Long, frame: ScreenFrame)
}

internal class MobileSessionApi(
   private val server: String,
   private val headers: () -> Map<String, String>,
   private val compatibility: UiApiCompatibility,
   onOutcome: (RequestOutcome) -> Unit = {},
) : MobileSessionTransport
{
   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .callTimeout(5, TimeUnit.SECONDS).addInterceptor(RequestOutcomeInterceptor(onOutcome)).build()

   override suspend fun heartbeat(session: String, state: SessionScreen): SessionHeartbeat
   {
      val locationAvailable = state.foreground && state.location.status == "AVAILABLE" && state.location.fix != null
      val locationStatus = if(!state.foreground || (state.location.status == "AVAILABLE" && !locationAvailable)) "UNAVAILABLE" else state.location.status
      val json = JSONObject().put("foreground", state.foreground).put("screenAvailable", state.available)
         .put("locationStatus", locationStatus)
      if(state.captureMode != CaptureMode.APP_CONTENT) json.put("captureMode", state.captureMode.name)
      state.location.fix?.takeIf { locationAvailable }?.let { fix ->
         json.put("location", JSONObject().put("latitude", fix.latitude).put("longitude", fix.longitude)
            .put("accuracyMeters", fix.accuracyMeters).put("capturedAt", fix.capturedAt))
      }
      mapOf("appName" to state.appName, "processName" to state.processName, "processInstanceId" to state.processInstanceId,
         "stepName" to state.stepName, "screenLabel" to state.label, "captureBlockedReason" to state.blockedReason)
         .forEach { (name, value) -> json.put(name, value ?: JSONObject.NULL) }
      return SessionHeartbeat.parse(post(session, "/sessions/heartbeat", json, 200))
   }

   override suspend fun end(session: String, reason: String)
   {
      require(reason == "SIGN_OUT" || reason == "SERVER_CHANGE")
      post(session, "/sessions/current/end", JSONObject().put("reason", reason), 200)
   }

   override suspend fun screen(session: String, leaseId: String, sequence: Long, frame: ScreenFrame)
   {
      val json = JSONObject().put("leaseId", leaseId).put("sequence", sequence).put("capturedAt", frame.capturedAt)
         .put("width", frame.width).put("height", frame.height).put("mimeType", "image/jpeg")
         .put("imageBase64", java.util.Base64.getEncoder().encodeToString(frame.jpeg))
      if(frame.captureMode != CaptureMode.APP_CONTENT) json.put("captureMode", frame.captureMode.name)
      post(session, "/sessions/current/screen", json, 204)
   }

   private suspend fun post(session: String, path: String, body: JSONObject, expected: Int): String = withContext(Dispatchers.IO)
   {
      require(session.isNotBlank())
      require(compatibility.server == server)
      compatibility.verify()
      val request = Request.Builder().url(server.trimEnd('/') + MOBILE_SCANNERS_PATH + path)
         .header("X-QQQ-Client", ANDROID_CLIENT).header("Cookie", "sessionUUID=$session")
         .header("Cache-Control", "no-store")
      headers().forEach { (name, value) -> request.header(name, value) }
      request.post(body.toString().toRequestBody("application/json".toMediaType()))
      val call = client.newCall(request.build())
      val response = suspendCancellableCoroutine<Response> { continuation ->
         continuation.invokeOnCancellation { call.cancel() }
         call.enqueue(object : Callback
         {
            override fun onFailure(call: Call, e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response)
            {
               continuation.resume(response) { _, value, _ -> value.close() }
            }
         })
      }
      response.use {
         if(!it.isSuccessful) throw UiApiException.from(it)
         check(it.code == expected) { "Unexpected mobile session response." }
         if(expected == 204) "" else
         {
            val source = it.body?.source() ?: error("Empty mobile session response.")
            source.request(16385)
            check(source.buffer.size <= 16384) { "Oversized mobile session response." }
            source.readUtf8()
         }
      }
   }
}
