package com.example.speakoke

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import android.os.Environment
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.UnsupportedTagException
import com.mpatric.mp3agic.InvalidDataException
import java.lang.Runnable
import org.webrtc.AudioProcessingFactory
import org.webrtc.audio.JavaAudioDeviceModule

class MainActivity : AppCompatActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private lateinit var searchEditText: EditText
    private var selectedSongPath: String? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var audioProcessing: AudioProcessing? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE: Int = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val lyricsView = findViewById<TextView>(R.id.lyricsView)
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        startStopButton.isEnabled = false

        if (checkPermissionNotGranted()) {
            requestPermissions()
        }

        searchEditText = findViewById(R.id.searchEditText)

        startStopButton.setOnClickListener {
            if (isPlaying) {
                stopPlaying()
                stopMicToSpeaker()
            } else {
                startPlaying()
                startMicToSpeaker()
            }
            isPlaying = !isPlaying
        }


        // Initialize WebRTC AudioProcessing
        audioProcessing = AudioProcessing.create()
        audioProcessing?.let {
            it.setGainControl(true)
            it.setEchoCancellation(true)
            it.setHighPassFilter(true)
            it.setNoiseSuppression(true)
            it.setVoiceDetection(true)
        }
    }

    private fun checkPermissionNotGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            PERMISSION_REQUEST_CODE)
    }

    // The rest of your existing code goes here...

    private fun startMicToSpeaker() {
        // setup audioTrack & audioRecord
        val minBufferSize = AudioRecord.getMinBufferSize(44100,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT)


        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize)

        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
            44100,
            AudioFormat.CHANNEL_CONFIGURATION_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
            AudioTrack.MODE_STREAM)

        // start playing and recording
        audioTrack?.play()
        audioRecord?.startRecording()

        // create thread to capture audio and write it to the track
        Thread(Runnable {
            val buffer = ByteArray(minBufferSize)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, buffer.size)
                if (read != null) {
                    if (AudioRecord.ERROR_INVALID_OPERATION != read && AudioRecord.ERROR_BAD_VALUE != read) {
                        // process the audio data using WebRTC AudioProcessing
                        audioProcessing?.processStream(buffer)

                        // play the audio data using AudioTrack
                        audioTrack?.write(buffer, 0, read)
                    }
                }
            }
        }).start()
    }

    private fun stopMicToSpeaker() {
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
    }

    private fun startPlaying() {
        if (selectedSongPath != null) {
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(selectedSongPath!!)
                    prepare()
                    start()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            Toast.makeText(this, "No song selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // The rest of your existing code goes here...

    override fun onDestroy() {
        super.onDestroy()

        // Clean up the AudioProcessing when done.
        audioProcessing?.release()
        audioProcessing = null
    }
}

