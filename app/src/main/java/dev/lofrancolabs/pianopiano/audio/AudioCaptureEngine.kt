package dev.lofrancolabs.pianopiano.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import dev.lofrancolabs.pianopiano.model.MicSensitivity
import dev.lofrancolabs.pianopiano.model.MicSourceMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max

data class AudioMonitorSnapshot(
    val rms: Float = 0f,
    val dbFs: Float = -80f,
    val onsetThreshold: Float = 0.006f,
    val offsetThreshold: Float = 0.003f,
    val liveMidi: Int? = null,
    val liveMidis: Set<Int> = emptySet(),
    val liveFrequencyHz: Float? = null,
    val liveConfidence: Float? = null,
    val activeAudioSourceLabel: String = "Idle"
)

class AudioCaptureEngine(
    private val context: Context,
    private val onNoteOn: (midi: Int, timestampMs: Long) -> Unit,
    private val onNoteOff: (midi: Int, timestampMs: Long) -> Unit,
    private val onMonitorUpdate: (AudioMonitorSnapshot) -> Unit
) {
    private data class SourceCandidate(
        val audioSource: Int,
        val digitalGain: Float,
        val label: String
    )

    private data class SelectedAudioRecord(
        val audioRecord: AudioRecord,
        val digitalGain: Float,
        val label: String
    )

    private val isRunning = AtomicBoolean(false)
    private val isCalibrating = AtomicBoolean(false)
    private val pendingTrackerReset = AtomicBoolean(false)
    private var captureThread: Thread? = null

    @Volatile
    private var sourceMode: MicSourceMode = MicSourceMode.RAW

    @Volatile
    private var activeSourceLabel: String = "Idle"

    private val tracker = OnsetEnvelopeTracker(
        sampleRate = SAMPLE_RATE,
        hopSize = HOP_SIZE,
        onNoteOn = { midi, ts -> onNoteOn(midi, ts) },
        onNoteOff = { midi, ts -> onNoteOff(midi, ts) }
    )

    @Volatile
    private var calibrationCallback: ((Float) -> Unit)? = null
    private val calibrationSamples = ArrayList<Float>(300)

    fun getNoiseFloor(): Float = tracker.getNoiseFloor()

    fun setNoiseFloor(rms: Float) {
        tracker.setNoiseFloor(rms)
    }

    fun setSensitivity(sensitivity: MicSensitivity) {
        tracker.setSensitivity(sensitivity)
    }

    fun setPolyphonicMode(enabled: Boolean) {
        tracker.setPolyphonicMode(enabled)
    }

    fun setSourceMode(mode: MicSourceMode) {
        if (sourceMode == mode) return
        sourceMode = mode
        if (isRunning.get()) {
            stop()
            start()
        }
    }

    fun resetTracker() {
        if (isRunning.get()) {
            pendingTrackerReset.set(true)
        } else {
            tracker.reset()
        }
    }

    fun start(): Boolean {
        if (isRunning.get()) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val selected = createAudioRecord() ?: return false
        val audioRecord = selected.audioRecord
        val selectedGain = selected.digitalGain
        activeSourceLabel = selected.label
        pendingTrackerReset.set(false)
        tracker.reset()
        isRunning.set(true)

        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val hopBuffer = FloatArray(HOP_SIZE)
            var lastUiEmitMs = 0L
            var prevRawSample = 0f
            var prevFilteredSample = 0f
            var warmupHops = 4

            try {
                audioRecord.startRecording()
                while (isRunning.get()) {
                    var filled = 0
                    var readFailed = false
                    while (filled < HOP_SIZE && isRunning.get()) {
                        val readCount = audioRecord.read(
                            hopBuffer,
                            filled,
                            HOP_SIZE - filled,
                            AudioRecord.READ_BLOCKING
                        )
                        if (readCount > 0) {
                            filled += readCount
                        } else if (readCount < 0) {
                            readFailed = true
                            break
                        }
                    }
                    if (readFailed || filled < HOP_SIZE || !isRunning.get()) {
                        break
                    }

                    for (i in 0 until HOP_SIZE) {
                        val raw = (hopBuffer[i] * selectedGain).coerceIn(-1f, 1f)
                        val hp = (raw - prevRawSample + DC_BLOCK_ALPHA * prevFilteredSample).coerceIn(-1f, 1f)
                        prevRawSample = raw
                        prevFilteredSample = hp
                        hopBuffer[i] = hp
                    }

                    if (warmupHops > 0) {
                        warmupHops--
                        continue
                    }

                    val timestampMs =
                        (SystemClock.elapsedRealtimeNanos() / 1_000_000L) - INPUT_LATENCY_MS

                    val displayRms = if (isCalibrating.get()) {
                        val rms = YinPitchDetector.computeRms(hopBuffer, HOP_SIZE)
                        calibrationSamples.add(rms)
                        if (calibrationSamples.size >= CALIBRATION_HOPS) {
                            finishCalibration()
                        }
                        rms
                    } else {
                        if (pendingTrackerReset.getAndSet(false)) {
                            tracker.reset()
                        }
                        tracker.processHop(hopBuffer, timestampMs)
                        tracker.currentRms
                    }

                    val nowMs = SystemClock.elapsedRealtimeNanos() / 1_000_000L
                    if (nowMs - lastUiEmitMs >= UI_EMIT_INTERVAL_MS) {
                        lastUiEmitMs = nowMs
                        val dbFs = if (displayRms > 1e-6f) {
                            (20f * log10(displayRms)).coerceIn(-80f, 0f)
                        } else {
                            -80f
                        }
                        val estimate = tracker.currentPitchEstimate
                        val activeSet = tracker.currentMidis.ifEmpty {
                            estimate?.midiNote?.let(::setOf) ?: emptySet()
                        }
                        onMonitorUpdate(
                            AudioMonitorSnapshot(
                                rms = displayRms,
                                dbFs = dbFs,
                                onsetThreshold = tracker.currentOnsetThreshold,
                                offsetThreshold = tracker.currentOffsetThreshold,
                                liveMidi = tracker.currentMidi ?: estimate?.midiNote,
                                liveMidis = activeSet,
                                liveFrequencyHz = estimate?.frequencyHz,
                                liveConfidence = estimate?.confidence,
                                activeAudioSourceLabel = activeSourceLabel
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
                // Capture stopped or interrupted cleanly
            } finally {
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: IllegalStateException) {
                }
                audioRecord.release()
                isRunning.set(false)
                activeSourceLabel = "Idle"
                onMonitorUpdate(
                    AudioMonitorSnapshot(
                        onsetThreshold = tracker.currentOnsetThreshold,
                        offsetThreshold = tracker.currentOffsetThreshold,
                        activeAudioSourceLabel = "Idle"
                    )
                )
            }
        }, "PianoPiano-AudioCapture")

        captureThread = thread
        thread.start()
        return true
    }

    fun stop() {
        isCalibrating.set(false)
        calibrationCallback = null
        isRunning.set(false)
        val thread = captureThread
        captureThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(400)
            } catch (_: InterruptedException) {
            }
        }
        tracker.reset()
    }

    fun startCalibration(onComplete: (Float) -> Unit) {
        isCalibrating.set(false)
        calibrationSamples.clear()
        calibrationCallback = onComplete
        isCalibrating.set(true)
        if (!isRunning.get()) {
            if (!start()) {
                isCalibrating.set(false)
                calibrationCallback = null
                onComplete(tracker.getNoiseFloor())
            }
        }
    }

    private fun finishCalibration() {
        isCalibrating.set(false)
        val sorted = calibrationSamples.sorted()
        val measured = if (sorted.isNotEmpty()) {
            // 75th percentile protects against brief ambient spikes while tracking room noise floor
            sorted[(sorted.size * 3) / 4]
        } else {
            0.002f
        }
        tracker.setNoiseFloor(measured)
        val callback = calibrationCallback
        calibrationCallback = null
        callback?.invoke(tracker.getNoiseFloor())
    }

    private fun supportsUnprocessedSource(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): SelectedAudioRecord? {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferBytes <= 0) return null
        val bufferBytes = max(minBufferBytes, HOP_SIZE * 4 * 8)

        val candidates = when (sourceMode) {
            MicSourceMode.RAW -> buildList {
                if (supportsUnprocessedSource()) {
                    add(SourceCandidate(MediaRecorder.AudioSource.UNPROCESSED, 6.0f, "Unprocessed +6x"))
                }
                add(SourceCandidate(MediaRecorder.AudioSource.VOICE_RECOGNITION, 1.5f, "Voice Recognition +1.5x"))
                add(SourceCandidate(MediaRecorder.AudioSource.MIC, 1.0f, "Standard Mic"))
            }
            MicSourceMode.STANDARD -> listOf(
                SourceCandidate(MediaRecorder.AudioSource.MIC, 1.0f, "Standard Mic"),
                SourceCandidate(MediaRecorder.AudioSource.VOICE_RECOGNITION, 1.2f, "Voice Recognition")
            )
        }

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        for (candidate in candidates) {
            val record = try {
                AudioRecord.Builder()
                    .setAudioSource(candidate.audioSource)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            } catch (_: Throwable) {
                null
            }
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                return SelectedAudioRecord(record, candidate.digitalGain, candidate.label)
            }
            record?.release()
        }
        return null
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val HOP_SIZE = 256
        private const val INPUT_LATENCY_MS = 15L
        private const val CALIBRATION_HOPS = 280 // ~1.5 seconds
        private const val UI_EMIT_INTERVAL_MS = 60L
        private const val DC_BLOCK_ALPHA = 0.995f
    }
}
