package dev.lofrancolabs.pianopiano.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import dev.lofrancolabs.pianopiano.model.MidiUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Low-latency polyphonic piano synthesizer + woodblock metronome using AudioTrack.
 */
class PianoSynthesizer {
    private class Voice {
        var active = false
        var midi = -1
        var f0 = 440f
        var ageSeconds = 0f
        var releasing = false
        var releaseSeconds = 0f
        val phases = FloatArray(HARMONIC_COUNT)
        val phaseIncrements = FloatArray(HARMONIC_COUNT)
    }

    private class ClickState {
        var active = false
        var freq = 1000f
        var phase = 0f
        var ageSeconds = 0f
        var gain = 0.45f
    }

    private val lock = Any()
    private val voices = Array(MAX_VOICES) { Voice() }
    private val click = ClickState()
    private val running = AtomicBoolean(false)
    private var renderThread: Thread? = null

    fun ensureStarted() {
        if (running.get()) return
        val track = createAudioTrack() ?: return
        running.set(true)

        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buffer = FloatArray(BUFFER_FRAMES)
            try {
                track.play()
                while (running.get()) {
                    renderBlock(buffer)
                    val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                }
            } catch (_: Throwable) {
                // Track released cleanly
            } finally {
                try {
                    track.pause()
                    track.flush()
                } catch (_: IllegalStateException) {
                }
                track.release()
                running.set(false)
            }
        }, "PianoPiano-Synth")
        renderThread = thread
        thread.start()
    }

    fun noteOn(midi: Int) {
        ensureStarted()
        val freq = MidiUtils.midiToFrequency(midi).toFloat()
        synchronized(lock) {
            // Reuse same midi voice or pick an inactive/oldest voice
            var target = voices.firstOrNull { !it.active }
            if (target == null) {
                target = voices.maxByOrNull { it.ageSeconds } ?: voices[0]
            }
            target.active = true
            target.midi = midi
            target.f0 = freq
            target.ageSeconds = 0f
            target.releasing = false
            target.releaseSeconds = 0f

            for (h in 0 until HARMONIC_COUNT) {
                val harmonicOrder = (h + 1).toFloat()
                val inharmonicMultiplier =
                    harmonicOrder * sqrt(1f + INHARMONICITY * harmonicOrder * harmonicOrder)
                val harmonicFreq = freq * inharmonicMultiplier
                target.phases[h] = 0f
                target.phaseIncrements[h] = (TWO_PI * harmonicFreq / SAMPLE_RATE).toFloat()
            }
        }
    }

    fun noteOff(midi: Int) {
        synchronized(lock) {
            for (voice in voices) {
                if (voice.active && voice.midi == midi && !voice.releasing) {
                    voice.releasing = true
                    voice.releaseSeconds = 0f
                }
            }
        }
    }

    fun allNotesOff() {
        synchronized(lock) {
            for (voice in voices) {
                if (voice.active && !voice.releasing) {
                    voice.releasing = true
                    voice.releaseSeconds = 0f
                }
            }
        }
    }

    fun triggerMetronome(accent: Boolean) {
        ensureStarted()
        synchronized(lock) {
            click.active = true
            click.freq = if (accent) 1180f else 840f
            click.phase = 0f
            click.ageSeconds = 0f
            click.gain = if (accent) 0.42f else 0.30f
        }
    }

    fun release() {
        running.set(false)
        val thread = renderThread
        renderThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(400)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun renderBlock(out: FloatArray) {
        out.fill(0f)
        val dt = 1f / SAMPLE_RATE

        synchronized(lock) {
            for (i in out.indices) {
                var sample = 0f

                for (voice in voices) {
                    if (!voice.active) continue

                    val t = voice.ageSeconds
                    val attack = if (t < ATTACK_SECONDS) t / ATTACK_SECONDS else 1f
                    var releaseEnv = 1f
                    if (voice.releasing) {
                        releaseEnv = max(0f, 1f - voice.releaseSeconds / RELEASE_SECONDS)
                        voice.releaseSeconds += dt
                        if (releaseEnv <= 0f) {
                            voice.active = false
                            continue
                        }
                    }

                    // Lower notes sustain slightly longer than high treble notes
                    val pitchScale = (440f / voice.f0).coerceIn(0.45f, 1.8f)
                    val baseDecay = 1.7f / pitchScale

                    var harmonicSum = 0f
                    for (h in 0 until HARMONIC_COUNT) {
                        val amp = HARMONIC_WEIGHTS[h] * exp(-t * baseDecay * (1f + 0.42f * h))
                        harmonicSum += amp * sin(voice.phases[h].toDouble()).toFloat()
                        var nextPhase = voice.phases[h] + voice.phaseIncrements[h]
                        if (nextPhase >= TWO_PI_FLOAT) nextPhase -= TWO_PI_FLOAT
                        voice.phases[h] = nextPhase
                    }

                    // Brief hammer transient in first 8ms
                    val hammerTransient = if (t < 0.008f) {
                        0.08f * sin(TWO_PI * voice.f0 * 2.5f * t).toFloat() * (1f - t / 0.008f)
                    } else {
                        0f
                    }

                    sample += (harmonicSum + hammerTransient) * attack * releaseEnv * VOICE_GAIN
                    voice.ageSeconds += dt
                    if (voice.ageSeconds > MAX_VOICE_AGE_SECONDS) {
                        voice.active = false
                    }
                }

                if (click.active) {
                    val ct = click.ageSeconds
                    val env = exp(-ct * 95f) * click.gain
                    sample += env * sin(click.phase.toDouble()).toFloat()
                    click.phase += (TWO_PI_FLOAT * click.freq / SAMPLE_RATE)
                    if (click.phase >= TWO_PI_FLOAT) click.phase -= TWO_PI_FLOAT
                    click.ageSeconds += dt
                    if (click.ageSeconds > 0.045f) {
                        click.active = false
                    }
                }

                out[i] = tanh(sample.toDouble()).toFloat()
            }
        }
    }

    private fun createAudioTrack(): AudioTrack? {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuf <= 0) return null

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf, BUFFER_FRAMES * 4 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)

        val track = try {
            builder.build()
        } catch (_: Throwable) {
            null
        }
        return if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
            track
        } else {
            track?.release()
            null
        }
    }

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val BUFFER_FRAMES = 256
        private const val MAX_VOICES = 12
        private const val HARMONIC_COUNT = 6
        private const val ATTACK_SECONDS = 0.004f
        private const val RELEASE_SECONDS = 0.050f
        private const val MAX_VOICE_AGE_SECONDS = 6.0f
        private const val VOICE_GAIN = 0.28f
        private const val INHARMONICITY = 0.00018f
        private const val TWO_PI = 2.0 * PI
        private const val TWO_PI_FLOAT = (2.0 * PI).toFloat()
        private val HARMONIC_WEIGHTS = floatArrayOf(1.0f, 0.44f, 0.22f, 0.12f, 0.06f, 0.03f)
    }
}
