package app.derminal

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun SettingsDialog(engine: TerminalEngine,onDismiss: ()->Unit,onSearch: ()->Unit) {
    val context=LocalContext.current
    val p=engine.settings.value
    val scope=rememberCoroutineScope()
    var section by remember { mutableStateOf("外观") }
    var editing by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun change(transform: (Preferences)->Preferences) { engine.settings.update(transform);engine.settingsChanged() }
    fun imported(uri: Uri?,kind: String) {
        if(uri==null)return
        scope.launch {
            busy=true;notice=null
            runCatching { withContext(Dispatchers.IO) {
                when(kind) {
                    "background"->{
                        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,options)}
                        require(options.outWidth>0 && options.outHeight>0){"无法读取此图片"}
                        var sample=1;while(options.outWidth/sample>2200 || options.outHeight/sample>2200)sample*=2
                        val bitmap=context.contentResolver.openInputStream(uri)!!.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})} ?: error("无法读取此图片")
                        val staging=File(context.filesDir,"background.tmp")
                        staging.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
                        check(staging.renameTo(File(context.filesDir,"background.jpg")))
                    }
                    "font"->{
                        val staging=File(context.filesDir,"font.tmp")
                        context.contentResolver.openInputStream(uri)!!.use {input->staging.outputStream().use {output->val buffer=ByteArray(32768);var count=0L;while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=20*1024*1024){"字体不能超过 20 MB"};output.write(buffer,0,n)}}}
                        Typeface.createFromFile(staging)
                        check(staging.renameTo(File(context.filesDir,"custom-font")))
                    }
                    else->{
                        val display=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst())cursor.getString(0)else null} ?: "imported-file"
                        val filename=display.substringAfterLast('/').substringAfterLast('\\').take(160).ifBlank{"imported-file"}
                        require(filename!="." && filename!="..")
                        val shared=context.getExternalFilesDir(null) ?: error("共享目录暂不可用")
                        val staging=File(shared,".import-${System.nanoTime()}")
                        try {
                            context.contentResolver.openInputStream(uri)!!.use {input->staging.outputStream().use {output->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;check(shared.usableSpace>16*1024*1024){"存储空间不足"};output.write(buffer,0,n)}}}
                            var destination=File(shared,filename)
                            if(destination.exists())destination=File(shared,"${System.currentTimeMillis()}-$filename")
                            check(staging.renameTo(destination));"已导入 /shared/${destination.name}"
                        } finally {staging.delete()}
                    }
                }
            } }.onSuccess {result->when(kind){"background"->{engine.assetsVersion++;change{it.copy(background=true)}};"font"->change{it.copy(font="custom")};else->notice=result.toString()}}.onFailure {engine.logs.error("settings.import",it);notice=it.message ?: "导入失败"}
            busy=false
        }
    }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){imported(it,"background")}
    val fontPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){imported(it,"font")}
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){imported(it,"file")}
    val storage=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->notice=if(result.values.any {it})"授权成功，新会话中可访问 /sdcard"else"未获得存储权限，仍可通过文件选择器导入"}
    val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){notice=if(it)"已允许会话通知"else"可在系统设置中开启通知"}
    Dialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.widthIn(max=620.dp).fillMaxWidth(.94f).fillMaxHeight(.86f).shadow(24.dp,RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Surface).border(.5.dp,Line,RoundedCornerShape(20.dp))) {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=12.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {Label("设置",Modifier.weight(1f),size=18,weight=FontWeight.SemiBold);CompactClose("关闭设置",onClick=onDismiss)}
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                listOf("外观","输入","会话","系统").forEach {title->Box(Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(topStart=7.dp,topEnd=7.dp)).clickable{section=title},contentAlignment=Alignment.Center){
                    Label(title,size=13,color=if(section==title)Ink else Muted,weight=if(section==title)FontWeight.Medium else FontWeight.Normal)
                    if(section==title)Box(Modifier.align(Alignment.BottomCenter).width(19.dp).height(2.dp).background(Accent,RoundedCornerShape(2.dp)))
                }}
            }
            Divider()
            key(section) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=22.dp)) {
                    when(section) {
                        "外观"->{
                            Caption("配色")
                            palettes.chunked(2).forEach {row->Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach {theme->
                                val bg=Color(android.graphics.Color.parseColor(theme.background));val fg=Color(android.graphics.Color.parseColor(theme.foreground))
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(bg).border(if(p.theme==theme.id)1.5.dp else .5.dp,if(p.theme==theme.id)Accent else Line,RoundedCornerShape(9.dp)).clickable{change{it.copy(theme=theme.id,customForeground="",customBackground="")}}.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Label(theme.name,Modifier.weight(1f),size=12,color=fg);if(p.theme==theme.id)Glyph("check",Modifier.size(14.dp),fg)}
                                    Spacer(Modifier.height(8.dp));Label("~ $ ls -la",size=12,color=fg,mono=true)
                                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){theme.ansi.drop(1).take(6).forEach{Box(Modifier.padding(top=9.dp).size(9.dp,3.dp).background(Color(android.graphics.Color.parseColor(it)),RoundedCornerShape(2.dp)))}}
                                }
                            }}}
                            SettingRow("自定义颜色",subtitle="文字和背景，支持十六进制色值",onClick={editing="colors"}){Glyph("chevron",color=Muted)}
                            Divider()
                            Caption("字体")
                            NumberSetting("字号",p.fontSize,9..32,"sp"){n->change{it.copy(fontSize=n)}}
                            Choice(listOf("system" to "系统等宽","serif" to "衬线等宽","custom" to "导入字体"),p.font){id->if(id=="custom")fontPicker.launch(arrayOf("*/*"))else change{it.copy(font=id)}}
                            ToggleRow("加粗文字",checked=p.bold){v->change{it.copy(bold=v)}}
                            Divider();Caption("背景")
                            SettingRow("背景图片",if(p.background)"已设置"else"未设置",onClick={imagePicker.launch(arrayOf("image/*"))}) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    if(p.background)TextAction("清除") {change{it.copy(background=false)};engine.assetsVersion++;scope.launch(Dispatchers.IO){File(context.filesDir,"background.jpg").delete()}}
                                    Glyph("chevron",Modifier.size(16.dp),Muted)
                                }
                            }
                            if(p.background) {
                                NumberSetting("背景遮罩",p.backgroundDim,20..100,"%",step=5){n->change{it.copy(backgroundDim=n)}}
                            }
                            Divider();Caption("光标")
                            Choice(listOf("0" to "方块","2" to "竖线","1" to "下划线"),p.cursor.toString()){v->change{it.copy(cursor=v.toInt())}}
                            ToggleRow("光标闪烁",checked=p.blink){v->change{it.copy(blink=v)}}
                        }
                        "输入"->{
                            Caption("功能键")
                            Label("轻点 Ctrl、Alt、Shift 启用一次，长按锁定。Fn 切换功能键和翻页键。",size=13,color=Muted)
                            ToggleRow("始终显示功能键","关闭后仅在键盘弹出时显示",p.extraAlways){v->change{it.copy(extraAlways=v)}}
                            NumberSetting("按键高度",p.keyHeight,32..56,"dp",step=2){n->change{it.copy(keyHeight=n)}}
                            ToggleRow("按键触感",checked=p.keyHaptic){v->change{it.copy(keyHaptic=v)}}
                            SettingRow("自定义双行按键",subtitle="设置排列、符号和 F1–F12",onClick={editing="keys"}) {Glyph("chevron",color=Muted)}
                            Divider();Caption("键盘")
                            ToggleRow("自动弹出键盘","进入会话时显示输入法",p.autoKeyboard){v->change{it.copy(autoKeyboard=v)}}
                            ToggleRow("音量减键作为 Ctrl","按住音量减键，同时输入字母",p.volumeCtrl){v->change{it.copy(volumeCtrl=v)}}
                            Divider();Caption("常用操作")
                            Label("Ctrl C 中断命令\nCtrl D 结束输入\nCtrl L 清屏\nCtrl R 搜索 Bash 历史\nCtrl A / E 移动到行首 / 行尾\nAlt B / F 按词移动\nTab 补全，方向键调出历史\n外接键盘 Ctrl Shift T 新建会话",size=13,color=Muted)
                        }
                        "会话"->{
                            Caption("当前会话")
                            SettingRow("粘贴",subtitle="多行文本会先确认",onClick={onDismiss();engine.requestPaste()}){Glyph("chevron",color=Muted)}
                            SettingRow("搜索与导出记录",subtitle="查找当前标签中的终端输出",onClick=onSearch){Glyph("search",color=Muted)}
                            SettingRow("复制屏幕",onClick={engine.active?.session?.emulator?.screen?.transcriptText?.let{engine.copy(it)}}){Glyph("copy",color=Muted)}
                            SettingRow("重置终端显示",subtitle="修复异常显示，正在运行的命令继续执行",onClick={engine.active?.session?.reset();engine.settingsChanged();notice="终端显示已重置"}){Glyph("chevron",color=Muted)}
                            Divider();Caption("新会话")
                            SettingRow("启动目录",p.workingDirectory,onClick={editing="directory"}){Glyph("chevron",color=Muted)}
                            SettingRow("启动命令",if(p.startup.isBlank())"未设置"else"已设置",onClick={editing="startup"}){Glyph("chevron",color=Muted)}
                            Caption("滚动记录行数")
                            Choice(listOf("2000" to "2 千","10000" to "1 万","50000" to "5 万"),p.scrollback.toString()){v->change{it.copy(scrollback=v.toInt())}}
                            Spacer(Modifier.height(8.dp));Label("应用于新会话。更多记录会占用更多内存。",size=12,color=Muted)
                            Divider();Caption("运行")
                            ToggleRow("保持屏幕常亮",checked=p.keepScreen){v->change{it.copy(keepScreen=v)}}
                            ToggleRow("保持 CPU 唤醒","供长时间任务使用，会增加耗电",p.wakeLock){v->change{it.copy(wakeLock=v)}}
                            ToggleRow("终端响铃触感",checked=p.bell){v->change{it.copy(bell=v)}}
                        }
                        "系统"->{
                            Caption("Debian 13 Trixie")
                            Label("ARM64  /  Bash  /  PRoot",size=13,mono=true,color=Muted)
                            Spacer(Modifier.height(10.dp));Label("内置 apt、Git、curl、SSH、nano、Vim 和 less。使用 apt update 与 apt upgrade 更新软件包。",size=13,color=Muted)
                            SettingRow("导入文件到 /shared",subtitle="通过系统文件选择器导入",onClick={filePicker.launch(arrayOf("*/*"))}){Glyph("download",color=Muted)}
                            SettingRow("访问手机存储",subtitle="授权后在新会话中使用 /sdcard",onClick={storage.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE))}){Glyph("chevron",color=Muted)}
                            if(Build.VERSION.SDK_INT>=33)SettingRow("会话通知",subtitle="在系统通知栏快速返回终端",onClick={notification.launch(Manifest.permission.POST_NOTIFICATIONS)}){Glyph("chevron",color=Muted)}
                            Divider();Caption("兼容性")
                            ToggleRow("系统调用加速","支持的内核使用 seccomp 减少跟踪开销，切换后新会话生效",p.seccomp){v->change{it.copy(seccomp=v)}}
                            Label("若会话启动失败或被系统停止，可关闭加速后重开。PRoot 不提供独立内核，无法运行 Docker 或 systemd。",size=12,color=Muted)
                            Divider();Caption("诊断与数据")
                            SettingRow("导出诊断日志",subtitle="本地轮转日志，不记录命令或终端内容",onClick={scope.launch{runCatching{withContext(Dispatchers.IO){engine.logs.export(context)}}.onSuccess{shareFile(context,it)}.onFailure{notice="无法导出日志"}}}){Glyph("chevron",color=Muted)}
                            SettingRow("恢复默认设置",subtitle="保留 Debian 文件和已安装的软件",onClick={reset=true}){Glyph("chevron",color=Muted)}
                            Divider();Caption("derminal ${BuildConfig.VERSION_NAME}")
                            Label("终端核心来自 Termux，采用 Apache 2.0 协议。PRoot 及依赖的源代码、许可证和构建说明随项目发布。",size=12,color=Muted)
                            SettingRow("开源许可",onClick={editing="licenses"}){Glyph("chevron",color=Muted)}
                        }
                    }
                }
            }
            if(busy || notice!=null) {Divider();Label(if(busy)"正在导入…"else notice.orEmpty(),Modifier.padding(horizontal=22.dp,vertical=12.dp),size=12,color=Muted,maxLines=3)}
        }
    }
    if(reset)ConfirmDialog("恢复默认设置？","背景、配色、字体及输入设置将恢复默认值。Debian 中的文件会保留。","恢复默认",onDismiss={reset=false}) {change{Preferences()};reset=false}
    editing?.let {kind->
        when(kind) {
            "keys"->{
                var first by remember {mutableStateOf(p.row1)};var second by remember {mutableStateOf(p.row2)}
                SheetDialog("功能键排列",onDismiss={editing=null}) {
                    Label("每行 4 至 9 个按键，以空格分隔。设置键固定在右侧。",size=12,color=Muted)
                    Spacer(Modifier.height(12.dp));Field(first,{first=it},mono=true,error=!KeyInput.valid(first));Spacer(Modifier.height(8.dp));Field(second,{second=it},mono=true,error=!KeyInput.valid(second))
                    Spacer(Modifier.height(10.dp));Label("可用：ESC TAB CTRL ALT SHIFT FN HOME END PGUP PGDN DEL BKSP INS ENTER F1–F12 ↑ ↓ ← → 及常用符号",size=12,color=Muted)
                    Spacer(Modifier.height(16.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("默认排列",Modifier.weight(1f)){first=Preferences().row1;second=Preferences().row2};Action("保存",Modifier.weight(1f),primary=true,enabled=KeyInput.valid(first)&&KeyInput.valid(second)){change{it.copy(row1=first.trim(),row2=second.trim())};editing=null}}
                }
            }
            "colors"->{
                var fg by remember {mutableStateOf(p.customForeground)};var bg by remember {mutableStateOf(p.customBackground)}
                fun valid(s: String)=s.isBlank() || s.matches(Regex("#[0-9a-fA-F]{6}"))
                SheetDialog("自定义颜色",onDismiss={editing=null}) {Label("留空使用当前配色，格式为 #RRGGBB。",size=12,color=Muted);Caption("文字");Field(fg,{fg=it},hint="#22252A",mono=true,error=!valid(fg));Caption("背景");Field(bg,{bg=it},hint="#FFFFFF",mono=true,error=!valid(bg));Spacer(Modifier.height(18.dp));Action("保存",Modifier.fillMaxWidth(),primary=true,enabled=valid(fg)&&valid(bg)){change{it.copy(customForeground=fg,customBackground=bg)};editing=null}}
            }
            "directory","startup"->{
                var value by remember {mutableStateOf(if(kind=="directory")p.workingDirectory else p.startup)}
                val valid=if(kind=="directory")value.startsWith('/') && !value.contains('\u0000') && !value.contains('\n')else !value.contains('\u0000') && value.length<=4000
                SheetDialog(if(kind=="directory")"启动目录"else"启动命令",onDismiss={editing=null}) {
                    Label(if(kind=="directory")"输入 Debian 中已存在的绝对路径。"else"每次新建会话时执行，留空则直接进入 Bash。",size=12,color=Muted)
                    Spacer(Modifier.height(12.dp));Field(value,{value=it},modifier=Modifier.heightIn(max=180.dp),singleLine=kind=="directory",mono=true,error=!valid)
                    Spacer(Modifier.height(18.dp));Action("保存",Modifier.fillMaxWidth(),primary=true,enabled=valid){change{if(kind=="directory")it.copy(workingDirectory=value.trim())else it.copy(startup=value)};editing=null}
                }
            }
            "licenses"->SheetDialog("开源许可",onDismiss={editing=null}) {
                val text=remember{runCatching{context.assets.open("licenses/NOTICE.txt").bufferedReader().use{it.readText()}}.getOrDefault("详见项目 THIRD_PARTY.md")}
                Box(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){Label(text,size=12,color=Muted)}
            }
        }
    }
}

@Composable private fun NumberSetting(title: String,value: Int,range: IntRange,unit: String,step: Int=1,onChange: (Int)->Unit) {
    SettingRow(title) {Row(Modifier.height(34.dp).clip(RoundedCornerShape(8.dp)).background(Wash).border(.5.dp,Line,RoundedCornerShape(8.dp)),verticalAlignment=Alignment.CenterVertically) {
        StepButton("−",value>range.first){onChange((value-step).coerceIn(range))}
        Box(Modifier.widthIn(min=53.dp).padding(horizontal=3.dp),contentAlignment=Alignment.Center) {Label("$value $unit",size=12,mono=true)}
        StepButton("+",value<range.last){onChange((value+step).coerceIn(range))}
    }}
}

@Composable private fun StepButton(text: String,enabled: Boolean,onClick: ()->Unit) {
    val source=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    Box(Modifier.size(32.dp,34.dp).clip(RoundedCornerShape(7.dp)).background(if(pressed)LocalUiColors.current.pressed else Color.Transparent).clickable(source,null,enabled=enabled,onClick=onClick),contentAlignment=Alignment.Center) {Label(text,size=17,color=if(enabled)Ink else Muted.copy(alpha=.45f),weight=FontWeight.Normal)}
}
