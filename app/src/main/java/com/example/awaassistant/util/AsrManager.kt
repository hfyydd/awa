package com.example.awaassistant.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AsrManager {
    private const val TAG = "AsrManager"
    private const val SAMPLE_RATE = 16000
    private const val MODEL_ASSET_PATH = "sense_voice/model.int8.onnx"
    private const val TOKENS_ASSET_PATH = "sense_voice/tokens.txt"
    
    private var recognizer: OfflineRecognizer? = null
    var isInitialized = false
        private set
    
    private var recordingJob: Job? = null
    var isRecording = false
        private set
        
    private val pcmDataStream = ByteArrayOutputStream()

    fun init(context: Context) {
        if (isInitialized) return
        if (!hasRequiredAssets(context)) {
            Log.w(TAG, "Skipping ASR initialization because required model assets are missing.")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Initializing Sherpa-ONNX SenseVoice OfflineRecognizer...")
                
                val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                    model = MODEL_ASSET_PATH,
                    language = "auto", // auto detect language
                    useInverseTextNormalization = true
                )
                
                val modelConfig = OfflineModelConfig(
                    senseVoice = senseVoiceConfig,
                    tokens = TOKENS_ASSET_PATH,
                    numThreads = 2,
                    debug = false
                )
                
                val config = OfflineRecognizerConfig(
                    modelConfig = modelConfig,
                    decodingMethod = "greedy_search"
                )
                
                // Initialize using assetManager
                recognizer = OfflineRecognizer(context.assets, config)
                isInitialized = true
                Log.d(TAG, "Sherpa-ONNX SenseVoice OfflineRecognizer initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize OfflineRecognizer", e)
            }
        }
    }

    private fun hasRequiredAssets(context: Context): Boolean {
        return assetExists(context, MODEL_ASSET_PATH) && assetExists(context, TOKENS_ASSET_PATH)
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (e: IOException) {
            Log.w(TAG, "Missing ASR asset: $path")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "AsrManager not initialized")
            return false
        }
        if (isRecording) return true
        
        pcmDataStream.reset()
        isRecording = true
        
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                isRecording = false
                return@launch
            }
            
            try {
                audioRecord.startRecording()
                Log.d(TAG, "AudioRecord started recording")
                
                val buffer = ShortArray(bufferSize)
                while (isRecording && isActive) {
                    val readCount = audioRecord.read(buffer, 0, buffer.size)
                    if (readCount > 0) {
                        // Write short data to stream as byte bytes
                        val byteBuf = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until readCount) {
                            byteBuf.putShort(buffer[i])
                        }
                        pcmDataStream.write(byteBuf.array())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (ex: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord", ex)
                }
                Log.d(TAG, "AudioRecord stopped and released")
            }
        }
        
        return true
    }

    fun stopRecording(onResult: (String) -> Unit) {
        if (!isRecording) {
            onResult("")
            return
        }
        
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        CoroutineScope(Dispatchers.IO).launch {
            val resultText = performTranscription()
            withContext(Dispatchers.Main) {
                onResult(resultText)
            }
        }
    }

    private fun performTranscription(): String {
        val recognizerInstance = recognizer ?: return "Error: Recognizer not initialized"
        val bytes = pcmDataStream.toByteArray()
        if (bytes.isEmpty()) return ""
        
        // Convert byte array of PCM 16-bit to FloatArray normalized to [-1, 1]
        val shortCount = bytes.size / 2
        val shortBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val floatSamples = FloatArray(shortCount)
        
        for (i in 0 until shortCount) {
            floatSamples[i] = shortBuf.get(i).toFloat() / 32768.0f
        }
        
        return try {
            val stream = recognizerInstance.createStream()
            stream.acceptWaveform(floatSamples, SAMPLE_RATE)
            recognizerInstance.decode(stream)
            val result = recognizerInstance.getResult(stream)
            val text = result.text.trim()
            stream.release()
            text
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            "Error: ${e.message}"
        }
    }
    
    fun release() {
        isRecording = false
        recordingJob?.cancel()
        recognizer?.release()
        recognizer = null
        isInitialized = false
    }

    /**
     * 同步停止录音并返回文字（阻塞直到识别完成）
     */
    fun stopRecordingSync(): String {
        if (!isRecording) return ""
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        return performTranscription()
    }

}
