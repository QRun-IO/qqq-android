#!/usr/bin/env bash
# Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
# Run only on a disposable emulator: verify the real scanner APK reaches setup.
set -euo pipefail

if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
   echo "Refusing scanner smoke on a physical device." >&2
   exit 1
fi

evidence="scanner-host/build/emulator-smoke"
mkdir -p "$evidence"
adb install -r scanner-host/build/outputs/apk/debug/scanner-host-debug.apk
adb shell pm clear io.qrun.mobile.scanner
adb shell am start -W -n io.qrun.mobile.scanner/.ScannerActivity
for _attempt in {1..15}; do
   adb shell uiautomator dump --compressed /sdcard/qqq-scanner-window.xml >/dev/null
   adb pull /sdcard/qqq-scanner-window.xml "$evidence/window.xml" >/dev/null
   if grep -Fq 'Connect QQQ Mobile' "$evidence/window.xml"; then
      adb exec-out screencap -p >"$evidence/setup.png"
      echo "Scanner setup screen verified on emulator."
      exit 0
   fi
   sleep 1
done

adb exec-out screencap -p >"$evidence/setup-failed.png"
echo "Scanner setup screen did not appear." >&2
exit 1
