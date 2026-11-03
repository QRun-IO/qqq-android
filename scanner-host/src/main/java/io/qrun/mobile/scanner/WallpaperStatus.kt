/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

/*******************************************************************************
 ** What this process currently knows about the operator's session. Never persisted: a new process starts UNKNOWN.
 *******************************************************************************/
internal enum class WallpaperSessionState { UNKNOWN, SIGNED_OUT, SIGNED_IN }

/*******************************************************************************
 ** Immutable display-only snapshot. Holds no credential, token, cookie or session identifier.
 *******************************************************************************/
internal data class WallpaperSnapshot(
   val state: WallpaperSessionState = WallpaperSessionState.UNKNOWN,
   val displayName: String? = null,
   val serverHost: String? = null,
   val brandName: String = HostBrand().displayName,
   val brandColor: String = HostBrand().primaryColor,
   val foreground: Boolean = false,
   val confirmedAtElapsed: Long = 0L,
   val confirmedAtWall: Long = 0L,
)

/*******************************************************************************
 ** Observer notified after a snapshot changes; it may be called on any thread.
 *******************************************************************************/
internal fun interface WallpaperStatusListener {
   /*******************************************************************************
    ** Read the new snapshot from the store; the callback carries no data.
    *******************************************************************************/
   fun changed()
}

/*******************************************************************************
 ** In-memory publisher of the approved session display name. Identity is dropped on sign-out and clear.
 *******************************************************************************/
internal class WallpaperStatusStore(private val elapsed: () -> Long, private val wall: () -> Long) {
   private val lock = Any()
   private val listeners = LinkedHashSet<WallpaperStatusListener>()
   @Volatile
   var snapshot = WallpaperSnapshot()
      private set

   /*******************************************************************************
    ** Register until removed; the wallpaper engine removes itself when it stops being visible or is destroyed.
    *******************************************************************************/
   fun addListener(listener: WallpaperStatusListener) = synchronized(lock) { listeners.add(listener) }

   /*******************************************************************************
    ** Stop notifying a listener; safe to call when it was never added.
    *******************************************************************************/
   fun removeListener(listener: WallpaperStatusListener) = synchronized(lock) { listeners.remove(listener) }

   /*******************************************************************************
    ** Apply the validated public brand of the selected server; invalid values keep the current brand.
    *******************************************************************************/
   fun setBrand(name: String, color: String) {
      val cleanName = cleanText(name, 80) ?: return
      if (!BRAND_COLOR.matches(color)) return
      update { it.copy(brandName = cleanName, brandColor = color.uppercase()) }
   }

   /*******************************************************************************
    ** Record a signed-in, device-approved session. Each call confirms the session as of now; the caller invokes it
    ** again with foreground=false as the app leaves the screen, which is the last time the server was reachable.
    *******************************************************************************/
   fun signedIn(displayName: String?, serverHost: String?, foreground: Boolean) {
      update {
         it.copy(
            state = WallpaperSessionState.SIGNED_IN, displayName = cleanText(displayName, MAX_NAME), serverHost = cleanText(serverHost, 80),
            foreground = foreground, confirmedAtElapsed = elapsed(), confirmedAtWall = wall()
         )
      }
   }

   /*******************************************************************************
    ** Drop identity when a signed-in session ends (logout, unauthorized, revocation); leave other states alone so
    ** startup does not claim "signed out" before restoration finishes.
    *******************************************************************************/
   fun signedOutIfSignedIn() {
      if (snapshot.state == WallpaperSessionState.SIGNED_IN) update { it.signedOutCopy() }
   }

   /*******************************************************************************
    ** Forget everything session-related (activity destroyed, server change): the state becomes UNKNOWN, not signed out.
    *******************************************************************************/
   fun clear() = update { it.copy(state = WallpaperSessionState.UNKNOWN, displayName = null, serverHost = null, foreground = false, confirmedAtElapsed = 0L, confirmedAtWall = 0L) }

   private fun WallpaperSnapshot.signedOutCopy() =
      copy(state = WallpaperSessionState.SIGNED_OUT, displayName = null, serverHost = null, foreground = false, confirmedAtElapsed = 0L, confirmedAtWall = 0L)

   /*******************************************************************************
    ** Swap the snapshot and notify outside the lock so a listener cannot deadlock the publisher.
    *******************************************************************************/
   private fun update(change: (WallpaperSnapshot) -> WallpaperSnapshot) {
      val toNotify = synchronized(lock) {
         val next = change(snapshot)
         if (next == snapshot) return
         snapshot = next
         listeners.toList()
      }
      toNotify.forEach { it.changed() }
   }

   /*******************************************************************************
    ** Single-line presentation text only: no control characters, bounded length, null when empty.
    *******************************************************************************/
   companion object {
      const val MAX_NAME = 40
      private val BRAND_COLOR = Regex("#[0-9a-fA-F]{6}")

      /*******************************************************************************
       ** Normalize display text before it can reach a canvas.
       *******************************************************************************/
      fun cleanText(raw: String?, max: Int): String? = raw?.filterNot { it.isISOControl() }?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
   }
}

/*******************************************************************************
 ** Where the wallpaper is being drawn. Identity and IP are shown only for HOME.
 *******************************************************************************/
internal enum class WallpaperContext { PREVIEW, LOCKED, HOME }

/*******************************************************************************
 ** Text and colours for one frame.
 *******************************************************************************/
internal data class WallpaperFrame(val header: String, val lines: List<String>, val colorHex: String, val darkText: Boolean)

/*******************************************************************************
 ** Pure rules that decide what the wallpaper may say, so they can be tested without a device.
 *******************************************************************************/
internal object WallpaperPresentation {
   /** The heartbeat only runs while the app is foreground; beyond this the name is no longer shown. */
   const val STALE_AFTER_MILLIS = 15 * 60 * 1000L

   /*******************************************************************************
    ** A signed-in session whose app has been in the background past the window is unverified, not signed in.
    *******************************************************************************/
   fun isStale(snapshot: WallpaperSnapshot, nowElapsed: Long) =
      snapshot.state == WallpaperSessionState.SIGNED_IN && !snapshot.foreground && nowElapsed - snapshot.confirmedAtElapsed >= STALE_AFTER_MILLIS

   /*******************************************************************************
    ** Milliseconds until a displayed, not-yet-stale name must be withdrawn, or null when no timer is needed.
    *******************************************************************************/
   fun millisUntilStale(snapshot: WallpaperSnapshot, nowElapsed: Long): Long? =
      if (snapshot.state == WallpaperSessionState.SIGNED_IN && !snapshot.foreground && !isStale(snapshot, nowElapsed))
         STALE_AFTER_MILLIS - (nowElapsed - snapshot.confirmedAtElapsed) else null

   /*******************************************************************************
    ** Compose the IP line from what the network callback last reported.
    *******************************************************************************/
   fun networkLine(known: Boolean, addresses: String?) = when {
      !known -> "IP: unknown"
      addresses == null -> "IP: no network address"
      else -> "IP: $addresses"
   }

   /*******************************************************************************
    ** Brand only for PREVIEW and LOCKED. HOME adds session state, the approved display name only while it is fresh,
    ** the server host, and the IP. The wording never claims the server just verified a background session.
    *******************************************************************************/
   fun render(snapshot: WallpaperSnapshot, context: WallpaperContext, networkLine: String, nowElapsed: Long, formatTime: (Long) -> String): WallpaperFrame {
      val dark = useBlackOnPrimary(snapshot.brandColor)
      if (context != WallpaperContext.HOME) return WallpaperFrame(snapshot.brandName, emptyList(), snapshot.brandColor, dark)
      val lines = mutableListOf<String>()
      when (snapshot.state) {
         WallpaperSessionState.UNKNOWN -> lines += "Session: not available"
         WallpaperSessionState.SIGNED_OUT -> lines += "Signed out"
         WallpaperSessionState.SIGNED_IN -> if (isStale(snapshot, nowElapsed)) {
            lines += "Session unverified"
            lines += "Last confirmed ${formatTime(snapshot.confirmedAtWall)}"
         } else {
            lines += snapshot.displayName?.let { "Signed in: $it" } ?: "Signed in"
            if (!snapshot.foreground) lines += "Last confirmed ${formatTime(snapshot.confirmedAtWall)} (app in background)"
            snapshot.serverHost?.let { lines += "Server: $it" }
         }
      }
      lines += networkLine
      return WallpaperFrame(snapshot.brandName, lines, snapshot.brandColor, dark)
   }
}

/*******************************************************************************
 ** Summarize the default network's link addresses for display: interface label plus up to two usable addresses,
 ** IPv4 first. Loopback and IPv6 link-local addresses are omitted; scope suffixes are stripped. Null when none remain.
 *******************************************************************************/
internal fun formatNetworkAddresses(interfaceName: String?, addresses: List<String>): String? {
   val usable = addresses.map { it.substringBefore('%') }
      .filter { it.isNotBlank() && !it.startsWith("fe80", ignoreCase = true) && it != "::1" && !it.startsWith("127.") }
      .distinct()
   if (usable.isEmpty()) return null
   val label = interfaceName?.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }?.take(16)?.takeIf { it.isNotEmpty() }
   return (label?.let { "$it " } ?: "") + usable.sortedBy { it.contains(':') }.take(2).joinToString(", ")
}
