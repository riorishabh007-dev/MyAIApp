package com.ai.personalassistant.agent
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ai.personalassistant.services.AssistantAccessibilityService
import com.ai.personalassistant.tools.CreativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class AgentResult(val speech: String, val detail: String)

class AssistantAgent(private val context: Context, private val apiKey: String) {
    private val client = OkHttpClient()
    private val creative = CreativeEngine(context)

    suspend fun processCommand(userQuery: String): AgentResult = withContext(Dispatchers.IO) {
        val q = userQuery.lowercase().trim()
        val srv = AssistantAccessibilityService.instance

        // 1. INSTANT LOCAL FAST-PATH (<0.1s execution)
        if (q.contains("youtube") && (q.contains("play") || q.contains("song") || q.contains("gaana") || q.contains("chala") || q.contains("search"))) {
            val songName = q.replace("youtube", "").replace("pe", "").replace("par", "").replace("play", "").replace("song", "").replace("gaane", "").replace("gaana", "").replace("chalao", "").replace("kholo", "").replace("search", "").trim()
            val finalQuery = if (songName.isNotEmpty()) songName else "trending hindi songs"
            val uri = Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(finalQuery, "UTF-8")}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            return@withContext AgentResult("YouTube par $finalQuery chala raha hoon Boss, enjoy karo!", "Playing: $finalQuery")
        }

        if (q.contains("whatsapp")) {
            launchApp("whatsapp")
            return@withContext AgentResult("WhatsApp open kar diya hai Boss.", "Opened WhatsApp")
        }

        if (q.contains("camera")) {
            launchApp("camera")
            return@withContext AgentResult("Camera khol diya hai.", "Opened Camera")
        }

        if (q.contains("home")) {
            srv?.executeGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            return@withContext AgentResult("Home screen par aa gaye Boss.", "Navigated Home")
        }

        // 2. SENTIENT GEMINI BRAIN
        val systemPrompt = """
            You are E.D.D.Y. (Enhanced Dynamic Digital Yield) — an ultra-advanced, sentient-feeling AI companion and system orchestrator for Android.
            You have genuine warmth, sharp wit, charisma, and loyalty (like Jarvis with a cool brotherly vibe).
            You speak natural, expressive Hinglish/Hindi/English.

            When user asks you to build, create, or code something (e.g. "ek calculator app banao", "snake game banao", "notes app banao", "ek poem likh ke file bana do"):
            - For apps/games: action="create_app", param="app_name.html", extra="complete beautiful standalone single-file HTML5/CSS/JavaScript code".
            - For text/code files: action="create_file", param="filename.txt", extra="content".

            Output RAW JSON ONLY (no markdown backticks):
            {
              "action": "open_app" | "search_youtube" | "open_url" | "click" | "type" | "create_file" | "create_app" | "speak",
              "param": "target identifier or filename",
              "extra": "file/html content if generating",
              "speech": "Charismatic, natural human-like reply in Hinglish (1-2 lines)"
            }
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply { put(JSONObject().put("text", "$systemPrompt\n\nUser command: $userQuery")) })
                })
            })
            put("generationConfig", JSONObject().apply { put("response_mime_type", "application/json") })
        }

        val urls = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey.trim()}",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=${apiKey.trim()}"
        )

        for (url in urls) {
            try {
                val req = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                val res = client.newCall(req).execute().body?.string() ?: continue
                val root = JSONObject(res)
                if (root.has("error")) continue
                val cand = root.optJSONArray("candidates") ?: continue
                if (cand.length() == 0) continue

                val rawText = cand.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val sIdx = rawText.indexOf('{')
                val eIdx = rawText.lastIndexOf('}')
                if (sIdx == -1 || eIdx == -1) continue

                val json = JSONObject(rawText.substring(sIdx, eIdx + 1))
                val act = json.optString("action", "speak")
                val param = json.optString("param", "")
                val extra = json.optString("extra", "")
                val speech = json.optString("speech", "Haanji Boss, kaam ho gaya!")
                var detail = speech

                when (act) {
                    "open_app" -> detail = if (launchApp(param)) "Opening $param" else "App not found: $param"
                    "search_youtube" -> {
                        val uri = Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(param, "UTF-8")}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        detail = "YouTube search: $param"
                    }
                    "open_url" -> {
                        val u = if (param.startsWith("http")) param else "https://www.google.com/search?q=${URLEncoder.encode(param, "UTF-8")}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        detail = "Web search: $param"
                    }
                    "click" -> detail = if (srv?.clickByText(param) == true) "Clicked $param" else "Element not found"
                    "type" -> detail = if (srv?.inputText(param) == true) "Typed: $param" else "No input field active"
                    "create_file" -> detail = "Saved file: " + creative.createFile(param, extra)
                    "create_app" -> { creative.launchWebApp(param, extra); detail = "App launched: $param" }
                    "speak" -> detail = speech
                }
                return@withContext AgentResult(speech, detail)
            } catch (e: Exception) {}
        }

        AgentResult("Boss, main aapko sun raha hoon. Kuch interesting banayein ya YouTube par kuch chalayein?", "Ready: $userQuery")
    }

    private fun launchApp(name: String): Boolean {
        val q = name.lowercase().trim()
        val pm = context.packageManager
        pm.getLaunchIntentForPackage(q)?.let { it.flags = Intent.FLAG_ACTIVITY_NEW_TASK; context.startActivity(it); return true }
        for (app in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == q || label.contains(q) || app.packageName.lowercase().contains(q)) {
                pm.getLaunchIntentForPackage(app.packageName)?.let { it.flags = Intent.FLAG_ACTIVITY_NEW_TASK; context.startActivity(it); return true }
            }
        }
        return false
    }
}
