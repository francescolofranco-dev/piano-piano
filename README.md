# Piano Piano

Native Android (Kotlin + Jetpack Compose) beginner piano practice app that listens to your acoustic piano through your device microphone and gives real-time feedback on pitch, onset timing, and hold duration.

## Key Features

- **Real-Time Microphone Listening**: Captures `48 kHz` `PCM_FLOAT` audio (validated `UNPROCESSED` / `VOICE_RECOGNITION` / `MIC` input with DC-blocking high-pass filter and digital gain), calibrates ambient room noise, detects smooth acoustic hammer onsets and release decay, and tracks fundamental pitch via YIN autocorrelation.
- **Note Detection Test & Tuning**: Open **Test Note Detection** in the menu drawer to view a live RMS trigger meter, detected pitch/frequency readout, recent detected strikes, Sensitivity (`Low`/`Normal`/`High`), Mic Input (`Raw`/`Standard`), and noise-floor calibration.
- **Pitch, Rhythm & Hold Judging**: Scores each note in real time as `Perfect!`, `Too soon! (-ms)`, `Too late! (+ms)`, `Wrong: <note>`, `Too short!`, `Too long!`, or `Missed`, with a post-run accuracy scorecard.
- **Generated Drills Across 3 Beginner Tiers**: Dynamically generates `4/4` melodic drills across **Beginner** (`C4–E4`, 6–8 bars), **Intermediate** (`C4–G4`, 10–12 bars), and **Advanced** (`C4–C5`, 16–20 bars), with one-tap **Next** continuous practice and XP leveling.
- **English or Solfege Note Names**: Switch between `C, D, E` and `Do, Re, Mi` across the staff, keyboard guide, and live feedback.
- **Built-In Low-Latency Preview Synth**: Preview any drill (**Listen**) and follow along on the 2-octave (`C3`–`C5`) visual keyboard guide.
- **Offline & Private**: Zero accounts, ads, or network calls. Microphone audio is processed strictly on-device in memory.

## Building & Testing

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

See [docs/GOOGLE_PLAY.md](docs/GOOGLE_PLAY.md) for Internal Testing opt-in links and CI/CD release pipeline details.
