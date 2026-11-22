# Piano Piano

Native Android (Kotlin + Jetpack Compose) beginner piano practice app that listens through your device microphone (or built-in touch keyboard) and gives real-time feedback on pitch, onset timing, and hold duration.

## Key Features

- **Real-Time Microphone Listening**: Captures raw `48 kHz` `PCM_FLOAT` audio (`UNPROCESSED` source with `VOICE_RECOGNITION` fallback), calibrates ambient room noise, detects hammer onsets and release decay, and tracks fundamental pitch via YIN autocorrelation.
- **Pitch, Rhythm & Hold Judging**: Scores each note in real time as `Perfect!`, `Too soon! (-ms)`, `Too late! (+ms)`, `Wrong: <note>`, `Too short!`, `Too long!`, or `Missed`, with a post-run accuracy scorecard.
- **Generated Drills Across 3 Beginner Tiers**: Dynamically generates `4/4` melodic drills across **Beginner** (`C4–E4`, 6–8 bars), **Intermediate** (`C4–G4`, 10–12 bars), and **Advanced** (`C4–C5`, 16–20 bars), with one-tap **Next** continuous practice and XP leveling.
- **English or Solfege Note Names**: Switch between `C, D, E` and `Do, Re, Mi` across the staff, keyboard, and live feedback.
- **Built-In Low-Latency Piano Synth & Touch Mode**: Preview any drill (**Listen**) or practice directly on the 2-octave (`C3`–`C5`) on-screen piano.
- **Offline & Private**: Zero accounts, ads, or network calls. Microphone audio is processed strictly on-device in memory.

## Building & Testing

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

See [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md) for Internal Testing opt-in links and CI/CD release pipeline details.
