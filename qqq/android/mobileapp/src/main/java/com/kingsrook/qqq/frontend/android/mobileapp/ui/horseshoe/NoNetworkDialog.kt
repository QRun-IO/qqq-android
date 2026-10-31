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

package com.kingsrook.qqq.frontend.android.mobileapp.ui.horseshoe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingsrook.qqq.frontend.android.mobileapp.R
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun NoNetworkDialog(qViewModel: QViewModel)
{
   AlertDialog(
      title = { },
      text = {
         Column(horizontalAlignment = Alignment.CenterHorizontally)
         {
            Icon(
               painter = painterResource(id = R.drawable.qqq_no_network),
               tint = Color.Blue,
               contentDescription = null,
               modifier = Modifier.fillMaxWidth()
                  .padding(bottom = 8.dp, top = 40.dp)
            )
            Text("No Network Connection", fontWeight = FontWeight.SemiBold, fontSize = 24.sp, modifier = Modifier.padding(vertical = 16.dp))
            Text("Contact support if this continues", fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
            CircularProgressIndicator(
               modifier = Modifier.width(32.dp).padding(bottom = 40.dp),
               color = MaterialTheme.colorScheme.secondary,
               trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
         }
      },
      onDismissRequest = { /* noop */ },
      dismissButton = { },
      confirmButton = { },
   )
}