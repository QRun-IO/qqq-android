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

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner


/***************************************************************************
 **
 ***************************************************************************/
@Composable
fun AudioWidgetBlock(params: WidgetBlockParameters, modifier: Modifier = Modifier)
{
   val widgetBlock = params.widgetBlock

   val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
   var mediaPlayer: MediaPlayer? = null

   val path = widgetBlock.values["path"]
   if(path is String)
   {
      val uri = runCatching { params.qViewModel?.getURI(path) }.getOrNull()
      val context = LocalContext.current

      if(uri != null)
      {
         fun doPlay()
         {
            mediaPlayer = createMediaPlayer(context, uri)
            mediaPlayer?.start()
         }

         /////////////////////////////////////////////////////////////////////////////////////////
         // use this lifecycle owner effect "stuff", to play the tone upon initial-composition, //
         // and to dispose of the media player upon dispose/stop/etc                            //
         /////////////////////////////////////////////////////////////////////////////////////////
         DisposableEffect(lifecycleOwner)
         {
            ///////////////////////////////////////////////////////////////
            // Create an observer that triggers our remembered callbacks //
            ///////////////////////////////////////////////////////////////
            val observer = LifecycleEventObserver()
            { _, event ->
               if(event == Lifecycle.Event.ON_START)
               {
                  //////////////////////////////////////////////////////////////////////////////////////////////
                  // go through the actionCallback - to try to manage making sure we only play the audio once //
                  // (since we'll assume that the callback runs in an object which doesn't get re-created if  //
                  // the device orientation changes -- without this (e.g., if we just played the audio here), //
                  // then we re-played it upon screen rotation - which wasn't good.                           //
                  //////////////////////////////////////////////////////////////////////////////////////////////
                  params.actionCallback?.invoke(mapOf(
                     "registerControlCallbackName" to "AudioWidgetBlock",
                     "registerControlCallbackFunction" to ::doPlay))

                  params.actionCallback?.invoke(mapOf(
                     "controlCode" to "invokeControlCallbackOnlyOnce:AudioWidgetBlock"
                  ))
               }
               else if(event == Lifecycle.Event.ON_STOP)
               {
                  mediaPlayer?.release()
                  mediaPlayer = null
               }
            }

            ///////////////////////////////////////
            // Add the observer to the lifecycle //
            ///////////////////////////////////////
            lifecycleOwner.lifecycle.addObserver(observer)

            /////////////////////////////////////////////////////////////////
            // When the effect leaves the Composition, remove the observer //
            /////////////////////////////////////////////////////////////////
            onDispose()
            {
               lifecycleOwner.lifecycle.removeObserver(observer)
               mediaPlayer?.release()
               mediaPlayer = null
            }
         }
      }
      else
      {
         Text("No uri for audio...")
      }
   }
   else
   {
      Text("No path for audio...")
   }
}


/***************************************************************************
 ** Create a media play for a specific URI
 ***************************************************************************/
fun createMediaPlayer(context: Context, uri: String): MediaPlayer?
{
   val player: MediaPlayer? = MediaPlayer.create(context, Uri.parse(uri))
   player?.setVolume(1f, 1f)
   return player
}
