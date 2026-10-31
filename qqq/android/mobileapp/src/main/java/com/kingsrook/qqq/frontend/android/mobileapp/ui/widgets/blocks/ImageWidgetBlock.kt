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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.widgets.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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

   val context = LocalContext.current
   val imageLoader = remember(context)
   {
      ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
   }
   DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }

   val imageModifier: Modifier = Modifier.size(BlockStyleUtils.getSizeFromStyleMap(widgetBlock.styles))
   val boxModifier = Modifier.padding(BlockStyleUtils.getPaddingValuesFromStyleMap(widgetBlock.styles))

   Box(modifier = boxModifier)
   {
      AsyncImage(
         imageLoader = imageLoader,
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
