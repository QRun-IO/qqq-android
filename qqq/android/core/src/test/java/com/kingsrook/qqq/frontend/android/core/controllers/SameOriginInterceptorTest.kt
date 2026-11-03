/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*******************************************************************************
 ** Native clients present the exact origin of the URL they actually request, canonicalized like a browser.
 *******************************************************************************/
class SameOriginInterceptorTest {
   /*******************************************************************************
    ** The origin comes only from scheme/host/port: a debug loopback forward keeps its device-side port
    ** (whatever the server listens on behind it), defaults are omitted, and IPv6 hosts stay bracketed.
    *******************************************************************************/
   @Test
   fun originIsTheCanonicalSchemeHostAndPortOfTheRequestUrl() {
      fun origin(url: String) = SameOriginInterceptor.httpOrigin(url.toHttpUrl())
      assertEquals("http://127.0.0.1:57832", origin("http://127.0.0.1:57832/qqq/v1/manageSession"))
      assertEquals("http://127.0.0.1", origin("http://127.0.0.1:80/"))
      assertEquals("https://qqq.example.test", origin("https://qqq.example.test/"))
      assertEquals("https://qqq.example.test", origin("https://qqq.example.test:443/path"))
      assertEquals("https://qqq.example.test:8443", origin("https://qqq.example.test:8443/"))
      assertEquals("http://qqq.example.test:443", origin("http://QQQ.Example.Test:443/"))
      assertEquals("http://[::1]:57832", origin("http://[::1]:57832/"))
      assertEquals("https://[2001:db8::1]", origin("https://[2001:db8::1]:443/"))
      assertEquals("Credentials, path, query and fragment are never part of an origin", "http://127.0.0.1:57832",
         origin("http://user:secret@127.0.0.1:57832/a/b?token=x#fragment"))
   }

   /*******************************************************************************
    ** The header the interceptor sets is final, even when an earlier layer tried to supply a different one.
    *******************************************************************************/
   @Test
   fun overridesAnyExistingOriginAndMatchesTheServerRequestHost() {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody("{}"))
         server.start()
         val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor(SameOriginInterceptor()).build()
         client.newCall(Request.Builder().url(server.url("/")).header("Origin", "https://evil.example").build()).execute().close()
         val request = server.takeRequest()
         assertEquals("http://" + request.getHeader("Host"), request.getHeader("Origin"))
         assertEquals(1, request.headers.values("Origin").size)
      }
   }

   /*******************************************************************************
    ** A redirect is still refused, so neither the cookie nor this origin reaches another server.
    *******************************************************************************/
   @Test
   fun refusedRedirectNeverReachesAnotherOrigin() {
      MockWebServer().use { first ->
         MockWebServer().use { other ->
            first.start()
            other.start()
            first.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/stolen").toString()))
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor(SameOriginInterceptor()).build()
            client.newCall(Request.Builder().url(first.url("/")).header("Cookie", "sessionUUID=synthetic").build()).execute().use { assertEquals(307, it.code) }
            assertEquals(1, first.requestCount)
            assertEquals(0, other.requestCount)
            assertNull(other.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
         }
      }
   }
}
