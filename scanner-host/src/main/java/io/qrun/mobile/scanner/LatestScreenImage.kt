/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** Main-thread owner of one captured image. Replacing or clearing releases the old buffer.
 *******************************************************************************/
internal class LatestScreenImage<T : AutoCloseable> : AutoCloseable {
   var image: T? = null
      private set

   /*******************************************************************************
    ** Adopt the replacement and close the previous image, keeping retained image ownership bounded.
    *******************************************************************************/
   fun replace(next: T?) {
      if (image === next) return
      val previous = image
      image = next
      previous?.close()
   }

   /*******************************************************************************
    ** Release the retained image through the same replacement path used for incoming frames.
    *******************************************************************************/
   override fun close() {
      replace(null)
   }
}
