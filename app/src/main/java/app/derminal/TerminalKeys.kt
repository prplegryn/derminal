package app.derminal

import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val keyNames=mapOf("ESC" to "Esc","TAB" to "Tab","CTRL" to "Ctrl","ALT" to "Alt","SHIFT" to "Shift","FN" to "Fn","HOME" to "Home","END" to "End","PGUP" to "PgUp","PGDN" to "PgDn","DEL" to "Del","BKSP" to "⌫","INS" to "Ins","ENTER" to "↵")
private val functionNames=mapOf("ESC" to "F1","TAB" to "F2","HOME" to "F3","↑" to "PGUP","END" to "F4","/" to "F5","-" to "F6","←" to "HOME","↓" to "PGDN","→" to "END")
private val modifierKeys=setOf("CTRL","ALT","SHIFT","FN")
private val repeatingKeys=setOf("↑","↓","←","→","BKSP","DEL","PGUP","PGDN")

@Composable fun KeyDeck(engine: TerminalEngine,onSettings: ()->Unit) {
    val p=engine.settings.value
    val context=LocalContext.current
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val enabled=engine.active?.exited==false
    Column(Modifier.fillMaxWidth().background(Color(0xFFF6F7F9))) {
        Box(Modifier.fillMaxWidth().height(.5.dp).background(Color(0xFFDFE3E8)))
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(p.row1,p.row2).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        KeyInput.parse(row).forEach { key ->
                            val shown=if(engine.modifiers.fn>0) functionNames[key] ?: key else key
                            val send={engine.send(key)}
                            TerminalKey(shown,Modifier.weight(1f).height(p.keyHeight.dp),state=engine.modifiers.state(key),enabled=enabled,repeat=key in repeatingKeys,
                                onClick={if(p.keyHaptic)engine.view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);send()},
                                onRepeat=send,onLock=if(key in modifierKeys)({engine.modifiers.toggle(key,true)})else null)
                        }
                    }
                }
            }
            Column(Modifier.width(35.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                TerminalKey("settings",Modifier.fillMaxWidth().height(p.keyHeight.dp),utility=true,onClick=onSettings)
                TerminalKey("keyboard",Modifier.fillMaxWidth().height(p.keyHeight.dp),utility=true,onClick={
                    engine.view?.let {v->val imm=context.getSystemService(InputMethodManager::class.java);if(keyboardVisible)imm.hideSoftInputFromWindow(v.windowToken,0)else{v.requestFocus();imm.showSoftInput(v,InputMethodManager.SHOW_IMPLICIT)}}
                })
            }
        }
    }
}

@Composable private fun TerminalKey(key: String,modifier: Modifier,state: Int=0,enabled: Boolean=true,repeat: Boolean=false,utility: Boolean=false,onClick: ()->Unit,onRepeat: ()->Unit=onClick,onLock: (()->Unit)?=null) {
    val source=remember {MutableInteractionSource()}
    val pressed by source.collectIsPressedAsState()
    val click by rememberUpdatedState(onClick)
    val tick by rememberUpdatedState(onRepeat)
    val shape=RoundedCornerShape(6.dp)
    val background=when {state==2 && pressed->Color(0xFF3B5262);state==2->Color(0xFF293D4A);state==1 && pressed->Color(0xFFCBDDE9);state==1->Color(0xFFE0E9F0);pressed->Color(0xFFE4E8ED);utility->Color(0xFFEDF0F4);else->Color.White}
    val foreground=when {state==2->Color.White;!enabled->Color(0xFFABB1BA);state==1->Color(0xFF28475C);else->Color(0xFF373F49)}
    val description=when(key){"settings"->"设置";"keyboard"->"显示或收起键盘";"↑"->"向上";"↓"->"向下";"←"->"向左";"→"->"向右";else->key} + when(state){1->" 已启用一次";2->" 已锁定";else->""}
    val interaction=if(repeat) {
        Modifier.semantics {role=Role.Button;contentDescription=description;if(!enabled)disabled();this.onClick {if(enabled)click();enabled}}
            .then(if(enabled)Modifier.pointerInput(key) {
                detectTapGestures(onPress={position->coroutineScope {
                    val event=PressInteraction.Press(position)
                    source.emit(event);click()
                    val job=launch {delay(380);while(true){tick();delay(65)}}
                    try {val released=tryAwaitRelease();source.emit(if(released)PressInteraction.Release(event)else PressInteraction.Cancel(event))}
                    finally {job.cancel();source.tryEmit(PressInteraction.Cancel(event))}
                }})
            }else Modifier)
    } else Modifier.combinedClickable(interactionSource=source,indication=null,enabled=enabled,role=Role.Button,onClick=onClick,onLongClick=onLock).semantics{contentDescription=description}
    Box(modifier.clip(shape).background(background).then(if(utility || state>0)Modifier else Modifier.border(.5.dp,Color(0xFFE3E7ED),shape)).then(interaction),contentAlignment=Alignment.Center) {
        when {
            key in listOf("↑","↓","←","→")->KeyArrow(key,foreground)
            key=="settings" || key=="keyboard"->Glyph(key,Modifier.size(17.dp),foreground)
            else->{
                val name=keyNames[key] ?: key
                val symbol=name.length==1
                BasicText(name,style=TextStyle(color=foreground,fontFamily=if(symbol)FontFamily.Monospace else FontFamily.SansSerif,fontSize=(if(symbol)15 else if(name.length>4)11 else 12).sp,lineHeight=16.sp,fontWeight=if(symbol)FontWeight.Normal else FontWeight.Medium,letterSpacing=if(symbol)0.sp else .1.sp,platformStyle=PlatformTextStyle(includeFontPadding=false)),maxLines=1,overflow=TextOverflow.Clip)
            }
        }
        if(state==2)Box(Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).width(7.dp).height(1.5.dp).background(Color(0xFFD9E6EF),RoundedCornerShape(2.dp)))
    }
}

@Composable private fun KeyArrow(direction: String,color: Color) {
    Canvas(Modifier.size(17.dp)) {
        val s=size.width/20f
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(color,Offset(x*s,y*s),Offset(x2*s,y2*s),1.5f*s,StrokeCap.Round)
        when(direction) {
            "↑"->{line(10f,16f,10f,4f);line(5.5f,8.5f,10f,4f);line(10f,4f,14.5f,8.5f)}
            "↓"->{line(10f,4f,10f,16f);line(5.5f,11.5f,10f,16f);line(10f,16f,14.5f,11.5f)}
            "←"->{line(16f,10f,4f,10f);line(8.5f,5.5f,4f,10f);line(4f,10f,8.5f,14.5f)}
            else->{line(4f,10f,16f,10f);line(11.5f,5.5f,16f,10f);line(16f,10f,11.5f,14.5f)}
        }
    }
}
