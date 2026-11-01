package app.derminal

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.lang.ref.WeakReference

class DerminalApp : Application() {
    lateinit var engine: TerminalEngine
    override fun onCreate() {
        super.onCreate(); engine=TerminalEngine(this)
        engine.logs.event("app.start","version=${BuildConfig.VERSION_NAME} api=${android.os.Build.VERSION.SDK_INT}")
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error -> engine.logs.crash(error); previous?.uncaughtException(thread,error) }
    }
}

class Tab(val session: TerminalSession, name: String) {
    val id = session.mHandle
    var name by mutableStateOf(name)
    var title by mutableStateOf("")
    var exited by mutableStateOf(false)
    var status by mutableStateOf(0)
    var unread by mutableStateOf(false)
}

/** 0=off, 1=one shot, 2=locked. */
class Modifiers {
    var ctrl by mutableStateOf(0); var alt by mutableStateOf(0); var shift by mutableStateOf(0); var fn by mutableStateOf(0)
    var volume=false
    fun toggle(key: String, lock: Boolean = false) {
        fun next(v: Int) = if (lock) { if(v==2) 0 else 2 } else if(v==0) 1 else 0
        when(key) { "CTRL" -> ctrl=next(ctrl); "ALT" -> alt=next(alt); "SHIFT" -> shift=next(shift); "FN" -> fn=next(fn) }
    }
    fun consume() { if(ctrl==1) ctrl=0; if(alt==1) alt=0; if(shift==1) shift=0; if(fn==1) fn=0 }
    fun clear() { ctrl=0;alt=0;shift=0;fn=0;volume=false }
    fun state(key: String) = when(key) { "CTRL"->ctrl;"ALT"->alt;"SHIFT"->shift;"FN"->fn;else->0 }
    fun mask() = (if(ctrl>0 || volume) KeyHandler.KEYMOD_CTRL else 0) or (if(alt>0) KeyHandler.KEYMOD_ALT else 0) or (if(shift>0) KeyHandler.KEYMOD_SHIFT else 0)
}

class TerminalEngine(val context: Context) : TerminalSessionClient {
    val settings=PreferenceStore(context)
    val logs=Diagnostics(context)
    val runtime=LinuxRuntime(context,logs)
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val boot=MutableStateFlow(BootState())
    val tabs=mutableStateListOf<Tab>()
    var selected by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var pasteRequest by mutableStateOf<String?>(null)
    var recovered by mutableStateOf(false)
    var assetsVersion by mutableStateOf(0)
    val modifiers=Modifiers()
    private var viewRef=WeakReference<TerminalView>(null)
    val view: TerminalView? get() = viewRef.get()
    val active: Tab? get() = tabs.firstOrNull { it.id==selected }
    private var preparing=false
    private var framePending=false
    private val main=Handler(Looper.getMainLooper())
    var onSessionCountChanged: (() -> Unit)?=null
    fun prepare() {
        if(preparing || boot.value.ready) return
        preparing=true
        scope.launch {
            try {
                runtime.prepare { boot.value=it }
                val saved=runCatching { JSONArray(settings.disk.getString("tabs","[]")) }.getOrDefault(JSONArray())
                recovered=saved.length()>0
                if(saved.length()==0) newTab() else repeat(saved.length().coerceAtMost(12)) { newTab(saved.optString(it,"终端 ${it+1}"),persist=false) }
                selected=tabs.getOrNull(settings.disk.getInt("selected",0))?.id ?: tabs.firstOrNull()?.id
                persistTabs()
            } catch(e: Exception) { logs.error("runtime.prepare",e); boot.value=BootState(error=e.message ?: "准备失败，请重试") }
            finally { preparing=false }
        }
    }
    fun newTab(name: String?=null, persist: Boolean=true) {
        if(!boot.value.ready) return
        if(tabs.size>=12) { notify("最多可同时打开 12 个会话");return }
        val p=settings.value
        val session=TerminalSession("${runtime.nativeDir}/libproot.so",context.filesDir.path,runtime.arguments(p),runtime.environment(p),p.scrollback,this)
        val tab=Tab(session,name ?: "终端 ${tabs.size+1}")
        tabs.add(tab); selected=tab.id
        // Start even if the tab has not been laid out yet. PTY resizing follows attachment.
        try { session.updateSize(80,24,8,16);applyPalette(session) }
        catch(e: Exception) { session.finishIfRunning();tab.exited=true;tab.status=-1;logs.error("session.start",e);notify("无法创建终端，请导出诊断日志") }
        scope.launch(Dispatchers.IO) { runtime.refreshDns() }
        if(!tab.exited && p.startup.isNotBlank()) session.write(p.startup+"\r")
        logs.event("session.create","count=${tabs.size}")
        if(persist) persistTabs()
        onSessionCountChanged?.invoke()
    }
    fun select(tab: Tab) { selected=tab.id;tab.unread=false;modifiers.clear();persistTabs() }
    fun rename(tab: Tab,name: String) { tab.name=name.trim().take(40).ifBlank { "终端" };persistTabs() }
    fun close(tab: Tab) {
        val index=tabs.indexOf(tab)
        if(index<0) return
        tab.session.finishIfRunning();tabs.remove(tab)
        if(selected==tab.id) selected=tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id
        modifiers.clear();persistTabs();onSessionCountChanged?.invoke()
        logs.event("session.close","count=${tabs.size}")
    }
    fun closeAll() { tabs.toList().forEach { close(it) } }
    private fun persistTabs() {
        settings.disk.edit().putString("tabs",JSONArray(tabs.map { it.name }).toString()).putInt("selected",tabs.indexOfFirst { it.id==selected }.coerceAtLeast(0)).apply()
    }
    fun attach(v: TerminalView) { viewRef=WeakReference(v); applyView(v) }
    fun detach(v: TerminalView) { if(view===v) viewRef.clear(); v.setTerminalCursorBlinkerState(false,false) }
    fun applyView(v: TerminalView) {
        val p=settings.value
        val fontFile=File(context.filesDir,"custom-font")
        val base=if(p.font=="custom" && fontFile.exists()) runCatching { Typeface.createFromFile(fontFile) }.getOrDefault(Typeface.MONOSPACE) else Typeface.create(if(p.font=="serif") "serif-monospace" else "monospace",Typeface.NORMAL)
        val typeface=if(p.bold) Typeface.create(base,Typeface.BOLD) else base
        // TerminalView creates its renderer in setTextSize; setTypeface requires it.
        val px=(p.fontSize * context.resources.displayMetrics.scaledDensity).toInt()
        if(v.contentDescription != "终端 ${p.fontSize}") { v.setTextSize(px);v.contentDescription="终端 ${p.fontSize}" }
        if(v.tag != "${p.font}:${p.bold}:${fontFile.lastModified()}") { v.setTypeface(typeface);v.tag="${p.font}:${p.bold}:${fontFile.lastModified()}" }
        v.setTerminalCursorBlinkerRate(if(p.blink) 600 else 0)
        active?.let { tab -> if(v.currentSession!==tab.session) v.attachSession(tab.session) }
        v.syncBackground(p.background)
        v.setTerminalCursorBlinkerState(true,true)
    }
    fun applyPalette(session: TerminalSession) {
        val p=settings.value
        val palette=palettes.firstOrNull { it.id==p.theme } ?: palettes.first()
        fun color(custom: String,default: String) = runCatching { Color.parseColor(custom.ifBlank { default }) }.getOrElse { Color.parseColor(default) }
        val colors=TerminalColors.COLOR_SCHEME.mDefaultColors
        palette.ansi.forEachIndexed { i,c -> colors[i]=Color.parseColor(c) }
        colors[TextStyle.COLOR_INDEX_FOREGROUND]=color(p.customForeground,palette.foreground)
        colors[TextStyle.COLOR_INDEX_BACKGROUND]=color(p.customBackground,palette.background)
        colors[TextStyle.COLOR_INDEX_CURSOR]=Color.parseColor(palette.cursor)
        session.emulator?.let { it.mColors.reset();it.setCursorStyle() }
    }
    fun settingsChanged() { tabs.forEach { applyPalette(it.session) };view?.let { applyView(it);it.invalidate() };onSessionCountChanged?.invoke() }
    fun notify(message: String) { toast=message;main.removeCallbacks(clearToast);main.postDelayed(clearToast,3200) }
    private val clearToast=Runnable { toast=null }
    fun send(key: String) {
        val session=active?.takeUnless { it.exited }?.session ?: return
        if(key in listOf("CTRL","ALT","SHIFT","FN")) { modifiers.toggle(key);return }
        val fn=modifiers.fn>0
        val mapped=if(fn) mapOf("ESC" to "F1","TAB" to "F2","HOME" to "F3","↑" to "PGUP","END" to "F4","/" to "F5","-" to "F6","←" to "HOME","↓" to "PGDN","→" to "END")[key] ?: key else key
        val keyCode=KeyInput.keyCode(mapped)
        val em=session.emulator
        if(keyCode!=null && em!=null) KeyHandler.getCode(keyCode,modifiers.mask(),em.isCursorKeysApplicationMode,em.isKeypadApplicationMode)?.let { session.write(it) }
        else key.codePoints().toArray().forEach { cp ->
            var value=cp
            if(modifiers.shift>0) value=Character.toUpperCase(value)
            if(modifiers.ctrl>0 || modifiers.volume) value=KeyInput.control(value)
            session.writeCodePoint(modifiers.alt>0,value)
        }
        modifiers.consume()
    }
    fun requestPaste() {
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if(text.isEmpty()) { notify("剪贴板为空");return }
        if(text.length>100000) { notify("文本过长，请通过文件导入");return }
        if(text.any { it=='\n' || it=='\r' || it=='\u001b' }) pasteRequest=text else paste(text)
    }
    fun paste(text: String) { active?.session?.emulator?.paste(text);pasteRequest=null }
    fun copy(text: String) { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("derminal",text));notify("已复制") }
    override fun onTextChanged(changedSession: TerminalSession) {
        if(changedSession.mHandle!=selected) { tabs.firstOrNull { it.session===changedSession }?.unread=true;return }
        val v=view ?: return
        if(!framePending) { framePending=true;v.postOnAnimation { framePending=false;if(view===v) v.onScreenUpdated() } }
    }
    override fun onTitleChanged(changedSession: TerminalSession) { tabs.firstOrNull { it.session===changedSession }?.title=changedSession.title.orEmpty().take(160) }
    override fun onSessionFinished(finishedSession: TerminalSession) {
        tabs.firstOrNull { it.session===finishedSession }?.let { it.exited=true;it.status=finishedSession.exitStatus }
        onSessionCountChanged?.invoke();logs.event("session.exit","status=${finishedSession.exitStatus}")
    }
    override fun onCopyTextToClipboard(session: TerminalSession,text: String) { copy(text) }
    override fun onPasteTextFromClipboard(session: TerminalSession?) { requestPaste() }
    override fun onBell(session: TerminalSession) { if(settings.value.bell) view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    override fun onColorsChanged(session: TerminalSession) {
        if(session.mHandle==selected) view?.let {it.syncBackground(settings.value.background);it.invalidate()}
    }
    override fun onTerminalCursorStateChange(state: Boolean) { view?.setTerminalCursorBlinkerState(state,false) }
    override fun setTerminalShellPid(session: TerminalSession,pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int = settings.value.cursor
    override fun logError(tag: String,message: String) { logs.event("terminal.error",tag) }
    override fun logWarn(tag: String,message: String) { logs.event("terminal.warn",tag) }
    override fun logInfo(tag: String,message: String) = Unit
    override fun logDebug(tag: String,message: String) = Unit
    override fun logVerbose(tag: String,message: String) = Unit
    override fun logStackTraceWithMessage(tag: String,message: String,e: Exception) { logs.error(tag,e) }
    override fun logStackTrace(tag: String,e: Exception) { logs.error(tag,e) }
}

class ViewClient(private val engine: TerminalEngine,private val terminal: TerminalView) : TerminalViewClient {
    private val m get()=engine.modifiers
    override fun onScale(scale: Float): Float {
        if(scale<.9f || scale>1.1f) { engine.settings.update { it.copy(fontSize=(it.fontSize+if(scale>1) 1 else -1).coerceIn(9,32)) };engine.settingsChanged();return 1f }
        return scale
    }
    override fun onSingleTapUp(e: MotionEvent) { terminal.requestFocus();terminal.context.getSystemService(InputMethodManager::class.java).showSoftInput(terminal,InputMethodManager.SHOW_IMPLICIT) }
    override fun shouldBackButtonBeMappedToEscape()=false
    override fun shouldEnforceCharBasedInput()=true
    override fun shouldUseCtrlSpaceWorkaround()=true
    override fun isTerminalViewSelected()=true
    override fun copyModeChanged(copyMode: Boolean)=Unit
    override fun onKeyDown(keyCode: Int,e: KeyEvent,session: TerminalSession): Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_DOWN && engine.settings.value.volumeCtrl) { m.volume=true;return true }
        if(e.isCtrlPressed && e.isShiftPressed) when(keyCode) {
            KeyEvent.KEYCODE_V -> { engine.requestPaste();return true }
            KeyEvent.KEYCODE_C -> { terminal.selectedText?.let { engine.copy(it) };return true }
            KeyEvent.KEYCODE_T -> { engine.newTab();return true }
        }
        return false
    }
    override fun onKeyUp(keyCode: Int,e: KeyEvent): Boolean { if(keyCode==KeyEvent.KEYCODE_VOLUME_DOWN && engine.settings.value.volumeCtrl) { m.volume=false;return true };return false }
    override fun onLongPress(event: MotionEvent)=false
    // TerminalView samples each modifier once per key, including IME input.
    override fun readControlKey(): Boolean { val on=m.ctrl>0 || m.volume;if(m.ctrl==1)m.ctrl=0;return on }
    override fun readAltKey(): Boolean { val on=m.alt>0;if(m.alt==1)m.alt=0;return on }
    override fun readShiftKey(): Boolean { val on=m.shift>0;if(m.shift==1)m.shift=0;return on }
    override fun readFnKey()=false
    override fun onCodePoint(codePoint: Int,ctrlDown: Boolean,session: TerminalSession): Boolean {
        if(m.fn>0) { m.consume();if(codePoint in '1'.code..'9'.code) { engine.send("F${codePoint-'0'.code}");return true } }
        return false
    }
    override fun onEmulatorSet() { engine.active?.let { engine.applyPalette(it.session) } }
    override fun logError(tag: String,message: String) { engine.logError(tag,message) }
    override fun logWarn(tag: String,message: String) { engine.logWarn(tag,message) }
    override fun logInfo(tag: String,message: String)=Unit
    override fun logDebug(tag: String,message: String)=Unit
    override fun logVerbose(tag: String,message: String)=Unit
    override fun logStackTraceWithMessage(tag: String,message: String,e: Exception) { engine.logs.error(tag,e) }
    override fun logStackTrace(tag: String,e: Exception) { engine.logs.error(tag,e) }
}

object KeyInput {
    fun control(cp: Int): Int = when(cp) { in 64..95 -> cp-64;in 97..122 -> cp-96;32->0;63->127;else->cp }
    fun keyCode(key: String): Int? = when(key) {
        "ESC"->KeyEvent.KEYCODE_ESCAPE;"TAB"->KeyEvent.KEYCODE_TAB;"↑"->KeyEvent.KEYCODE_DPAD_UP;"↓"->KeyEvent.KEYCODE_DPAD_DOWN;"←"->KeyEvent.KEYCODE_DPAD_LEFT;"→"->KeyEvent.KEYCODE_DPAD_RIGHT
        "HOME"->KeyEvent.KEYCODE_MOVE_HOME;"END"->KeyEvent.KEYCODE_MOVE_END;"PGUP"->KeyEvent.KEYCODE_PAGE_UP;"PGDN"->KeyEvent.KEYCODE_PAGE_DOWN;"DEL"->KeyEvent.KEYCODE_FORWARD_DEL;"BKSP"->KeyEvent.KEYCODE_DEL;"INS"->KeyEvent.KEYCODE_INSERT;"ENTER"->KeyEvent.KEYCODE_ENTER
        else->key.removePrefix("F").toIntOrNull()?.takeIf { key.startsWith("F") && it in 1..12 }?.let { KeyEvent.KEYCODE_F1+it-1 }
    }
    val allowed=(listOf("ESC","TAB","CTRL","ALT","SHIFT","FN","HOME","END","PGUP","PGDN","DEL","BKSP","INS","ENTER","↑","↓","←","→","/","-","|","~","_","=","+","[","]","{","}","\\","$","`",":",";",".",",","?","!","&","*","(",")")+(1..12).map { "F$it" }).toSet()
    fun parse(row: String): List<String> = row.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun valid(row: String) = parse(row).let { it.size in 4..9 && it.all { k -> k in allowed } }
}
