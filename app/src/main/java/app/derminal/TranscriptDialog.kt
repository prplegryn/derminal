package app.derminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun TranscriptDialog(engine: TerminalEngine,onDismiss: ()->Unit) {
    val snapshot=remember {engine.active?.session?.emulator?.screen?.transcriptText.orEmpty()}
    val lines=remember {snapshot.lines()}
    var query by remember {mutableStateOf("")}
    var current by remember {mutableIntStateOf(0)}
    var status by remember {mutableStateOf<String?>(null)}
    val matches=remember(query) {if(query.isBlank())emptyList()else lines.mapIndexedNotNull{i,line->i.takeIf{line.contains(query,true)}}}
    val list=rememberLazyListState()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch{runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(snapshot)}}}.onSuccess{status="已导出记录"}.onFailure{status="导出失败";engine.logs.error("transcript.export",it)}}}
    LaunchedEffect(query){current=0}
    LaunchedEffect(current,matches){if(matches.isNotEmpty())list.animateScrollToItem(matches[current.coerceIn(matches.indices)])}
    SheetDialog("终端记录",onDismiss) {
        Field(query,{query=it},hint="搜索记录")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Label(if(query.isBlank())"${lines.size} 行"else if(matches.isEmpty())"无匹配"else"${current+1} / ${matches.size}",Modifier.weight(1f),size=12,color=Muted)
            if(matches.isNotEmpty()){Action("上一处"){current=(current-1+matches.size)%matches.size};Action("下一处"){current=(current+1)%matches.size}}
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            LazyColumn(Modifier.fillMaxWidth().heightIn(min=160.dp,max=340.dp).background(Wash),state=list) {
                itemsIndexed(lines){index,line->Label(line.ifEmpty{" "},Modifier.fillMaxWidth().background(if(index==matches.getOrNull(current))Accent.copy(alpha=.22f)else Color.Transparent).padding(horizontal=10.dp,vertical=2.dp),size=12,mono=true)}
            }
        }
        Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("复制全部",Modifier.weight(1f)){engine.copy(snapshot)};Action("导出文本",Modifier.weight(1f),primary=true){export.launch("derminal-transcript.txt")}}
        status?.let{Spacer(Modifier.height(8.dp));Label(it,size=12,color=Muted)}
    }
}
