/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.controllers

import com.kingsrook.qqq.frontend.android.core.model.Environment
import com.kingsrook.qqq.frontend.android.core.model.metadata.authentication.ManageSessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

/*******************************************************************************
 **
 *******************************************************************************/
class DeferredAuthenticationTest {
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun passwordCookieAfterSwitchIsNeverAdopted() = delayedResponse(password = true, cookie = true)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun passwordBodyAfterSwitchIsNeverAdopted() = delayedResponse(password = true, cookie = false)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun auth0CookieAfterSwitchIsNeverAdopted() = delayedResponse(password = false, cookie = true)

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   fun auth0BodyAfterSwitchIsNeverAdopted() = delayedResponse(password = false, cookie = false)

   /*******************************************************************************
    **
    *******************************************************************************/
   private fun delayedResponse(password: Boolean, cookie: Boolean) = runBlocking()
   {
      MockWebServer().use { first ->
         MockWebServer().use { second ->
            val origin = first.url("/").toString()
            val otherOrigin = second.url("/").toString()
            first.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            val response = MockResponse().setBody(if (cookie) "{}" else """{"uuid":"abandoned-session"}""")
               .setBodyDelay(500, TimeUnit.MILLISECONDS)
            if (cookie) response.addHeader("Set-Cookie", "sessionUUID=abandoned-session; HttpOnly; Path=/")
            first.enqueue(response)
            first.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "sessionUUID=; Path=/; Max-Age=0"))
            first.enqueue(MockResponse().setBody("{}"))
            second.enqueue(MockResponse().setBody(VERSION_FIXTURE))
            second.enqueue(MockResponse().setBody("{}"))
            val repository = NetworkQQQRepository(origin)
            repository.setEnvironment(Environment("first", origin))
            val pending = async(Dispatchers.IO) {
               if (password) repository.loginWithPassword("operator", "synthetic-password")
               else repository.manageSession(ManageSessionRequest(accessToken = "synthetic-provider-token"))
            }
            withContext(Dispatchers.IO) {
               assertEquals("/qqq/v1/version", first.takeRequest(5, TimeUnit.SECONDS)?.path)
               val login = requireNotNull(first.takeRequest(5, TimeUnit.SECONDS))
               assertEquals("/qqq/v1/manageSession", login.path)
               assertNull(login.getHeader("Cookie"))
            }
            repository.setEnvironment(Environment("second", otherOrigin))
            repository.setSessionUUID("current-second-session")
            assertEquals("abandoned-session", pending.await().uuid)
            repository.discardNativeSession(origin, "abandoned-session")
            val discard = requireNotNull(first.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/qqq/v1/logout", discard.path)
            assertEquals("sessionUUID=abandoned-session", discard.getHeader("Cookie"))
            assertNull(discard.getHeader("Authorization"))
            repository.getMetaData("test", "1", "test", "1")
            assertEquals("/qqq/v1/version", second.takeRequest(5, TimeUnit.SECONDS)?.path)
            val current = requireNotNull(second.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("sessionUUID=current-second-session", current.getHeader("Cookie"))
            assertNull(current.getHeader("Authorization"))
            assertFalse(current.body.readUtf8().contains("synthetic-provider-token"))
            repository.setEnvironment(Environment("first", origin))
            repository.getMetaData("test", "1", "test", "1")
            assertNull(
               "Returning to the original server must not resurrect the abandoned cookie",
               requireNotNull(first.takeRequest(5, TimeUnit.SECONDS)).getHeader("Cookie")
            )
         }
      }
   }
}
