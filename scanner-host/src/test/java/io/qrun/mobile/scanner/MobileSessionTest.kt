/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import androidx.compose.runtime.mutableStateOf
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiCompatibility
import com.kingsrook.qqq.frontend.android.core.controllers.UiApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSessionTest
{
   private fun ready() = SessionScreen(true, true, true, true, false, true, false, 1, 1,
      "warehouse", "count", "process-id", "scan", "Scan items")
   private val lease = ScreenLease("lease-one", 30000, 1000, 640, 1280, 262144, 0)
   private val frame = ScreenFrame(byteArrayOf(1, 2, 3), 100, 200, "2026-10-30T12:00:00Z")

   @Test fun captureIsBlockedOutsideTheApprovedVisibleApplication()
   {
      val state = ready()
      assertTrue(state.available)
      listOf(state.copy(authenticated = false), state.copy(approved = false), state.copy(foreground = false),
         state.copy(focused = false), state.copy(hasPassword = true), state.copy(ready = false), state.copy(paused = true))
         .forEach { assertFalse(it.available); assertNotNull(it.blockedReason) }
   }

   @Test fun frameTicketRejectsStateSessionAndLeaseRaces()
   {
      val state = ready()
      val ticket = ScreenTicket(8, state, "lease-one")
      assertTrue(ticket.valid(8, state, "lease-one", 100, 200))
      assertFalse(ticket.valid(9, state, "lease-one", 100, 200))
      assertFalse(ticket.valid(8, state.copy(navigationRevision = 2), "lease-one", 100, 200))
      assertFalse(ticket.valid(8, state.copy(hasPassword = true), "lease-one", 100, 200))
      assertFalse(ticket.valid(8, state, "lease-two", 100, 200))
      assertFalse(ticket.valid(8, state, "lease-one", 200, 200))
   }

   @Test fun leaseDurationUsesServerTimeAndRespectsLocalLimits()
   {
      val reply = SessionHeartbeat.parse(heartbeatJson("2099-01-01T00:00:00Z", "2099-01-01T00:00:25Z"))
      assertEquals(25000L, reply.lease!!.durationMillis)
      assertEquals(640, reply.lease.maxWidth)
      assertEquals(1280, reply.lease.maxHeight)
      assertEquals(262144, reply.lease.maxBytes)
      assertEquals(0L, SessionHeartbeat.parse(heartbeatJson("2099-01-01T00:01:00Z", "2099-01-01T00:00:25Z")).lease!!.durationMillis)
   }

   @Test fun absenceOfViewerLeaseNeverInvokesCapture() = runTest()
   {
      val transport = FakeTransport(null)
      var captures = 0
      val controller = MobileSessionController("session-a", transport, { ready() }, { captures++; frame }, {}, { testScheduler.currentTime })
      val job = backgroundScope.launch { controller.run() }
      runCurrent(); advanceTimeBy(10000); runCurrent()
      assertTrue(transport.states.size >= 2)
      assertEquals(0, captures)
      controller.end("SIGN_OUT")
      assertEquals(listOf("session-a:SIGN_OUT"), transport.ends)
      job.cancel()
   }

   @Test fun screenChangingToPasswordDuringCaptureDiscardsTheFrame() = runTest()
   {
      val state = mutableStateOf(ready())
      val transport = FakeTransport(lease)
      val captured = CompletableDeferred<ScreenFrame>()
      val controller = MobileSessionController("session-a", transport, { state.value }, { captured.await() }, {}, { testScheduler.currentTime })
      backgroundScope.launch { controller.run() }
      runCurrent()
      state.value = state.value.copy(hasPassword = true)
      captured.complete(frame)
      runCurrent()
      assertTrue(transport.uploads.isEmpty())
      assertEquals("PASSWORD_SCREEN", transport.states.last().blockedReason)
      controller.end("SIGN_OUT")
   }

   @Test fun endingSessionCancelsAnInFlightCaptureAndStopsUploads() = runTest()
   {
      val transport = FakeTransport(lease)
      val captured = CompletableDeferred<ScreenFrame>()
      val controller = MobileSessionController("session-a", transport, { ready() }, { captured.await() }, {}, { testScheduler.currentTime })
      backgroundScope.launch { controller.run() }
      runCurrent()
      controller.end("SERVER_CHANGE")
      captured.complete(frame)
      runCurrent()
      assertTrue(transport.uploads.isEmpty())
      assertFalse(controller.viewing)
      assertEquals(listOf("session-a:SERVER_CHANGE"), transport.ends)
   }

   @Test fun expiredLeaseDuringCaptureDiscardsTheFrame() = runTest()
   {
      val transport = FakeTransport(lease.copy(durationMillis = 1000))
      val captured = CompletableDeferred<ScreenFrame>()
      val controller = MobileSessionController("session-a", transport, { ready() }, { captured.await() }, {}, { testScheduler.currentTime })
      backgroundScope.launch { controller.run() }
      runCurrent(); advanceTimeBy(1500)
      captured.complete(frame); runCurrent()
      assertTrue(transport.uploads.isEmpty())
      controller.end("SIGN_OUT")
   }

   @Test fun serverEndedSessionRequestsLogoutOnceAndFreshLoginHasSeparateIdentity() = runTest()
   {
      val transport = FakeTransport(lease)
      transport.rejectSession = "ended-session"
      var logouts = 0
      val ended = MobileSessionController("ended-session", transport, { ready() }, { frame }, { logouts++ }, { testScheduler.currentTime })
      backgroundScope.launch { ended.run() }
      runCurrent(); advanceTimeBy(10000); runCurrent()
      assertEquals(1, logouts)
      assertTrue(transport.uploads.isEmpty())
      val fresh = MobileSessionController("fresh-session", transport, { ready() }, { frame }, { logouts++ }, { testScheduler.currentTime })
      backgroundScope.launch { fresh.run() }; runCurrent()
      assertEquals("fresh-session", transport.uploads.single().first)
      assertEquals(1, logouts)
      fresh.end("SIGN_OUT")
   }

   @Test fun operatorPauseClearsAvailabilityAndRejectsPendingFrame() = runTest()
   {
      val transport = FakeTransport(lease)
      val captured = CompletableDeferred<ScreenFrame>()
      val controller = MobileSessionController("session-a", transport, { ready() }, { captured.await() }, {}, { testScheduler.currentTime })
      backgroundScope.launch { controller.run() }; runCurrent()
      controller.pauseViewing(); captured.complete(frame); runCurrent()
      assertTrue(transport.uploads.isEmpty())
      assertEquals("OPERATOR_PAUSED", transport.states.last().blockedReason)
      controller.end("SIGN_OUT")
   }

   @Test fun networkPayloadUsesSemanticRoutesAndCarriesOnlyScreenMetadata() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION))
         server.enqueue(MockResponse().setBody(heartbeatJson("2026-10-30T00:00:00Z", "2026-10-30T00:00:30Z")))
         server.enqueue(MockResponse().setResponseCode(204))
         server.enqueue(MockResponse().setBody("{}"))
         val origin = server.url("/").toString()
         val api = MobileSessionApi(origin, { mapOf("X-QQQ-Device-Token" to "synthetic-token") }, UiApiCompatibility(origin, true))
         api.heartbeat("synthetic-session", ready())
         api.screen("synthetic-session", "lease-one", 4, frame)
         api.end("synthetic-session", "SIGN_OUT")
         val discovery = server.takeRequest()
         assertEquals("/qqq/v1/version", discovery.path)
         assertNull(discovery.getHeader("Cookie"))
         val heartbeat = server.takeRequest()
         assertEquals("/qqq/v1/qbit/mobile-scanners/sessions/heartbeat", heartbeat.path)
         assertEquals("android/qqq-mobile", heartbeat.getHeader("X-QQQ-Client"))
         val body = JSONObject(heartbeat.body.readUtf8())
         assertTrue(body.getBoolean("screenAvailable"))
         assertEquals("count", body.getString("processName"))
         listOf("user", "deviceId", "barcode", "sessionUUID", "password", "values").forEach { assertFalse(body.has(it)) }
         val screen = server.takeRequest()
         assertEquals("/qqq/v1/qbit/mobile-scanners/sessions/current/screen", screen.path)
         assertEquals(4, JSONObject(screen.body.readUtf8()).getLong("sequence"))
         assertEquals("/qqq/v1/qbit/mobile-scanners/sessions/current/end", server.takeRequest().path)
      }
   }

   @Test fun locationIsReportedOnlyWithAnAvailableForegroundFix() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody(VERSION))
         repeat(3) { server.enqueue(MockResponse().setBody(heartbeatJson("2026-10-30T00:00:00Z", "2026-10-30T00:00:30Z"))) }
         val api = MobileSessionApi(server.url("/").toString(), { emptyMap() }, UiApiCompatibility(server.url("/").toString(), true))
         val located = ready().copy(location = LocationReport("AVAILABLE", LocationFix(38.5, -90.5, 12.0, "2026-10-30T00:00:00Z")))
         api.heartbeat("session", located)
         api.heartbeat("session", located.copy(foreground = false))
         api.heartbeat("session", located.copy(location = LocationReport("PERMISSION_DENIED")))
         server.takeRequest()
         val foreground = JSONObject(server.takeRequest().body.readUtf8())
         assertEquals("AVAILABLE", foreground.getString("locationStatus"))
         assertEquals(38.5, foreground.getJSONObject("location").getDouble("latitude"), 0.0)
         val background = JSONObject(server.takeRequest().body.readUtf8())
         assertEquals("UNAVAILABLE", background.getString("locationStatus"))
         assertFalse(background.has("location"))
         val denied = JSONObject(server.takeRequest().body.readUtf8())
         assertEquals("PERMISSION_DENIED", denied.getString("locationStatus"))
         assertFalse(denied.has("location"))
         assertTrue(denied.getBoolean("screenAvailable"))
      }
   }

   private inner class FakeTransport(private val lease: ScreenLease?) : MobileSessionTransport
   {
      val states = mutableListOf<SessionScreen>()
      val uploads = mutableListOf<Pair<String, Long>>()
      val ends = mutableListOf<String>()
      var rejectSession: String? = null
      override suspend fun heartbeat(session: String, state: SessionScreen): SessionHeartbeat
      {
         if(session == rejectSession) throw UiApiException(401, "SESSION_ENDED", "Ended")
         states += state
         return SessionHeartbeat("public-session-id", 5000, lease)
      }
      override suspend fun end(session: String, reason: String) { ends += "$session:$reason" }
      override suspend fun screen(session: String, leaseId: String, sequence: Long, frame: ScreenFrame) { uploads += session to sequence }
   }

   private fun heartbeatJson(server: String, expires: String) = """{"sessionId":"public-id","status":"ACTIVE","serverTime":"$server","heartbeatIntervalMillis":5000,"screenMirror":{"leaseId":"lease-one","expiresAt":"$expires","frameIntervalMillis":1000,"maxWidth":640,"maxHeight":1280,"maxBytes":262144,"nextSequence":0}}"""
}

private const val VERSION = """{"apiVersion":"1.8.0","features":["version","metaData","processes","processCancel","extensions","widgets"],"clientChannels":["android"],"extensions":[{"name":"mobile-scanners","version":"1.1.0","pathPrefix":"/qbit/mobile-scanners"}]}"""
