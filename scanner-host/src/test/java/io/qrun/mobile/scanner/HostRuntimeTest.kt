package io.qrun.mobile.scanner

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import com.kingsrook.qqq.frontend.android.core.controllers.RequestOutcome

class HostRuntimeTest
{
   @Test
   fun requestDiagnosticsDistinguishNativeAndDeviceOutcomes()
   {
      val log = DiagnosticLog()
      log.nativeRequest(RequestOutcome(401, 10))
      log.nativeRequest(RequestOutcome(null, 20))
      log.deviceRequest(RequestOutcome(403, 30))
      log.deviceRequest(RequestOutcome(null, 40))
      assertTrue(log.text().contains("NATIVE_RESPONSE HTTP=401 10ms"))
      assertTrue(log.text().contains("NATIVE_TRANSPORT_FAILED HTTP=none 20ms"))
      assertTrue(log.text().contains("DEVICE_RESPONSE HTTP=403 30ms"))
      assertTrue(log.text().contains("DEVICE_TRANSPORT_FAILED HTTP=none 40ms"))
   }

   @Test
   fun malformedBrandFallsBackAndUnsafeLogoIsDiscarded()
   {
      for(payload in listOf("not json", "[]", "null", "{\"displayName\":12,\"primaryColor\":\"red\"}"))
         assertEquals(HostBrand(), HostBrand.parse(payload))
      assertEquals(HostBrand("Acme", "#AB12FF"), HostBrand.parse("{\"displayName\":\" Acme \",\"primaryColor\":\"#ab12ff\",\"logoUrl\":\"http://example.com/logo.png\"}"))
      assertEquals(HostBrand().displayName, HostBrand.parse("{\"displayName\":\"<script>\"}").displayName)
      listOf("file:///secret", "content://secret", "https://user:pass@example.com/logo", "https://example.com/logo#secret").forEach { assertFalse(isSafeLogoUrl(it)) }
      assertTrue(isSafeLogoUrl("https://example.com/logo.png"))
      assertTrue(useBlackOnPrimary("#FFFFFF"))
      assertTrue(useBlackOnPrimary("#FFFF00"))
      assertFalse(useBlackOnPrimary("#000000"))
      assertFalse(useBlackOnPrimary("#1565C0"))
   }

   @Test
   fun serverSwitchDoesNotReuseBrandAndPublicRequestsContainNoCredentials() = runBlocking()
   {
      MockWebServer().use { first -> MockWebServer().use { second ->
         first.enqueue(MockResponse().setBody("{\"displayName\":\"Customer A\",\"primaryColor\":\"#123456\"}"))
         second.enqueue(MockResponse().setResponseCode(404))
         val firstRuntime = HostRuntime(first.url("/").toString())
         val secondRuntime = HostRuntime(second.url("/").toString())
         assertEquals("Customer A", firstRuntime.branding().displayName)
         assertEquals(HostBrand(), secondRuntime.branding())
         for(request in listOf(first.takeRequest(), second.takeRequest()))
         {
            assertEquals("/mobile/v1/branding", request.path)
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("X-QQQ-Device-Token"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
         }
         assertFalse(secondRuntime.diagnostics.text().contains("BRAND_LOADED"))
      } }
   }

   @Test
   fun reachabilityRequiresExpectedStatusAndNeverLogsResponseData() = runBlocking()
   {
      MockWebServer().use { server ->
         server.enqueue(MockResponse().setBody("{\"status\":\"ok\"}"))
         server.enqueue(MockResponse().setBody("<html>private-barcode-and-token</html>"))
         server.enqueue(MockResponse().setResponseCode(503).setBody("private-password"))
         val runtime = HostRuntime(server.url("/").toString())
         assertTrue(runtime.connected())
         assertFalse(runtime.connected())
         assertFalse(runtime.connected())
         assertFalse(runtime.diagnostics.text().contains("private"))
         assertTrue(runtime.diagnostics.text().contains("CONNECTION_FAILED HTTP=503"))
      }
   }

   @Test
   fun diagnosticsAreBoundedAndClearable()
   {
      val log = DiagnosticLog(2)
      log.record(DiagnosticCode.CONNECTION_OK, 200, 1)
      log.record(DiagnosticCode.CONNECTION_FAILED, 500, 2)
      log.record(DiagnosticCode.CONNECTION_FAILED, null, 3)
      assertEquals(2, log.text().lines().size)
      assertFalse(log.text().contains("CONNECTION_OK"))
      log.clear()
      assertEquals("", log.text())
   }
}
