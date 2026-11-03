#!/usr/bin/env bash
set -euo pipefail

PKG="com.tscore.app"
ACTIVITY="com.tscore.clean.MainActivity"
APK="android/cleanapp/build/outputs/apk/debug/cleanapp-debug.apk"
TOKEN_FILE="/tmp/tscore-token"

adb install -r "$APK" >/dev/null
TOKEN="$(cat "$TOKEN_FILE")"

adb shell pm clear "$PKG" >/dev/null
XML="<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><string name=\"token\">$TOKEN</string></map>"
adb shell "run-as $PKG sh -c 'mkdir -p /data/user/0/$PKG/shared_prefs'"
printf '%s' "$XML" | adb shell "run-as $PKG sh -c 'cat > /data/user/0/$PKG/shared_prefs/tscore_clean.xml'"

adb logcat -c
adb shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep 4

pid_alive() {
  adb shell pidof "$PKG" | tr -d '\r' | grep -Eq '[0-9]'
}

fail_if_bad() {
  adb logcat -d > /tmp/clean-e2e-logcat.txt
  if grep -Eiq 'FATAL EXCEPTION|ANR in com\.tscore\.app|Process: com\.tscore\.app.*has died|StackOverflowError' /tmp/clean-e2e-logcat.txt; then
    cat /tmp/clean-e2e-logcat.txt
    exit 1
  fi
  pid_alive || { cat /tmp/clean-e2e-logcat.txt; exit 1; }
}

find_and_tap() {
  local needle="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
  python3 - "$needle" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
needle=sys.argv[1]
root=ET.parse('/tmp/window.xml').getroot()
for n in root.iter('node'):
    text=n.attrib.get('text','')
    desc=n.attrib.get('content-desc','')
    if needle.lower() in text.lower() or needle.lower() in desc.lower():
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            x=(int(m.group(1))+int(m.group(3)))//2
            y=(int(m.group(2))+int(m.group(4)))//2
            subprocess.check_call(['adb','shell','input','tap',str(x),str(y)])
            sys.exit(0)
print('not found:', needle)
sys.exit(2)
PY
  sleep 2
  fail_if_bad
}

fail_if_bad
find_and_tap "Deals"
find_and_tap "People"
find_and_tap "Bob CI"
find_and_tap "Message"
find_and_tap "Chats"
find_and_tap "Profile"

adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
fail_if_bad

adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 3
fail_if_bad

echo "CLEAN_ANDROID_E2E_PASS"
