package app.derminal

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val engine get()=(application as DerminalApp).engine
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor=android.graphics.Color.TRANSPARENT
        window.navigationBarColor=android.graphics.Color.TRANSPARENT
        if(Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
        WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true }
        startForegroundService(Intent(this,TerminalService::class.java))
        setContent { Derminal(engine) }
    }
    override fun onResume() { super.onResume();engine.scope.launch(Dispatchers.IO) { if(engine.boot.value.ready)engine.runtime.refreshDns() };engine.view?.setTerminalCursorBlinkerState(true,true) }
    override fun onPause() { engine.view?.setTerminalCursorBlinkerState(false,false);super.onPause() }
    fun screenOn(value: Boolean) { if(value)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
}

@Composable fun Derminal(engine: TerminalEngine) {
    val boot by engine.boot.collectAsStateWithLifecycle()
    val p=engine.settings.value
    val context=LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<Tab?>(null) }
    var renaming by remember { mutableStateOf<Tab?>(null) }
    var searching by remember { mutableStateOf(false) }
    val ime=WindowInsets.ime.getBottom(LocalDensity.current)>0
    SideEffect { (context as? MainActivity)?.screenOn(p.keepScreen) }
    BackHandler(enabled=engine.modifiers.ctrl>0 || engine.modifiers.alt>0 || engine.modifiers.shift>0 || engine.modifiers.fn>0) { engine.modifiers.clear() }
    Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        Row(Modifier.fillMaxWidth().height(51.dp).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
            val rowState=androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(engine.selected) { val index=engine.tabs.indexOfFirst { it.id==engine.selected };if(index>=0)rowState.animateScrollToItem(index) }
            LazyRow(Modifier.weight(1f),state=rowState,horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                items(engine.tabs,key={it.id}) { tab ->
                    val selected=engine.selected==tab.id
                    Row(Modifier.height(37.dp).widthIn(min=92.dp,max=195.dp).clip(RoundedCornerShape(9.dp)).background(if(selected)Wash else Color.Transparent).combinedClickable(onClick={engine.select(tab)},onLongClick={renaming=tab}).padding(start=11.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(if(tab.exited)Color(0xFFA4A8AE)else if(tab.unread)Accent else if(selected)Ink else Color(0xFF9AA0A7),RoundedCornerShape(5.dp)))
                        Spacer(Modifier.width(7.dp));Label(tab.name,Modifier.weight(1f,fill=false),size=13,weight=if(selected)FontWeight.SemiBold else FontWeight.Normal,maxLines=1)
                        IconButton("close","关闭 ${tab.name}",Modifier.size(33.dp),color=Muted) { closing=tab }
                    }
                }
            }
            IconButton("plus","新建终端",onClick={engine.newTab()})
            if(!ime && !p.extraAlways)IconButton("settings","设置",onClick={settings=true})
        }
        Divider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !boot.ready -> Bootstrap(boot,onRetry={engine.prepare()})
                engine.active!=null -> TerminalSurface(engine)
                else -> Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) { Glyph("terminal",Modifier.size(32.dp),Muted);Action("新建终端",primary=true) { engine.newTab() } }
            }
            if(engine.recovered && boot.ready) Row(Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Wash).border(1.dp,Line,RoundedCornerShape(10.dp)).padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) { Label("已恢复标签，会话已重新启动",size=12,color=Muted);IconButton("close","关闭提示",Modifier.size(32.dp)) {engine.recovered=false} }
            engine.active?.takeIf { it.exited }?.let { tab -> Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp,Line,RoundedCornerShape(12.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { Label("会话已结束  ${tab.status}",size=12,color=Muted);Action("重新打开") { val name=tab.name;engine.close(tab);engine.newTab(name) } } }
            androidx.compose.animation.AnimatedVisibility(engine.toast!=null,Modifier.align(Alignment.BottomCenter).padding(16.dp),enter=fadeIn(),exit=fadeOut()) { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Ink).padding(horizontal=16.dp,vertical=10.dp)) { Label(engine.toast.orEmpty(),size=13,color=Color.White) } }
        }
        if(ime || p.extraAlways) KeyDeck(engine,onSettings={settings=true})
    }
    if(settings) SettingsDialog(engine,onDismiss={settings=false},onSearch={settings=false;searching=true})
    closing?.let { tab -> ConfirmDialog("关闭“${tab.name}”？",if(tab.exited)"此标签中的终端记录将被关闭。"else"正在运行的命令会终止，此操作无法撤销。","关闭会话",destructive=true,onDismiss={closing=null}) { engine.close(tab);closing=null } }
    renaming?.let { tab ->
        var name by remember(tab.id) { mutableStateOf(tab.name) }
        SheetDialog("标签名称",onDismiss={renaming=null}) {
            Field(name,{name=it.take(40)},hint="终端名称")
            Spacer(Modifier.height(8.dp));Label(tab.title.ifBlank { "长按标签可再次修改名称" },size=12,color=Muted,maxLines=2)
            Spacer(Modifier.height(20.dp));Action("保存",Modifier.fillMaxWidth(),primary=true,enabled=name.isNotBlank()) { engine.rename(tab,name);renaming=null }
        }
    }
    engine.pasteRequest?.let { text -> SheetDialog("粘贴多行文本",onDismiss={engine.pasteRequest=null}) {
        Label("文本包含换行或控制字符，可能立即执行命令。",size=13,color=Muted)
        Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().heightIn(max=180.dp).background(Wash,RoundedCornerShape(8.dp)).padding(12.dp).verticalScroll(rememberScrollState())) { Label(text.take(3000),size=12,mono=true) }
        Spacer(Modifier.height(16.dp));Action("粘贴到终端",Modifier.fillMaxWidth(),primary=true) {engine.paste(text)}
    } }
    if(searching) TranscriptDialog(engine) {searching=false}
}

@Composable private fun Bootstrap(state: BootState,onRetry: ()->Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        Glyph("terminal",Modifier.size(30.dp));Spacer(Modifier.height(24.dp))
        Label(if(state.error==null)state.message else "无法准备 Debian",weight=FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        if(state.error!=null) {Label(state.error,size=13,color=Muted);Spacer(Modifier.height(16.dp));Action("重试",onClick=onRetry)}
        else {Box(Modifier.width(200.dp).height(3.dp).background(Line,RoundedCornerShape(3.dp))) {Box(Modifier.fillMaxWidth(state.progress.coerceIn(.01f,1f)).fillMaxHeight().background(Ink,RoundedCornerShape(3.dp)))};Spacer(Modifier.height(12.dp));Label("首次展开系统，完成后即可离线使用",size=12,color=Muted)}
    }
}

@Composable private fun TerminalSurface(engine: TerminalEngine) {
    val p=engine.settings.value
    val palette=palettes.firstOrNull {it.id==p.theme} ?: palettes.first()
    val bg=remember(p.theme,p.customBackground) { runCatching {Color(android.graphics.Color.parseColor(p.customBackground.ifBlank {palette.background}))}.getOrDefault(Color.White) }
    val image by produceState<Bitmap?>(null,p.background,engine.assetsVersion) { value=if(p.background) withContext(Dispatchers.IO) {BitmapFactory.decodeFile(File(engine.context.filesDir,"background.jpg").path)} else null }
    Box(Modifier.fillMaxSize().background(bg)) {
        if(p.background && image!=null) { Image(image!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(bg.copy(alpha=p.backgroundDim/100f))) }
        AndroidView(factory={ context -> TerminalView(context,null).apply { setTerminalViewClient(ViewClient(engine,this));isFocusable=true;isFocusableInTouchMode=true;setIsTerminalViewKeyLoggingEnabled(false);setBackgroundColor(android.graphics.Color.TRANSPARENT);engine.attach(this);requestFocus() } },modifier=Modifier.fillMaxSize().padding(horizontal=7.dp,vertical=5.dp),update={v->engine.attach(v)},onRelease={v->engine.detach(v)})
        LaunchedEffect(engine.selected) {
            if(p.autoKeyboard && engine.active?.exited==false) {delay(180);engine.view?.let {it.requestFocus();it.context.getSystemService(InputMethodManager::class.java).showSoftInput(it,InputMethodManager.SHOW_IMPLICIT)}}
        }
    }
}

@Composable private fun KeyDeck(engine: TerminalEngine,onSettings: ()->Unit) {
    val p=engine.settings.value
    val context=LocalContext.current
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(p.row1,p.row2).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        KeyInput.parse(row).forEach { key ->
                            val state=engine.modifiers.state(key)
                            val shown=if(engine.modifiers.fn>0)mapOf("ESC" to "F1","TAB" to "F2","HOME" to "F3","↑" to "PgUp","END" to "F4","/" to "F5","-" to "F6","←" to "Home","↓" to "PgDn","→" to "End")[key] ?: key else key
                            val shape=RoundedCornerShape(7.dp)
                            val basic=Modifier.weight(1f).height(p.keyHeight.dp).clip(shape).background(if(state>0)Ink else Wash).border(1.dp,if(state==2)Accent else if(state>0)Ink else Line,shape).semantics {contentDescription=key + if(state==2)" 已锁定"else if(state==1)" 已启用"else""}
                            val tap={if(p.keyHaptic)engine.view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);engine.send(key)}
                            val repeat=key in listOf("↑","↓","←","→","BKSP","DEL","PGUP","PGDN")
                            Box(basic.then(if(repeat)Modifier.pointerInput(key) {detectTapGestures(onPress={coroutineScope {tap();val job=launch {delay(380);while(true){tap();delay(65)}};try{tryAwaitRelease()}finally{job.cancel()}}})}else Modifier.combinedClickable(onClick=tap,onLongClick={if(key in listOf("CTRL","ALT","SHIFT","FN"))engine.modifiers.toggle(key,true)else tap()})),contentAlignment=Alignment.Center) {
                                Label(shown,size=if(shown.length>4)10 else 12,color=if(state>0)Color.White else Ink,weight=FontWeight.Medium,maxLines=1)
                                if(state==2)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).size(3.dp).background(Color.White,RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                IconButton("settings","设置",Modifier.size(42.dp,p.keyHeight.dp),onClick=onSettings)
                IconButton("keyboard","显示或收起键盘",Modifier.size(42.dp,p.keyHeight.dp)) { engine.view?.let { v->val imm=context.getSystemService(InputMethodManager::class.java);if(keyboardVisible)imm.hideSoftInputFromWindow(v.windowToken,0);else {v.requestFocus();imm.showSoftInput(v,InputMethodManager.SHOW_IMPLICIT)} } }
            }
        }
    }
}

@Composable fun SheetDialog(title: String,onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth(.92f).widthIn(max=560.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(22.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Label(title,Modifier.weight(1f),size=18,weight=FontWeight.SemiBold);IconButton("close","关闭",onClick=onDismiss)}
            Spacer(Modifier.height(16.dp));content()
        }
    }
}

@Composable fun ConfirmDialog(title: String,message: String,action: String,destructive: Boolean=false,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    SheetDialog(title,onDismiss) {Label(message,size=14,color=Muted);Spacer(Modifier.height(24.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {Action("取消",Modifier.weight(1f),onClick=onDismiss);Action(action,Modifier.weight(1f),primary=true,destructive=destructive,onClick=onConfirm)}}
}

fun shareFile(context: android.content.Context,file: File,mime: String="text/plain") {
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"导出文件"))
}
