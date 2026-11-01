/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import org.junit.Assert.*
import org.junit.Test

class LatestScreenImageTest
{
   private class Image : AutoCloseable {
      var closes = 0
      override fun close() { closes++ }
   }

   @Test fun replacingFramesRetainsOnlyTheNewestAndReleasesPreviousBuffers()
   {
      val latest = LatestScreenImage<Image>()
      val first = Image()
      val second = Image()
      latest.replace(first)
      latest.replace(second)
      assertSame(second, latest.image)
      assertEquals(1, first.closes)
      assertEquals(0, second.closes)
      latest.close()
      assertNull(latest.image)
      assertEquals(1, second.closes)
   }

   @Test fun repeatedStopAndDuplicateNotificationDoNotCloseAnOwnedBufferTwice()
   {
      val latest = LatestScreenImage<Image>()
      val image = Image()
      latest.replace(image)
      latest.replace(image)
      assertEquals(0, image.closes)
      latest.close()
      latest.close()
      assertEquals(1, image.closes)
      assertNull(latest.image)
      val nextSession = Image()
      latest.replace(nextSession)
      assertSame(nextSession, latest.image)
      latest.close()
      assertEquals(1, nextSession.closes)
   }
}
