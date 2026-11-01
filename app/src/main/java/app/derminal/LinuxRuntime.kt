package app.derminal

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.Os
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class BootState(val ready: Boolean = false, val progress: Float = 0f, val message: String = "正在准备 Debian", val error: String? = null)

class LinuxRuntime(private val context: Context, private val logs: Diagnostics) {
    val root = File(context.filesDir,"debian")
    val temporary = File(context.filesDir,"proot-tmp").apply { mkdirs() }
    val nativeDir: String get() = context.applicationInfo.nativeLibraryDir
    suspend fun prepare(update: (BootState) -> Unit) = withContext(Dispatchers.IO) {
        require("arm64-v8a" in Build.SUPPORTED_ABIS) { "此安装包需要 64 位 ARM 设备" }
        if (File(root,".derminal-ready").exists()) { update(BootState(true,1f)); return@withContext }
        val staging = File(context.filesDir,"debian-installing")
        try {
            val manifest = JSONObject(context.assets.open("runtime.json").bufferedReader().use { it.readText() })
            val required = manifest.getLong("unpackedBytes") + 160L * 1024 * 1024
            check(context.filesDir.usableSpace > required) { "空间不足，请至少保留 ${required / 1024 / 1024} MB" }
            staging.deleteRecursively(); check(staging.mkdirs())
            update(BootState(progress=.01f,message="正在校验系统文件"))
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("debian.zip").use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) { currentCoroutineContext().ensureActive(); val n=input.read(buffer); if (n<0) break; digest.update(buffer,0,n) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == manifest.getString("sha256")) { "系统文件校验失败，请重新下载安装包" }
            var metadata = JSONObject(); var count = 0
            val prefix = staging.canonicalPath + "/"
            ZipInputStream(context.assets.open("debian.zip").buffered(128 * 1024)).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "_derminal.json") metadata = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    else {
                        val dest = File(staging,entry.name).canonicalFile
                        check(dest.path.startsWith(prefix)) { "系统文件路径异常" }
                        if (entry.isDirectory) dest.mkdirs() else {
                            dest.parentFile!!.mkdirs()
                            dest.outputStream().buffered(64 * 1024).use { zip.copyTo(it,64 * 1024) }
                            count++
                            if (count % 120 == 0) update(BootState(progress=.05f + .85f * (count.toFloat()/manifest.getInt("files")).coerceAtMost(1f),message="正在展开 Debian 13"))
                        }
                    }
                    zip.closeEntry()
                }
            }
            check(count == manifest.getInt("files")) { "系统文件不完整" }
            val modes = metadata.getJSONObject("modes")
            modes.keys().forEach { path ->
                val dest=File(staging,path).canonicalFile
                check(dest.path.startsWith(prefix)); Os.chmod(dest.path,modes.getInt(path) and 511)
            }
            val links = metadata.getJSONObject("links")
            // Files are installed first, then links; no write can traverse an archive symlink.
            links.keys().asSequence().sortedByDescending { it.count { c -> c=='/' } }.forEach { path ->
                val link=File(staging,path)
                check(link.canonicalPath.startsWith(prefix)); link.parentFile!!.mkdirs()
                Os.symlink(links.getString(path),link.path)
            }
            File(staging,".derminal-ready").writeText("1")
            check(staging.renameTo(root)) { "无法保存系统，请重试" }
            logs.event("runtime.installed","files=$count")
            update(BootState(true,1f))
        } catch (e: Exception) { staging.deleteRecursively(); logs.error("runtime.install",e); throw e }
    }
    fun refreshDns() {
        runCatching {
            val cm=context.getSystemService(ConnectivityManager::class.java)
            val servers=cm.activeNetwork?.let { cm.getLinkProperties(it)?.dnsServers }.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') }
            val dest=File(root,"etc/resolv.conf")
            if (!java.nio.file.Files.isSymbolicLink(dest.toPath())) dest.writeText((servers.ifEmpty { listOf("1.1.1.1","8.8.8.8") }).joinToString("\n",postfix="\n") { "nameserver $it" })
        }.onFailure { logs.error("runtime.dns",it) }
    }
    fun arguments(p: Preferences): Array<String> {
        val args=mutableListOf("$nativeDir/libproot.so","--kill-on-exit","--link2symlink","-0","-r",root.path,"-b","/dev","-b","/proc","-b","/sys","-b","${temporary.path}:/tmp","-w",p.workingDirectory)
        val shared=context.getExternalFilesDir(null)
        if (shared!=null) args += listOf("-b","${shared.path}:/shared")
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED) args += listOf("-b","${Environment.getExternalStorageDirectory().path}:/sdcard")
        args += listOf("/usr/bin/env","-i","HOME=/root","USER=root","LOGNAME=root","SHELL=/bin/bash","HOSTNAME=derminal","LANG=C.UTF-8","TERM=xterm-256color","COLORTERM=truecolor","PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin","TMPDIR=/tmp","/bin/bash","--login")
        return args.toTypedArray()
    }
    fun environment(p: Preferences): Array<String> = buildList {
        add("PATH=/system/bin"); add("HOME=${context.filesDir}"); add("TMPDIR=${temporary.path}")
        add("LD_LIBRARY_PATH=$nativeDir"); add("PROOT_LOADER=$nativeDir/libproot-loader.so"); add("PROOT_TMP_DIR=${temporary.path}")
        if (!p.seccomp) add("PROOT_NO_SECCOMP=1")
    }.toTypedArray()
}
