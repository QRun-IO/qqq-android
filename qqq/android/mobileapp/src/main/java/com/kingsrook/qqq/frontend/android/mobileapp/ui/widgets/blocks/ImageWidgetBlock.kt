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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.kingsrook.qqq.frontend.android.mobileapp.R
import com.kingsrook.qqq.frontend.android.mobileapp.ui.utils.BlockStyleUtils

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun ImageWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier)
{
   val widgetBlock = params.widgetBlock
   val qViewModel = params.qViewModel

   // todo - should this go elsewhere?  in some singleton?
   ImageLoader.Builder(context = LocalContext.current)
      .components { add(SvgDecoder.Factory()) }
      .build()

   val imageModifier: Modifier = Modifier.size(BlockStyleUtils.getSizeFromStyleMap(widgetBlock.styles))
   val boxModifier = Modifier.padding(BlockStyleUtils.getPaddingValuesFromStyleMap(widgetBlock.styles))

   Box(modifier = boxModifier)
   {
      AsyncImage(
         model = ImageRequest.Builder(context = LocalContext.current)
            .data(runCatching { qViewModel?.getURI(widgetBlock.values["path"]?.toString() ?: "") }.getOrNull())
            .crossfade(true)
            .build(),
         contentDescription = null,
         error = painterResource(R.drawable.ic_broken_image),
         placeholder = painterResource(R.drawable.loading_img),
         contentScale = ContentScale.Crop,
         modifier = imageModifier
         // .border(1.dp, Color.Gray, RectangleShape),
         // .fillMaxWidth(.75f).fillMaxHeight(.75f)
      )
   }

}
