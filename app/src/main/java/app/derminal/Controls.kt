package app.derminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun Label(text: String,modifier: Modifier=Modifier,size: Int=14,color: Color=Ink,weight: FontWeight=FontWeight.Normal,maxLines: Int=Int.MAX_VALUE,mono: Boolean=false) {
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=size.sp,lineHeight=(size*1.45f).sp,fontWeight=weight,fontFamily=if(mono) FontFamily.Monospace else FontFamily.SansSerif,platformStyle=PlatformTextStyle(includeFontPadding=false)),maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}

@Composable fun Glyph(name: String,modifier: Modifier=Modifier,color: Color=Ink) {
    Canvas(modifier.size(20.dp)) {
        val s=size.width/24f
        fun line(x: Float,y: Float,x2: Float,y2: Float)=drawLine(color,Offset(x*s,y*s),Offset(x2*s,y2*s),1.65f*s,StrokeCap.Round)
        when(name) {
            "plus"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
            "close"->{line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            "back"->{line(14f,5f,7f,12f);line(7f,12f,14f,19f)}
            "chevron"->{line(9f,7f,14f,12f);line(14f,12f,9f,17f)}
            "settings"->{
                val p=Path()
                for(i in 0..47) { val a=i*Math.PI/24;val r=if(i%6 in 1..4) 9.0 else 7.1;val x=(12+kotlin.math.cos(a)*r).toFloat()*s;val y=(12+kotlin.math.sin(a)*r).toFloat()*s;if(i==0)p.moveTo(x,y)else p.lineTo(x,y) };p.close()
                drawPath(p,color,style=Stroke(1.5f*s));drawCircle(color,3.2f*s,style=Stroke(1.5f*s))
            }
            "keyboard"->{drawRoundRect(color,Offset(3f*s,5f*s),androidx.compose.ui.geometry.Size(18f*s,14f*s),androidx.compose.ui.geometry.CornerRadius(2f*s),style=Stroke(1.5f*s));for(y in listOf(9,12)) for(x in listOf(7,10,13,16))drawCircle(color,.65f*s,Offset(x*s,y*s));line(8f,16f,16f,16f)}
            "copy"->{drawRoundRect(color,Offset(8f*s,8f*s),androidx.compose.ui.geometry.Size(12f*s,12f*s),androidx.compose.ui.geometry.CornerRadius(2f*s),style=Stroke(1.5f*s));line(15f,4f,5f,4f);line(4f,5f,4f,15f)}
            "search"->{drawCircle(color,6.5f*s,Offset(10f*s,10f*s),style=Stroke(1.6f*s));line(15f,15f,21f,21f)}
            "check"->{line(5f,12f,10f,17f);line(10f,17f,20f,7f)}
            "terminal"->{line(5f,6f,11f,12f);line(11f,12f,5f,18f);line(14f,18f,21f,18f)}
            "more"->{for(y in listOf(5,12,19))drawCircle(color,1.5f*s,Offset(12f*s,y*s))}
            "download"->{line(12f,3f,12f,15f);line(7f,10f,12f,15f);line(12f,15f,17f,10f);line(4f,17f,4f,21f);line(4f,21f,20f,21f);line(20f,21f,20f,17f)}
        }
    }
}

@Composable fun IconButton(icon: String,description: String,modifier: Modifier=Modifier,color: Color=Ink,onClick: ()->Unit) {
    val source=remember { MutableInteractionSource() };val pressed by source.collectIsPressedAsState()
    Box(modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(if(pressed) Line else Color.Transparent).clickable(source,null,role=Role.Button,onClick=onClick).semantics { contentDescription=description },contentAlignment=Alignment.Center) { Glyph(icon,color=color) }
}

@Composable fun CompactClose(description: String,modifier: Modifier=Modifier,small: Boolean=false,onClick: ()->Unit) {
    val source=remember {MutableInteractionSource()};val pressed by source.collectIsPressedAsState()
    Box(modifier.size(if(small)28.dp else 34.dp).clip(CircleShape).clickable(source,null,role=Role.Button,onClick=onClick).semantics{contentDescription=description},contentAlignment=Alignment.Center) {
        Box(Modifier.size(if(small)18.dp else 23.dp).background(Muted.copy(alpha=if(pressed).22f else .1f),CircleShape),contentAlignment=Alignment.Center) {Glyph("close",Modifier.size(if(small)10.dp else 12.dp),Muted)}
    }
}

@Composable fun TextAction(text: String,modifier: Modifier=Modifier,onClick: ()->Unit) {
    val source=remember {MutableInteractionSource()};val pressed by source.collectIsPressedAsState()
    Box(modifier.heightIn(min=32.dp).clip(RoundedCornerShape(7.dp)).background(if(pressed)LocalUiColors.current.pressed else Color.Transparent).clickable(source,null,role=Role.Button,onClick=onClick).padding(horizontal=9.dp,vertical=6.dp),contentAlignment=Alignment.Center) {Label(text,size=12,color=Muted,weight=FontWeight.Medium)}
}

@Composable fun Action(text: String,modifier: Modifier=Modifier,primary: Boolean=false,destructive: Boolean=false,enabled: Boolean=true,onClick: ()->Unit) {
    val source=remember { MutableInteractionSource() };val pressed by source.collectIsPressedAsState()
    val bg=when { !enabled->Line;primary && destructive->if(pressed)Color(0xFF932D34)else Color(0xFFAD3239);primary->if(pressed)Ink.copy(alpha=.82f)else Ink;pressed->LocalUiColors.current.pressed;else->Wash }
    Box(modifier.heightIn(min=42.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(source,null,enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Label(text,size=14,color=when{!enabled->Muted;primary && destructive->Color.White;primary->OnInk;destructive->Danger;else->Ink},weight=FontWeight.Medium)
    }
}

@Composable fun Field(value: String,onChange: (String)->Unit,modifier: Modifier=Modifier,hint: String="",singleLine: Boolean=true,mono: Boolean=false,error: Boolean=false) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value,onChange,modifier.fillMaxWidth().onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(9.dp)).background(if(focused) Raised else Wash).border(if(focused)1.dp else .5.dp,if(error) Danger else if(focused) Accent else Line,RoundedCornerShape(9.dp)).padding(horizontal=12.dp,vertical=11.dp),textStyle=TextStyle(color=Ink,fontSize=14.sp,lineHeight=21.sp,fontFamily=if(mono)FontFamily.Monospace else FontFamily.SansSerif,platformStyle=PlatformTextStyle(includeFontPadding=false)),cursorBrush=SolidColor(Accent),singleLine=singleLine,decorationBox={ inner -> Box { if(value.isEmpty()) Label(hint,color=Muted,size=14,mono=mono);inner() } })
}

@Composable fun SettingRow(title: String,subtitle: String?=null,onClick: (()->Unit)?=null,trailing: @Composable ()->Unit={}) {
    val source=remember {MutableInteractionSource()};val pressed by source.collectIsPressedAsState()
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(onClick!=null)Modifier.clip(RoundedCornerShape(8.dp)).background(if(pressed)LocalUiColors.current.pressed else Color.Transparent).clickable(source,null,onClick=onClick)else Modifier).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Label(title,weight=FontWeight.Medium);if(subtitle!=null)Label(subtitle,size=12,color=Muted) };trailing()
    }
}

@Composable fun ToggleRow(title: String,subtitle: String?=null,checked: Boolean,onChange: (Boolean)->Unit) {
    SettingRow(title,subtitle,onClick={onChange(!checked)}) {
        Box(Modifier.size(36.dp,22.dp).clip(RoundedCornerShape(20.dp)).background(if(checked) Ink else Line).semantics { contentDescription=if(checked)"已开启"else"已关闭" }.padding(3.dp),contentAlignment=if(checked) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(16.dp).background(if(checked)OnInk else if(LocalUiColors.current.dark)Muted else Color.White,RoundedCornerShape(20.dp))) }
    }
}

@Composable fun Divider() { Box(Modifier.fillMaxWidth().height(.5.dp).background(Line)) }
@Composable fun Caption(text: String) { Label(text,Modifier.padding(top=18.dp,bottom=8.dp),size=12,color=Muted,weight=FontWeight.Medium) }
@Composable fun Choice(options: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Wash).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        options.forEach { (id,title) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(7.dp)).background(if(id==selected)Raised else Color.Transparent).border(.5.dp,if(id==selected)Line else Color.Transparent,RoundedCornerShape(7.dp)).clickable { onSelect(id) }.padding(horizontal=5.dp,vertical=8.dp),contentAlignment=Alignment.Center) { Label(title,size=12,color=if(id==selected)Ink else Muted,weight=if(id==selected)FontWeight.Medium else FontWeight.Normal,maxLines=1) }
        }
    }
}
