# Dewi Anjani Live (prototype)

Android Kotlin prototype for sending microphone/USB audio input to an Icecast server as AAC-LC over HTTP PUT.

## Build
Open in Android Studio (JDK 17, Android Gradle Plugin 8.7.3) or push to GitHub `main`; GitHub Actions builds a debug APK.

## Icecast setup
Use the Icecast source username/password, host, port, and mountpoint (e.g. `/live.aac`). Ensure the server accepts HTTP PUT source clients and AAC/ADTS streams. Configure the mount with AAC support as needed.

## Important prototype limitations
- This initial version uses Android's default microphone source. USB audio input selection varies by phone/interface and must be verified on the target device.
- It sends AAC-LC with ADTS framing. Confirm that the Icecast server and player accept AAC/ADTS on the chosen mount.
- For production, add TLS (`https`), secure credential storage, reconnection/backoff, runtime permission handling, audio device selection, proper status reporting, and test on the exact Android model.
- Never expose the source password in public repositories. Use a test server while developing.
- This is a starter project, not yet production-certified.
