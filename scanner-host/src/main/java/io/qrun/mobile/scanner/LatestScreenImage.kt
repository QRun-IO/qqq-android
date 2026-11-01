/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/** Main-thread owner of one captured image. Replacing or clearing releases the old buffer. */
internal class LatestScreenImage<T : AutoCloseable> : AutoCloseable
{
   var image: T? = null
      private set

   fun replace(next: T?)
   {
      if(image === next) return
      val previous = image
      image = next
      previous?.close()
   }

   override fun close() { replace(null) }
}
