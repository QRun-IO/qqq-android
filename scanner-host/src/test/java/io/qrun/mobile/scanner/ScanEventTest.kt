package io.qrun.mobile.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Retrying a submission must not turn it into a second captured scan. */
class ScanEventTest
{
   @Test
   fun retryKeepsIdentityAndOriginalCaptureTime()
   {
      val event = ScanEvent("001234", "LABEL-TYPE-CODE128", "scanner")
      assertEquals(event.values(), event.values())
      assertEquals("001234", event.values()["barcode"])
      assertNotEquals(event.eventId, ScanEvent("001234", "LABEL-TYPE-CODE128", "scanner").eventId)
   }
}
