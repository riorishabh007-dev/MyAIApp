package com.ai.personalassistant
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ai.personalassistant.agent.AssistantAgent
import com.ai.personalassistant.services.EddyWakeService
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var statusConsole: TextView
    private lateinit var systemStateText: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var agent: AssistantAgent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusConsole = findViewById(R.id.statusConsole)
        systemStateText = findViewById(R.id.systemStateText)
        val btnSpeakNow: Button = findViewById(R.id.btnSpeakNow)
        val btnToggleWake: Button = findViewById(R.id.btnToggleWake)
        val btnConfig: Button = findViewById(R.id.btnConfig)
        val btnAccessibility: Button = findViewById(R.id.btnAccessibility)

        prefs = getSharedPreferences("eddy_prefs", MODE_PRIVATE)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        tts = TextToSpeech(this, this)

        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isNotEmpty()) {
            agent = AssistantAgent(this, apiKey)
            systemStateText.text = "CORE ONLINE: ACTIVE"
        } else {
            systemStateText.text = "READY (OFFLINE FAST-PATH ACTIVE)"
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) { log("🎙️ Listening to you...") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() { log("⏳ Thinking & Executing...") }
            override fun onError(error: Int) { log("❌ Voice error ($error). Tap button again.") }
            override fun onResults(results: Bundle?) {
                val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                log("🗣️ You: \"$command\"")

                lifecycleScope.launch {
                    val key = prefs.getString("api_key", "") ?: ""
                    agent = AssistantAgent(this@MainActivity, key)
                    val res = agent?.processCommand(command)
                    if (res != null) {
                        log("🤖 Eddy: ${res.speech}")
                        log("⚙️ Result: ${res.detail}")
                        tts.speak(res.speech, TextToSpeech.QUEUE_FLUSH, null, "EDDY_MAIN")
                    }
                }
            }
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        btnSpeakNow.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            }
            speechRecognizer.startListening(intent)
        }

        btnToggleWake.setOnClickListener {
            ContextCompat.startForegroundService(this, Intent(this, EddyWakeService::class.java))
            log("⚡ 24/7 background engine started! Say 'Eddy' anytime.")
            Toast.makeText(this, "Eddy Background Active", Toast.LENGTH_SHORT).show()
        }

        btnConfig.setOnClickListener { showApiKeyDialog() }
        btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun log(msg: String) { statusConsole.append("\n$msg") }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply { hint = "Paste Gemini API Key"; setText(prefs.getString("api_key", "")) }
        AlertDialog.Builder(this)
            .setTitle("Configure E.D.D.Y.")
            .setMessage("Paste Gemini API Key:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save & Activate") { _, _ ->
                val k = input.text.toString().trim()
                if (k.isNotEmpty()) {
                    prefs.edit().putString("api_key", k).apply()
                    agent = AssistantAgent(this, k)
                    systemStateText.text = "CORE ONLINE: ACTIVE"
                    log("✅ API Key Saved & Sentient Brain Active!")
                }
            }
            .show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale("en", "IN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) tts.language = Locale.US
            tts.setPitch(1.0f)
        }
    }

    override fun onDestroy() { speechRecognizer.destroy(); tts.stop(); tts.shutdown(); super.onDestroy() }
}
