package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Credentials must never be sent to user-info URLs, unexpected paths, or release HTTP origins. */
class ServerUrlTest
{
   @Test
   fun normalizesOneCanonicalOrigin()
   {
      assertEquals("https://example.com/", normalizeServerUrl(" https://EXAMPLE.com ", false))
      assertEquals("http://192.168.1.20:8000/", normalizeServerUrl("http://192.168.1.20:8000/", true))
   }

   @Test
   fun rejectsUnsafeOrigins()
   {
      listOf("http://example.com", "https://user:password@example.com", "https://example.com/path", "https://example.com/?token=x", "https://example.com/#fragment").forEach { url ->
         assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl(url, false) }
      }
   }
}
