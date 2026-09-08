package com.example.spark.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioCaptureEngine(
    private val onPcmChunkCaptured: (ByteArray) -> Unit
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val POST_UNMUTE_DECAY_MS = 200L
    }

    private val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val bufferSize = maxOf(minBufferSize, 2048)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var unmuteTimestamp = 0L

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (_isRecording.value) return true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch {
                val buffer = ByteArray(1024)
                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // Calculate RMS amplitude
                        val rms = calculateRms(buffer, read)
                        _amplitude.value = rms

                        val now = System.currentTimeMillis()
                        val isDecayActive = (now - unmuteTimestamp) < POST_UNMUTE_DECAY_MS

                        // Only emit chunk if not muted and outside echo decay window
                        if (!_isMuted.value && !isDecayActive) {
                            val chunk = ByteArray(read)
                            System.arraycopy(buffer, 0, chunk, 0, read)
                            onPcmChunkCaptured(chunk)
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            _isRecording.value = false
            return false
        }
    }

    fun stop() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _amplitude.value = 0f
    }

    fun setMute(muted: Boolean) {
        if (_isMuted.value && !muted) {
            // Unmuting: trigger 200ms post-unmute decay for acoustic echo protection
            unmuteTimestamp = System.currentTimeMillis()
        }
        _isMuted.value = muted
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
            i += 2
        }
        val count = length / 2
        if (count == 0) return 0f
        val rms = sqrt(sum / count).toFloat()
        // Normalize roughly between 0.0 and 1.0
        return (rms / 8000f).coerceIn(0f, 1f)
    }
}
