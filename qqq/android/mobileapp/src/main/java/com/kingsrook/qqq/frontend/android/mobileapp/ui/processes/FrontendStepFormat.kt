/*
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

/***************************************************************************
 ** when a frontend step specifies a "format", that implies a number of
 ** different settings - which this class defines and its subclasses clarify.
 ***************************************************************************/
open class FrontendStepFormat
{
   open fun isStepLabelDisplayed() = true
   open fun doesScreenGetPadding() = true
   open fun areStandardFooterButtonsDisplayed() = true
   open fun playOnSubmitSound() = false
   open fun playOnLoadingSound() = false

   companion object
   {
      /***************************************************************************
       **
       ***************************************************************************/
      fun of(format: String?): FrontendStepFormat
      {
         return when ((format ?: "").toLowerCase(Locale.current))
         {
            "scanner" -> ScannerFormat()
            else -> DefaultFormat()
         }
      }

   }
}

/***************************************************************************
 **
 ***************************************************************************/
class DefaultFormat : FrontendStepFormat()

/***************************************************************************
 **
 ***************************************************************************/
class ScannerFormat : FrontendStepFormat()
{
   /***************************************************************************
    **
    ***************************************************************************/
   override fun isStepLabelDisplayed() = false

   /***************************************************************************
    **
    ***************************************************************************/
   override fun doesScreenGetPadding() = false

   /***************************************************************************
    **
    ***************************************************************************/
   override fun areStandardFooterButtonsDisplayed() = false

   /***************************************************************************
    **
    ***************************************************************************/
   override fun playOnSubmitSound() = true

   /***************************************************************************
    **
    ***************************************************************************/
   override fun playOnLoadingSound() = true
}