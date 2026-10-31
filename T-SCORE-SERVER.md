# T-Score Server Configuration

Android builds accept `TSCORE_SERVER_HOST` as a Gradle property. Until a dedicated T-Score backend is deployed, the fallback remains the upstream Tinode public server for functional testing.

Example local build:

```bash
cd android
./gradlew assembleDebug -PTSCORE_SERVER_HOST=chat.example.com
```

For production, deploy the included `server/` component on a dedicated host, enable TLS, then build the APK with that host. Firebase `google-services.json` is optional for basic chat builds but required for production FCM push notifications.
