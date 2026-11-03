#!/usr/bin/env bash
set -euo pipefail

APK="android/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$APK"

# Cold launcher: process must survive and the splash/router must be gone within 5s.
adb logcat -c
adb shell am force-stop com.tscore.app || true
adb shell am start -W -n com.tscore.app/co.tinode.tindroid.InitRouterActivity > launcher-start.log
cat launcher-start.log
sleep 5
adb logcat -d -v threadtime > launcher.log
! grep -E 'FATAL EXCEPTION|Process: com\.tscore\.app.*has died|StackOverflowError|ANR in com\.tscore\.app' launcher.log
adb shell pidof com.tscore.app | grep -q .
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|LoginActivity|ChatsActivity|InitRouterActivity|com.tscore.app' | head -100 > launcher-state.log || true
cat launcher-state.log
! grep -E 'mResumedActivity.*InitRouterActivity|topResumedActivity.*InitRouterActivity' launcher-state.log

# Background -> foreground used to create Tinode/DB from onStop and could crash via FCM.
adb logcat -c
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell monkey -p com.tscore.app -c android.intent.category.LAUNCHER 1
sleep 5
adb logcat -d -v threadtime > lifecycle.log
! grep -E 'FATAL EXCEPTION|Process: com\.tscore\.app.*has died|StackOverflowError|ANR in com\.tscore\.app|Default FirebaseApp is not initialized' lifecycle.log
adb shell pidof com.tscore.app | grep -q .
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|LoginActivity|ChatsActivity|InitRouterActivity|com.tscore.app' | head -100 > lifecycle-state.log || true
cat lifecycle-state.log
! grep -E 'mResumedActivity.*InitRouterActivity|topResumedActivity.*InitRouterActivity' lifecycle-state.log

# Direct Chats debug alias: invalid/no session must recover safely instead of closing.
adb logcat -c
adb shell am force-stop com.tscore.app || true
adb shell am start -W -n com.tscore.app/co.tinode.tindroid.CrashAuditChatsAlias > chats-start.log
cat chats-start.log
! grep -E 'SecurityException|Permission Denial|Error type' chats-start.log
sleep 5
adb logcat -d -v threadtime > chats.log
! grep -E 'FATAL EXCEPTION|Process: com\.tscore\.app.*has died|StackOverflowError|ANR in com\.tscore\.app|Default FirebaseApp is not initialized' chats.log
adb shell pidof com.tscore.app | grep -q .
adb shell dumpsys activity activities | grep -E 'mResumedActivity|LoginActivity|ChatsActivity|CrashAuditChatsAlias|com.tscore.app' | head -100 || true
