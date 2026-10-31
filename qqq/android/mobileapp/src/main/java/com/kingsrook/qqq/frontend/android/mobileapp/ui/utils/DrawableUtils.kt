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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.utils

import android.annotation.SuppressLint
import android.content.Context

/***************************************************************************
 ** utilities for working with drawable resources
 ***************************************************************************/
object DrawableUtils
{

   /***************************************************************************
    ** given a name (e.g., a material icon name), get the corresponding
    ** resource identifier.  Null (instead of 0...) if it's not found.
    ***************************************************************************/
   @SuppressLint("DiscouragedApi") // for getIdentifier...
   fun loadDrawableId(context: Context, name: String?): Int?
   {
      if(name == null)
      {
         return (null)
      }

      try
      {
         val rs = context.resources.getIdentifier(
            name,
            "drawable",
            context.packageName
         )
         return if(rs == 0) null else rs
      }
      catch(e: Exception)
      {
         return null
      }
   }

}