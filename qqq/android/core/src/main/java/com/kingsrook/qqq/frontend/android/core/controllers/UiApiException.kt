/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response

@Serializable
data class UiApiFieldError(val field: String = "", val message: String = "", val code: String? = null)

@Serializable
private data class UiApiErrorBody(val message: String? = null, val code: String? = null,
   val fieldErrors: List<UiApiFieldError> = emptyList(), val traceId: String? = null)

/** Human-facing server errors are available to the UI, never to the diagnostic event log. */
class UiApiException(val httpStatus: Int, val code: String, message: String,
   val fieldErrors: List<UiApiFieldError> = emptyList(), val traceId: String? = null) : IOException(message)
{
   companion object
   {
      fun from(response: Response): UiApiException
      {
         val body = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<UiApiErrorBody>(response.peekBody(65536).string()) }.getOrNull()
         val fallback = when(response.code) {
            400 -> "BAD_REQUEST"; 401 -> "UNAUTHENTICATED"; 403 -> "PERMISSION_DENIED"
            404 -> "NOT_FOUND"; 409 -> "CONFLICT"; 429 -> "RATE_LIMITED"; 500 -> "INTERNAL_ERROR"; else -> "ERROR"
         }
         return UiApiException(response.code, body?.code ?: fallback,
            body?.message?.takeIf { it.isNotBlank() } ?: "The server could not complete this request (HTTP ${response.code}).",
            body?.fieldErrors ?: emptyList(), body?.traceId ?: response.header("X-QQQ-Trace-Id"))
      }
   }
}

class UiApiErrorInterceptor : Interceptor
{
   override fun intercept(chain: Interceptor.Chain): Response
   {
      val response = chain.proceed(chain.request())
      if(!response.isSuccessful) { val error = UiApiException.from(response); response.close(); throw error }
      return response
   }
}
