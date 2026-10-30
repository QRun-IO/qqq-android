/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2024.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.kingsrook.qqq.frontend.android.mobileapp.ui.utils

import android.util.Log
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.kingsrook.qqq.frontend.android.core.model.metadata.QIcon
import kotlinx.serialization.json.Json

/***************************************************************************
 ** Render an icon - given a QIcon model, a defaultResourceId to use if it
 ** isn't found, and so-on.
 ***************************************************************************/
@Composable
fun DynamicIcon(icon: QIcon?, defaultResourceId: Int?, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = Color.Black)
{
   val context = LocalContext.current
   val drawableId = remember(icon?.semanticName, icon?.name) {
      DrawableUtils.loadDrawableId(context, icon?.semanticName) ?: DrawableUtils.loadDrawableId(context, icon?.name)
   }

   val actualId = if(drawableId != null) drawableId else if(defaultResourceId != null) defaultResourceId else return

   Icon(
      painter = painterResource(id = actualId),
      tint = tint,
      contentDescription = contentDescription,
      modifier = modifier
   )
}


/***************************************************************************
 ** Convert an Any (could be a map, or a json string) into a QIcon
 ***************************************************************************/
fun makeIcon(value: Any?): QIcon?
{
   if(value is Map<*, *>)
   {
      return (QIcon(value["name"]?.toString(), value["path"]?.toString(), value["color"]?.toString(), semanticName = value["semanticName"]?.toString()))
   }

   if(value is String)
   {
      try
      {
         return Json { ignoreUnknownKeys = true }.decodeFromString<QIcon>(value)
      }
      catch(e: Exception)
      {
         // Unknown icon metadata is decorative; retain the caller's fallback.
      }
   }

   return (null)
}
