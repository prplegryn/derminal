package app.derminal

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** No keystrokes, terminal output, clipboard, commands or environment values. */
class Diagnostics(context: Context) {
    private val dir = File(context.filesDir,"diagnostics").apply { mkdirs() }
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r,"derminal-diagnostics").apply { isDaemon = true } }
    fun event(name: String, detail: String = "") {
        writer.execute {
            runCatching {
                val file = File(dir,"current.log")
                if (file.length() > 256 * 1024) { File(dir,"previous.log").delete(); file.renameTo(File(dir,"previous.log")) }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.ROOT).format(Date())
                file.appendText("$time $name ${detail.replace('\n',' ').take(400)}\n")
            }
        }
    }
    fun error(name: String, error: Throwable) = event(name,error.javaClass.simpleName + ": " + error.stackTrace.take(5).joinToString(" > ") { "${it.className}.${it.methodName}:${it.lineNumber}" })
    fun crash(error: Throwable) {
        // The process may exit before the asynchronous log writer can run.
        runCatching {
            val frames=error.stackTrace.take(24).joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            File(dir,"last-crash.log").outputStream().use { it.write("${error.javaClass.name}\n$frames\n".toByteArray());it.flush();(it as java.io.FileOutputStream).fd.sync() }
        }
    }
    fun export(context: Context): File {
        val target = File(context.cacheDir,"exports/derminal-diagnostics.txt").apply { parentFile!!.mkdirs() }
        target.writeText("derminal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}\nABI ${Build.SUPPORTED_ABIS.joinToString()}\n\n" + listOf("last-crash.log","previous.log","current.log").joinToString("\n") { File(dir,it).takeIf { f -> f.exists() }?.readText().orEmpty() })
        return target
    }
}
