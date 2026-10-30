package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPresenceTest
{
   @Test
   fun losingCellularDoesNotHideAnAvailableWifiNetwork()
   {
      val networks = NetworkPresence()
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onAvailable(101))
      assertTrue(networks.onLost(100))
      assertFalse(networks.onLost(101))
   }

   @Test
   fun duplicateAndStaleCallbacksDoNotInvalidateTheRemainingNetwork()
   {
      val networks = NetworkPresence()
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onAvailable(100))
      assertTrue(networks.onLost(999))
      assertFalse(networks.onLost(100))
      assertTrue(networks.onAvailable(101))
      assertTrue(networks.onLost(100))
   }
}
