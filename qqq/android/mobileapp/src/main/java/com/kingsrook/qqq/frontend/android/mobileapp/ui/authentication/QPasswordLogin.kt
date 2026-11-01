/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package com.kingsrook.qqq.frontend.android.mobileapp.ui.authentication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.LoadState
import com.kingsrook.qqq.frontend.android.mobileapp.viewmodel.QViewModel

/*******************************************************************************
 ** Native credential form for QQQ TABLE_BASED authentication.
 *******************************************************************************/
@Composable
fun QPasswordLogin(viewModel: QViewModel, modifier: Modifier = Modifier) {
   var username by remember { mutableStateOf("") }
   var password by remember { mutableStateOf("") }
   var submitted by remember { mutableStateOf(false) }
   val focusManager = LocalFocusManager.current
   val keyboard = LocalSoftwareKeyboardController.current
   val busy = submitted && viewModel.manageSessionLoadState is LoadState.Loading
   Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
   {
      Text("Sign in")
      OutlinedTextField(
         username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !busy,
         keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
         keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
      )
      OutlinedTextField(
         password, { password = it }, label = { Text("Password") }, singleLine = true,
         visualTransformation = PasswordVisualTransformation(), enabled = !busy,
         keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
         keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() })
      )
      Button(enabled = !busy && username.isNotBlank() && password.isNotEmpty(), onClick = {
         focusManager.clearFocus()
         keyboard?.hide()
         submitted = true
         viewModel.loginWithPassword(username.trim(), password)
         password = ""
      }) { Text(if (busy) "Signing in…" else "Sign in") }
      if (submitted && viewModel.manageSessionLoadState is LoadState.Error) {
         Text("Sign-in failed. Check your credentials and connection.")
      }
   }
}
