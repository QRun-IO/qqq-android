/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

/*******************************************************************************
 ** Aggregate network callbacks: losing one transport must not hide another available network.
 *******************************************************************************/
internal class NetworkPresence {
   private val handles = mutableSetOf<Long>()

   /*******************************************************************************
    ** Track available networks by identity so one network's arrival cannot hide another network's loss.
    *******************************************************************************/
   fun onAvailable(networkHandle: Long): Boolean {
      handles.add(networkHandle)
      return handles.isNotEmpty()
   }

   /*******************************************************************************
    ** Remove only the lost network before deriving whether any usable network remains.
    *******************************************************************************/
   fun onLost(networkHandle: Long): Boolean {
      handles.remove(networkHandle)
      return handles.isNotEmpty()
   }
}
