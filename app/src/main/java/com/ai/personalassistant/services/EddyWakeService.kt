package com.ai.personalassistant.services
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ai.personalassistant.agent.AssistantAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class EddyWakeService : Service(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var agent: AssistantAgent? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var isSpeaking = false
    private var isAwaiting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        val prefs = getSharedPreferences("eddy_prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isNotEmpty()) agent = AssistantAgent(this, apiKey)

        tts = TextToSpeech(this, this)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { isSpeaking = true }
            override fun onDone(id: String?) { isSpeaking = false; restart(250) }
            override fun onError(id: String?) { isSpeaking = false; restart(250) }
        })
        initRecognizer()
        startListening()
    }

    private fun startForegroundNotification() {
        val channelId = "eddy_wake_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "EDDY Neural Core", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("E.D.D.Y. Active")
            .setContentText("Say 'Eddy' to command...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(1003, notification)
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p0: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { if (!isSpeaking) restart(400) }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                    handleVoice(text)
                }
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
        }
    }

    private fun handleVoice(text: String) {
        if (text.isBlank()) { restart(200); return }
        val clean = text.trim()
        val wakeFound = clean.contains("eddy") || clean.contains("adi") || clean.contains("eddie") || clean.contains("edi")

        if (isAwaiting) {
            isAwaiting = false
            execute(clean)
            return
        }

        if (wakeFound) {
            val cmd = clean.replace("hey eddy", "").replace("ok eddy", "").replace("eddy", "").replace("suno eddy", "").trim()
            if (cmd.length > 2) {
                Toast.makeText(this, "EDDY: $cmd", Toast.LENGTH_SHORT).show()
                execute(cmd)
            } else {
                isAwaiting = true
                speak("Haanji Boss, boliye?")
            }
        } else { restart(200) }
    }

    private fun execute(cmd: String) {
        scope.launch {
            val res = agent?.processCommand(cmd)
            if (res != null) speak(res.speech) else restart(200)
        }
    }

    private fun speak(text: String) {
        isSpeaking = true
        speechRecognizer?.cancel()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "EDDY_REPLY")
    }

    private fun startListening() {
        if (isSpeaking) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        }
        try { speechRecognizer?.startListening(intent) } catch (e: Exception) { restart(500) }
    }

    private fun restart(ms: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!isSpeaking) startListening() }, ms)
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale("en", "IN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) tts.language = Locale.US
            tts.setPitch(1.0f)
        }
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); speechRecognizer?.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}
