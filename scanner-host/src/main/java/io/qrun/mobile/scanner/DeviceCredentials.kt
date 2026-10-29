/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Enrollment belongs to one server; its secret is encrypted by a non-exportable device key. */
class DeviceCredentials(context: Context, server: String)
{
   private val scope = MessageDigest.getInstance("SHA-256").digest(server.toByteArray()).joinToString("") { "%02x".format(it) }
   private val file = File(context.noBackupFilesDir, "device-$scope.json")
   private val alias = "qqq-mobile-device"
   private var values: JSONObject = if(file.exists()) decrypt(file.readText()) else JSONObject().put("installationId", UUID.randomUUID().toString())

   init { if(!file.exists()) save() }

   val installationId: String get() = values.getString("installationId")
   val deviceId: String get() = values.optString("deviceId")

   /** Credentials are headers only and never become QQQ process inputs. */
   fun headers(): Map<String, String> = if(deviceId.isBlank()) emptyMap() else mapOf(
      "X-QQQ-Device-Id" to deviceId, "X-QQQ-Device-Token" to values.getString("deviceToken")
   )

   /** Save before exposing the enrollment result to UI. */
   fun enroll(response: JSONObject)
   {
      values.put("deviceId", response.get("deviceId").toString())
      values.put("deviceToken", response.getString("deviceToken"))
      save()
   }

   /** A lost enrollment response requires a fresh identity and a new administrator approval. */
   fun restartPendingEnrollment()
   {
      check(deviceId.isBlank()) { "An enrolled identity cannot be silently replaced" }
      values = JSONObject().put("installationId", UUID.randomUUID().toString())
      save()
   }

   /** Android Keystore owns the AES key across application launches. */
   private fun key(): SecretKey
   {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
      return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
         init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
      }.generateKey()
   }

   /** Atomic replacement prevents a partially written token after interruption. */
   private fun save()
   {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
      val encrypted = cipher.doFinal(values.toString().toByteArray(Charsets.UTF_8))
      val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
      val atomicFile = android.util.AtomicFile(file)
      val stream = atomicFile.startWrite()
      try { stream.write(encoded.toByteArray()); atomicFile.finishWrite(stream) }
      catch(exception: Exception) { atomicFile.failWrite(stream); throw exception }
   }

   /** Corrupt storage fails closed instead of silently enrolling a replacement identity. */
   private fun decrypt(encoded: String): JSONObject
   {
      val bytes = Base64.decode(encoded, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
      return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
   }
}
