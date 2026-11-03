#!/usr/bin/env bash
set -euo pipefail

APK="android/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.tscore.app"
TEST_USER="tscore_ci_audit_20261103"
TEST_PASS="AuditPass20261103"
TEST_EMAIL="tscore.audit.20261103@example.com"
TEST_NAME="TScore Audit"

adb install -r "$APK"

fail_if_runtime_bad() {
  local log_file="$1"
  local pattern="$2"
  if grep -E "$pattern" "$log_file" >/dev/null; then
    echo "=== RUNTIME FAILURE: $log_file ==="
    sed -n '/FATAL EXCEPTION/,+120p' "$log_file" || true
    grep -n -B 20 -A 120 -E "$pattern" "$log_file" || true
    exit 1
  fi
}

capture_and_check() {
  local name="$1"
  sleep "${2:-2}"
  adb logcat -d -v threadtime > "${name}.log"
  fail_if_runtime_bad "${name}.log" 'FATAL EXCEPTION|Process: com\.tscore\.app.*has died|StackOverflowError|ANR in com\.tscore\.app|Default FirebaseApp is not initialized'
  adb shell pidof "$PKG" | grep -q .
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|LoginActivity|ChatsActivity|InitRouterActivity|com.tscore.app' | head -100 > "${name}-state.log" || true
  cat "${name}-state.log"
}

dump_ui() {
  adb shell uiautomator dump /sdcard/tscore-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/tscore-ui.xml 2>/dev/null | tr -d '\r' | sed 's/></>\n</g'
}

bounds_for_id() {
  local id="$1"
  dump_ui | grep -F "resource-id=\"${PKG}:id/${id}\"" | head -1 | \
    sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'
}

has_id() {
  local id="$1"
  dump_ui | grep -Fq "resource-id=\"${PKG}:id/${id}\""
}

wait_id() {
  local id="$1"
  local attempts="${2:-20}"
  for _ in $(seq 1 "$attempts"); do
    if has_id "$id"; then return 0; fi
    sleep 1
  done
  echo "Timed out waiting for id=$id"
  dump_ui | head -200 || true
  return 1
}

tap_id() {
  local id="$1"
  local b
  b="$(bounds_for_id "$id")"
  if [ -z "$b" ]; then
    echo "Unable to find tappable id=$id"
    dump_ui | head -200 || true
    return 1
  fi
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input tap $(((x1+x2)/2)) $(((y1+y2)/2))
}

input_id() {
  local id="$1"
  local value="$2"
  tap_id "$id"
  sleep 0.3
  adb shell input text "$value"
  adb shell input keyevent KEYCODE_ESCAPE || true
}

seed_live_server_prefs() {
  # Legacy Tinode code intentionally redirects emulators to 10.0.2.2:6060 with TLS off.
  # For T-Score E2E we must exercise the same production server/settings as a real phone.
  local prefs_xml
  prefs_xml='<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="pref_hostName">chat.socialrise.garden</string><boolean name="pref_useTLS" value="true" /></map>'
  adb shell "run-as $PKG sh -c 'mkdir -p /data/user/0/$PKG/shared_prefs'"
  printf '%s' "$prefs_xml" | adb shell "run-as $PKG sh -c 'cat > /data/user/0/$PKG/shared_prefs/${PKG}_preferences.xml'"
  echo "=== SEEDED LIVE SERVER PREFS ==="
  adb shell "run-as $PKG cat /data/user/0/$PKG/shared_prefs/${PKG}_preferences.xml"
}

# 1) Cold launcher: process must survive and splash/router must be gone within 5s.
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$PKG/co.tinode.tindroid.InitRouterActivity" > launcher-start.log
cat launcher-start.log
capture_and_check launcher 5
! grep -E 'mResumedActivity.*InitRouterActivity|topResumedActivity.*InitRouterActivity' launcher-state.log

# 2) Background -> foreground used to create Tinode/DB from onStop and could crash via FCM.
adb logcat -c
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
capture_and_check lifecycle 5
! grep -E 'mResumedActivity.*InitRouterActivity|topResumedActivity.*InitRouterActivity' lifecycle-state.log

# 3) Direct Chats debug alias: invalid/no session must recover safely instead of closing.
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$PKG/co.tinode.tindroid.CrashAuditChatsAlias" > chats-start.log
cat chats-start.log
! grep -E 'SecurityException|Permission Denial|Error type' chats-start.log
capture_and_check chats 5

# 4) REAL E2E: create/login a reusable audit account, then physically tap every main tab.
# Start from clean app data so stale state cannot hide navigation bugs.
adb shell pm clear "$PKG" >/dev/null
seed_live_server_prefs
adb logcat -c
adb shell am start -W -n "$PKG/co.tinode.tindroid.InitRouterActivity" > e2e-start.log
cat e2e-start.log
wait_id createAccount 15

# Bootstrap the fixed audit account. On later runs it may already exist; in that case return to login.
tap_id createAccount
wait_id newLogin 10
input_id newLogin "$TEST_USER"
input_id newPassword "$TEST_PASS"
input_id fullName "$TEST_NAME"
input_id email "$TEST_EMAIL"
tap_id signUp
sleep 12

# Successful signup returns to login. Duplicate/rejected signup remains on signup: go back to login.
if ! has_id editLogin; then
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi
wait_id editLogin 10

# Login with the same audit account.
input_id editLogin "$TEST_USER"
input_id editPassword "$TEST_PASS"
tap_id signIn

# Signed-in home must actually appear. If credentials need server-side verification, show logs/UI.
if ! wait_id tabDeals 25; then
  echo "=== SIGNED-IN HOME DID NOT APPEAR ==="
  dump_ui | head -250 || true
  adb logcat -d -v threadtime > signin-failure.log
  grep -n -B 20 -A 120 -E 'Login failed|credential|FATAL EXCEPTION|Exception|Error|connect' signin-failure.log || true
  exit 1
fi
capture_and_check signed-in 2

# Actual bottom-nav clicks, matching the real-device user flow.
for tab in tabChats tabDeals tabPeople tabProfile tabChats; do
  echo "=== TAP $tab ==="
  adb logcat -c
  tap_id "$tab"
  capture_and_check "e2e-${tab}" 3
  wait_id mainBottomNav 5 || wait_id tabDeals 5
  if ! grep -Eq 'ChatsActivity|com.tscore.app' "e2e-${tab}-state.log"; then
    echo "Unexpected activity after $tab"
    exit 1
  fi
done

# 5) Signed-in background/foreground and full restart must preserve a healthy app.
adb logcat -c
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
capture_and_check signed-in-resume 5
wait_id tabDeals 10

adb logcat -c
adb shell am force-stop "$PKG"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
capture_and_check signed-in-restart 7
wait_id tabDeals 10

# Re-run each tab after session restore.
for tab in tabDeals tabPeople tabProfile tabChats; do
  echo "=== RESTORED SESSION TAP $tab ==="
  adb logcat -c
  tap_id "$tab"
  capture_and_check "restored-${tab}" 3
done

echo "FULL_T_SCORE_E2E_PASS"
