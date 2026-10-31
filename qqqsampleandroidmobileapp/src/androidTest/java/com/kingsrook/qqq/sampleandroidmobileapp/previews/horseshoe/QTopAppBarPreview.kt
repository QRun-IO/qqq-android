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

package com.kingsrook.qqq.sampleandroidmobileapp.previews.horseshoe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe.QTopAppBar
import com.kingsrook.qqq.sampleandroidmobileapp.previews.utils.PreviewUtils

/***************************************************************************
 **
 ***************************************************************************/
@Preview(widthDp = 400)
@Composable
fun QTopAppBarPreview()
{
   val qViewModel = PreviewUtils.createQViewModel().also()
   {
      it.logInSuccessful()
      it.sessionUserFullName = "John Doekhoff"
   }
   QTopAppBar(qViewModel, "My App")
}
