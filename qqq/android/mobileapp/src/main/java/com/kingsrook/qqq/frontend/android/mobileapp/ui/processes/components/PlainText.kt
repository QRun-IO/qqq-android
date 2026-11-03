/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes.components

import androidx.core.text.HtmlCompat

/*******************************************************************************
 ** Compatibility fallback when an older server has no semantic plain-text sibling.
 *******************************************************************************/
internal fun plainText(html: String): String = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
