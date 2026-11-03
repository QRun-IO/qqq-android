/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/*******************************************************************************
 ** Native requests have no browser to supply `Origin`, yet cookie-bearing QQQ mutations must present their own origin.
 ** The value is always the canonical scheme/host/port of the actual request URL: never credentials, input or header state.
 ** Install it only on clients that talk to the selected QQQ server, never on provider, logo or download clients, and
 ** after any header-adding interceptor so that it has the final say.
 *******************************************************************************/
class SameOriginInterceptor : Interceptor {
   /*******************************************************************************
    ** Replace any existing `Origin` with the origin of the URL being requested; redirects stay refused by the clients.
    *******************************************************************************/
   override fun intercept(chain: Interceptor.Chain): Response =
      chain.proceed(chain.request().newBuilder().header("Origin", httpOrigin(chain.request().url)).build())

   companion object {
      /*******************************************************************************
       ** `scheme://host[:port]` with the scheme's default port omitted and IPv6 hosts bracketed.
       *******************************************************************************/
      fun httpOrigin(url: HttpUrl): String {
         val host = if (url.host.contains(':')) "[${url.host}]" else url.host
         val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
         return "${url.scheme}://$host$port"
      }
   }
}
