package com.ai.personalassistant.tools
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

class CreativeEngine(private val context: Context) {
    fun createFile(name: String, content: String): String {
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "EddyWorkspace")
        if (!target.exists()) target.mkdirs()
        val file = File(target, name)
        file.writeText(content)
        return file.absolutePath
    }
    fun launchWebApp(name: String, content: String) {
        val path = createFile(name, content)
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
