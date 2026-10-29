/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/** Native credential form for QQQ TABLE_BASED authentication. */
@Composable
fun QPasswordLogin(viewModel: QViewModel, modifier: Modifier = Modifier)
{
   var username by remember { mutableStateOf("") }
   var password by remember { mutableStateOf("") }
   var submitted by remember { mutableStateOf(false) }
   val busy = submitted && viewModel.manageSessionLoadState is LoadState.Loading
   Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
   {
      Text("Sign in")
      OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !busy)
      OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
         visualTransformation = PasswordVisualTransformation(), enabled = !busy)
      Button(enabled = !busy && username.isNotBlank() && password.isNotEmpty(), onClick = {
         submitted = true
         viewModel.loginWithPassword(username.trim(), password)
         password = ""
      }) { Text(if(busy) "Signing in…" else "Sign in") }
      if(submitted && viewModel.manageSessionLoadState is LoadState.Error)
      {
         Text("Sign-in failed. Check your credentials and connection.")
      }
   }
}
