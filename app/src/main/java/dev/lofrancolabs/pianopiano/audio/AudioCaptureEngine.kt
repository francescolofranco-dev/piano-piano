package dev.lofrancolabs.pianopiano.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioCaptureEngine(
    private val context: Context,
    private val onNoteOn: (midi: Int, timestampMs: Long) -> Unit,
    private val onNoteOff: (midi: Int, timestampMs: Long) -> Unit,
    private val onMonitorUpdate: (rms: Float, heardMidi: Int?) -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private val isCalibrating = AtomicBoolean(false)
    private var captureThread: Thread? = null

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

    fun start(): Boolean {
        if (isRunning.get()) return true
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val audioRecord = createAudioRecord() ?: return false
        tracker.reset()
        isRunning.set(true)

        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val hopBuffer = FloatArray(HOP_SIZE)
            var lastUiEmitMs = 0L

            try {
                audioRecord.startRecording()
                while (isRunning.get()) {
                    val readCount = audioRecord.read(
                        hopBuffer,
                        0,
                        HOP_SIZE,
                        AudioRecord.READ_BLOCKING
                    )
                    if (readCount == HOP_SIZE) {
                        val timestampMs =
                            (SystemClock.elapsedRealtimeNanos() / 1_000_000L) - INPUT_LATENCY_MS

                        if (isCalibrating.get()) {
                            val rms = YinPitchDetector.computeRms(hopBuffer, HOP_SIZE)
                            calibrationSamples.add(rms)
                            if (calibrationSamples.size >= CALIBRATION_HOPS) {
                                finishCalibration()
                            }
                        } else {
                            tracker.processHop(hopBuffer, timestampMs)
                        }

                        val nowMs = SystemClock.elapsedRealtimeNanos() / 1_000_000L
                        if (nowMs - lastUiEmitMs >= UI_EMIT_INTERVAL_MS) {
                            lastUiEmitMs = nowMs
                            val displayRms = if (isCalibrating.get()) {
                                YinPitchDetector.computeRms(hopBuffer, HOP_SIZE)
                            } else {
                                tracker.currentRms
                            }
                            onMonitorUpdate(displayRms, tracker.currentMidi)
                        }
                    } else if (readCount < 0) {
                        break
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
                onMonitorUpdate(0f, null)
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
            0.004f
        }
        tracker.setNoiseFloor(measured)
        val callback = calibrationCallback
        calibrationCallback = null
        callback?.invoke(tracker.getNoiseFloor())
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferBytes <= 0) return null
        val bufferBytes = max(minBufferBytes, HOP_SIZE * 4 * 8)

        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        for (source in sources) {
            val candidate = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
            } catch (_: Throwable) {
                null
            }
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            candidate?.release()
        }
        return null
    }

    companion object {
        const val SAMPLE_RATE = 48_000
        const val HOP_SIZE = 256
        private const val INPUT_LATENCY_MS = 15L
        private const val CALIBRATION_HOPS = 280 // ~1.5 seconds
        private const val UI_EMIT_INTERVAL_MS = 33L
    }
}
