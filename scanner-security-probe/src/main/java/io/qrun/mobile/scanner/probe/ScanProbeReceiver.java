/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
package io.qrun.mobile.scanner.probe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/** Headless adversarial sender: the NEW intents originate under this ordinary application's UID. */
public final class ScanProbeReceiver extends BroadcastReceiver
{
   @Override
   public void onReceive(Context context, Intent control)
   {
      if(control == null || !"io.qrun.mobile.scanner.probe.EMIT".equals(control.getAction()))
      {
         return;
      }
      String target = control.getStringExtra("targetPackage");
      String marker = control.getStringExtra("marker");
      if(!("io.qrun.mobile.scanner.qa".equals(target) || "io.qrun.mobile.scanner.e2e".equals(target)) ||
         marker == null || !marker.matches("probe-[A-Za-z0-9-]{1,80}"))
      {
         setResultCode(2);
         return;
      }
      for(int index = 0; index < 4; index++)
      {
         Intent forged = new Intent("io.qrun.mobile.scanner.SCAN").setPackage(target)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .putExtra("com.symbol.datawedge.data_string", marker + "-" + index)
            .putExtra("com.symbol.datawedge.label_type", "LABEL-TYPE-CODE128")
            .putExtra("com.symbol.datawedge.source", index % 2 == 0 ? "scanner" : "zebra-emdk")
            .putExtra("senderPackage", "com.symbol.datawedge")
            .putExtra("com.symbol.datawedge.decode_mode", "single");
         context.sendBroadcast(forged);
      }
      // Separate test observation channel, never a scanner callback or submission seam.
      context.sendBroadcast(new Intent("io.qrun.mobile.scanner.probe.ECHO").setPackage(target)
         .putExtra("marker", marker).putExtra("senderUid", Process.myUid()).putExtra("sent", 4));
      Bundle result = new Bundle();
      result.putInt("senderUid", Process.myUid());
      result.putInt("sent", 4);
      result.putString("marker", marker);
      setResultExtras(result);
      setResultCode(1);
   }
}
