/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.MOBILE_SCANNERS_PATH
import com.kingsrook.qqq.frontend.android.core.controllers.ANDROID_CLIENT
import java.net.URI
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcome

/*******************************************************************************
 ** Public presentation data; never mixed with a session or device credential.
 *******************************************************************************/
internal data class HostBrand(val displayName: String = "QQQ Mobile Scanners", val primaryColor: String = "#1565C0", val logoUrl: String? = null) {
   /*******************************************************************************
    ** Centralize public branding validation so every host applies the same safe presentation defaults.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       ** Accept bounded presentation fields independently so malformed branding safely falls back to local defaults.
       *******************************************************************************/
      fun parse(payload: String): HostBrand {
         val fields = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return HostBrand()

         /*******************************************************************************
          ** Read only JSON strings; coercing other values would bypass the presentation-field validation.
          *******************************************************************************/
         fun string(name: String): String? = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
         val name = string("displayName")?.trim()?.takeIf { it.length in 1..80 && it.none { c -> c.isISOControl() || c == '<' || c == '>' } }
         val color = string("primaryColor")?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.uppercase()
         val logo = string("logoUrl")?.takeIf { isSafeLogoUrl(it) }
         return HostBrand(name ?: HostBrand().displayName, color ?: HostBrand().primaryColor, logo)
      }
   }
}

/*******************************************************************************
 ** Allow only credential-free HTTPS image URLs with a host and no fragment.
 *******************************************************************************/
internal fun isSafeLogoUrl(value: String): Boolean = runCatching {
   val uri = URI(value)
   value.length <= 2048 && uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

/*******************************************************************************
 ** Choose the higher WCAG contrast of black/white against the supplied opaque color.
 *******************************************************************************/
internal fun useBlackOnPrimary(hex: String): Boolean {
   val channels = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) / 255.0 }
      .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
   val luminance = channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
   return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
}

/*******************************************************************************
 ** Restrict diagnostic categories so server bodies and credentials cannot become event messages.
 *******************************************************************************/
internal enum class DiagnosticCode { COMPATIBILITY_RESPONSE, COMPATIBILITY_FAILED, BRAND_LOADED, BRAND_DEFAULT, LOGO_LOADED, LOGO_UNAVAILABLE, CONNECTION_OK, CONNECTION_FAILED, NATIVE_RESPONSE, NATIVE_TRANSPORT_FAILED, DEVICE_RESPONSE, DEVICE_TRANSPORT_FAILED, SESSION_RESPONSE, SESSION_TRANSPORT_FAILED }

/*******************************************************************************
 ** Retain only a timestamp, fixed category, status and duration for operator diagnostics.
 *******************************************************************************/
internal data class DiagnosticEvent(val time: Instant, val code: DiagnosticCode, val httpStatus: Int?, val elapsedMillis: Long)

/*******************************************************************************
 ** No free-form messages: raw responses, URLs, credentials, and barcode data cannot enter this log.
 *******************************************************************************/
internal class DiagnosticLog(private val capacity: Int = 100) {
   private val events = ArrayDeque<DiagnosticEvent>()

   /*******************************************************************************
    ** Bound retained activity and synchronize writers so diagnostics cannot grow with session duration.
    *******************************************************************************/
   @Synchronized
   fun record(code: DiagnosticCode, status: Int?, elapsedMillis: Long) {
      events.addLast(DiagnosticEvent(Instant.now(), code, status, elapsedMillis.coerceAtLeast(0)))
      while (events.size > capacity) events.removeFirst()
   }

   /*******************************************************************************
    ** Export only fixed event fields; never include request URLs, response bodies or credentials.
    *******************************************************************************/
   @Synchronized
   fun text(): String = events.joinToString("\n") { "${it.time} ${it.code} HTTP=${it.httpStatus ?: "none"} ${it.elapsedMillis}ms" }

   /*******************************************************************************
    ** Return an independent ordered snapshot so callers cannot mutate the bounded diagnostic queue.
    *******************************************************************************/
   @Synchronized
   fun snapshot(): List<DiagnosticEvent> = events.toList()

   /*******************************************************************************
    ** Clear retained events under the same lock used by concurrent request observers.
    *******************************************************************************/
   @Synchronized
   fun clear() {
      events.clear()
   }

   /*******************************************************************************
    ** Classify native API outcomes without retaining the request or any authentication material.
    *******************************************************************************/
   fun nativeRequest(outcome: RequestOutcome) = record(if (outcome.httpStatus == null) DiagnosticCode.NATIVE_TRANSPORT_FAILED else DiagnosticCode.NATIVE_RESPONSE, outcome.httpStatus, outcome.elapsedMillis)

   /*******************************************************************************
    ** Classify enrollment outcomes using only transport status and elapsed time.
    *******************************************************************************/
   fun deviceRequest(outcome: RequestOutcome) = record(if (outcome.httpStatus == null) DiagnosticCode.DEVICE_TRANSPORT_FAILED else DiagnosticCode.DEVICE_RESPONSE, outcome.httpStatus, outcome.elapsedMillis)

   /*******************************************************************************
    ** Classify session telemetry outcomes without retaining transmitted metadata or pixels.
    *******************************************************************************/
   fun sessionRequest(outcome: RequestOutcome) = record(if (outcome.httpStatus == null) DiagnosticCode.SESSION_TRANSPORT_FAILED else DiagnosticCode.SESSION_RESPONSE, outcome.httpStatus, outcome.elapsedMillis)
}

/*******************************************************************************
 ** Dedicated unauthenticated client with no cookie jar, redirects, or disk cache.
 *******************************************************************************/
internal class HostRuntime(private val server: String, val diagnostics: DiagnosticLog = DiagnosticLog()) {
   val compatibility = UiApiCompatibility(server, requireMobileScanners = true) { outcome ->
      diagnostics.record(if (outcome.httpStatus == 200) DiagnosticCode.COMPATIBILITY_RESPONSE else DiagnosticCode.COMPATIBILITY_FAILED, outcome.httpStatus, outcome.elapsedMillis)
   }

   /*******************************************************************************
    ** Discover required server capabilities before any host operation can send credentials.
    *******************************************************************************/
   suspend fun verifyCompatibility() = withContext(Dispatchers.IO) { compatibility.verify() }

   private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .callTimeout(5, TimeUnit.SECONDS).build()

   /*******************************************************************************
    ** Bound presentation responses and record safe outcomes while preserving coroutine cancellation.
    *******************************************************************************/
   private suspend fun fetch(url: String, limit: Long, success: DiagnosticCode, failure: DiagnosticCode, validate: (ByteArray) -> Boolean = { true }): ByteArray? = withContext(Dispatchers.IO)
   {
      val start = System.nanoTime()
      var status: Int? = null
      try {
         if (url.startsWith(server.trimEnd('/') + MOBILE_SCANNERS_PATH)) compatibility.verify()
         client.newCall(
            Request.Builder().url(url).header("Cache-Control", "no-store")
               .apply { if (url.startsWith(server.trimEnd('/') + MOBILE_SCANNERS_PATH)) header("X-QQQ-Client", ANDROID_CLIENT) }.build()
         ).execute().use { response ->
            status = response.code
            val body = response.body
            if (!response.isSuccessful || body == null || body.contentLength() > limit) error("Unavailable")
            val source = body.source()
            source.request(limit + 1)
            if (source.buffer.size > limit) error("Oversized")
            val bytes = source.readByteArray()
            if (!validate(bytes)) error("Unexpected response")
            diagnostics.record(success, status, (System.nanoTime() - start) / 1_000_000)
            bytes
         }
      } catch (exception: CancellationException) {
         throw exception
      } catch (exception: Exception) {
         diagnostics.record(failure, status, (System.nanoTime() - start) / 1_000_000)
         null
      }
   }

   /*******************************************************************************
    ** Fetch public branding with local defaults when transport or field validation fails.
    *******************************************************************************/
   suspend fun branding(): HostBrand = fetch(server.trimEnd('/') + MOBILE_SCANNERS_PATH + "/branding", 8192, DiagnosticCode.BRAND_LOADED, DiagnosticCode.BRAND_DEFAULT)
      ?.toString(Charsets.UTF_8)?.let { HostBrand.parse(it) } ?: HostBrand()

   /*******************************************************************************
    ** Apply the public HTTPS URL policy and byte limit before decoding remote artwork.
    *******************************************************************************/
   suspend fun logo(url: String): ByteArray? = if (isSafeLogoUrl(url)) fetch(url, 1_048_576, DiagnosticCode.LOGO_LOADED, DiagnosticCode.LOGO_UNAVAILABLE) else null

   /*******************************************************************************
    ** Require the extension's explicit reachable response instead of treating any HTTP response as success.
    *******************************************************************************/
   suspend fun connected(): Boolean = fetch(server.trimEnd('/') + MOBILE_SCANNERS_PATH + "/status", 1024, DiagnosticCode.CONNECTION_OK, DiagnosticCode.CONNECTION_FAILED) {
      val fields = Json.parseToJsonElement(it.toString(Charsets.UTF_8)) as? JsonObject
      (fields?.get("status") as? JsonPrimitive)?.let { status -> status.isString && status.content == "ok" } == true
   } != null
}
