/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.processes

import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase

/*******************************************************************************
 ** when a frontend step specifies a "format", that implies a number of
 ** different settings - which this class defines and its subclasses clarify.
 *******************************************************************************/
open class FrontendStepFormat {
   /*******************************************************************************
    ** Let the server-selected screen format control whether the standard step heading is rendered.
    *******************************************************************************/
   open fun isStepLabelDisplayed() = true

   /*******************************************************************************
    ** Keep standard screen spacing overridable for formats whose controls fill the display.
    *******************************************************************************/
   open fun doesScreenGetPadding() = true

   /*******************************************************************************
    ** Allow specialized formats to own submission controls instead of duplicating the standard footer.
    *******************************************************************************/
   open fun areStandardFooterButtonsDisplayed() = true

   /*******************************************************************************
    ** Make submit audio an explicit format policy rather than an assumption about process names.
    *******************************************************************************/
   open fun playOnSubmitSound() = false

   /*******************************************************************************
    ** Make loading audio opt-in so ordinary metadata screens stay silent.
    *******************************************************************************/
   open fun playOnLoadingSound() = false

   /*******************************************************************************
    ** Resolve advertised format names through a shared default instead of inventing process-specific behavior.
    *******************************************************************************/
   companion object {
      /*******************************************************************************
       *******************************************************************************/
      fun of(format: String?): FrontendStepFormat {
         return when ((format ?: "").toLowerCase(Locale.current)) {
            "scanner" -> ScannerFormat()
            else -> DefaultFormat()
         }
      }

   }
}

/*******************************************************************************
 *******************************************************************************/
class DefaultFormat : FrontendStepFormat()

/*******************************************************************************
 *******************************************************************************/
class ScannerFormat : FrontendStepFormat() {
   /*******************************************************************************
    *******************************************************************************/
   override fun isStepLabelDisplayed() = false

   /*******************************************************************************
    *******************************************************************************/
   override fun doesScreenGetPadding() = false

   /*******************************************************************************
    *******************************************************************************/
   override fun areStandardFooterButtonsDisplayed() = false

   /*******************************************************************************
    *******************************************************************************/
   override fun playOnSubmitSound() = true

   /*******************************************************************************
    *******************************************************************************/
   override fun playOnLoadingSound() = true
}
