/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/*******************************************************************************
 ** Deliberately excludes URLs, headers, bodies, and exception messages. Null status means transport failure.
 *******************************************************************************/
data class RequestOutcome(val httpStatus: Int?, val elapsedMillis: Long)

/*******************************************************************************
 ** Optional diagnostics must never change request behavior, even if an observer throws.
 *******************************************************************************/
class RequestOutcomeInterceptor(private val observer: (RequestOutcome) -> Unit) : Interceptor {
   /*******************************************************************************
    ** Observe status and monotonic duration without retaining sensitive request data or changing HTTP behavior.
    *******************************************************************************/
   override fun intercept(chain: Interceptor.Chain): Response {
      val started = System.nanoTime()

      /*******************************************************************************
       ** Isolate observer failures so optional diagnostics cannot fail the original request.
       *******************************************************************************/
      fun report(status: Int?) {
         runCatching { observer(RequestOutcome(status, (System.nanoTime() - started) / 1_000_000)) }
      }
      try {
         return chain.proceed(chain.request()).also { report(it.code) }
      } catch (exception: IOException) {
         report(null)
         throw exception
      }
   }
}
