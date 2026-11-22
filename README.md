# Piano Piano

Native Android (Kotlin + Jetpack Compose) beginner piano practice app that listens through your device microphone (or built-in touch keyboard) and gives real-time feedback on pitch, onset timing, and hold duration.

## Key Features

- **Real-Time Microphone Listening**: Captures raw `48 kHz` `PCM_FLOAT` audio (`UNPROCESSED` source with `VOICE_RECOGNITION` fallback), calibrates ambient room noise, detects hammer onsets and release decay, and tracks fundamental pitch via YIN autocorrelation.
- **Pitch, Rhythm & Hold Judging**: Scores each note in real time as `Perfect!`, `Too soon! (-ms)`, `Too late! (+ms)`, `Wrong: <note>`, `Too short!`, `Too long!`, or `Missed`, with a post-run accuracy scorecard.
- **Hybrid Staff + Proportional Duration Bars**: Scrolling treble clef staff showing traditional noteheads paired with horizontal duration bars and live color-coded hold fills.
- **Built-In Low-Latency Piano Synth & Touch Mode**: Listen to any drill beforehand (**Preview**) or switch to **Touch** mode to practice directly on the 2-octave (`C3`–`C5`) on-screen piano.
- **5 Starter Beginner Exercises**: *Middle C Pulse*, *Hold It Right*, *Five-Finger Escalator*, *Skipping Stones*, and *Ode to Joy* (50–120 BPM).
- **Offline & Private**: Zero accounts, ads, or network calls. Microphone audio is processed strictly on-device in memory.

## Building & Testing

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

See [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md) for Internal Testing opt-in links and CI/CD release pipeline details.
