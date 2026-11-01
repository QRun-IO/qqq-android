/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.core.model.metadata.authentication

import kotlinx.serialization.Serializable

/** An explicit request for optional provider logout; ordinary logout retains its existing contract. */
@Serializable
data class NativeLogoutRequest(val nativeLogout: NativeLogout)

@Serializable
data class NativeLogout(val postLogoutRedirectUri: String)

/** Secret-bearing response: deliberately no data-class toString/copy/component methods. */
@Serializable
class NativeLogoutResponse(val nativeLogoutData: NativeLogoutData? = null)

/** Transient original ID-token hint. Never use its claims as identity or persist/log this object. */
@Serializable
class NativeLogoutData(val issuer: String, val endSessionEndpoint: String,
   val postLogoutRedirectUri: String, val idTokenHint: String)
