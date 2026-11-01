package app.derminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink=Color(0xFF202327)
val Muted=Color(0xFF666D76)
val Line=Color(0xFFE3E5E8)
val Wash=Color(0xFFF4F5F6)
val Accent=Color(0xFF365B75)
val Danger=Color(0xFFAD3239)

@Composable fun Label(text: String,modifier: Modifier=Modifier,size: Int=14,color: Color=Ink,weight: FontWeight=FontWeight.Normal,maxLines: Int=Int.MAX_VALUE,mono: Boolean=false) {
    BasicText(text,modifier,style=TextStyle(color=color,fontSize=size.sp,lineHeight=(size*1.45f).sp,fontWeight=weight,fontFamily=if(mono) FontFamily.Monospace else FontFamily.SansSerif),maxLines=maxLines,overflow=TextOverflow.Ellipsis)
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

@Composable fun Action(text: String,modifier: Modifier=Modifier,primary: Boolean=false,destructive: Boolean=false,enabled: Boolean=true,onClick: ()->Unit) {
    val source=remember { MutableInteractionSource() };val pressed by source.collectIsPressedAsState()
    val bg=when { !enabled->Line;primary && destructive->Danger;primary->Ink;pressed->Line;else->Wash }
    Box(modifier.heightIn(min=42.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(source,null,enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Label(text,size=14,color=if(primary) Color.White else if(destructive) Danger else if(enabled) Ink else Muted,weight=FontWeight.Medium)
    }
}

@Composable fun Field(value: String,onChange: (String)->Unit,modifier: Modifier=Modifier,hint: String="",singleLine: Boolean=true,mono: Boolean=false,error: Boolean=false) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value,onChange,modifier.fillMaxWidth().onFocusChanged { focused=it.isFocused }.clip(RoundedCornerShape(10.dp)).background(if(focused) Color.White else Wash).border(if(focused) 1.5.dp else 1.dp,if(error) Danger else if(focused) Ink else Line,RoundedCornerShape(10.dp)).padding(horizontal=12.dp,vertical=11.dp),textStyle=TextStyle(color=Ink,fontSize=14.sp,lineHeight=21.sp,fontFamily=if(mono)FontFamily.Monospace else FontFamily.SansSerif),cursorBrush=SolidColor(Ink),singleLine=singleLine,decorationBox={ inner -> Box { if(value.isEmpty()) Label(hint,color=Muted,size=14,mono=mono);inner() } })
}

@Composable fun SettingRow(title: String,subtitle: String?=null,onClick: (()->Unit)?=null,trailing: @Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick=onClick)else Modifier).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Label(title,weight=FontWeight.Medium);if(subtitle!=null)Label(subtitle,size=12,color=Muted) };trailing()
    }
}

@Composable fun ToggleRow(title: String,subtitle: String?=null,checked: Boolean,onChange: (Boolean)->Unit) {
    SettingRow(title,subtitle,onClick={onChange(!checked)}) {
        Box(Modifier.size(38.dp,23.dp).clip(RoundedCornerShape(20.dp)).background(if(checked) Ink else Color(0xFFCDD1D6)).semantics { contentDescription=if(checked)"已开启"else"已关闭" }.padding(3.dp),contentAlignment=if(checked) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(17.dp).background(Color.White,RoundedCornerShape(20.dp))) }
    }
}

@Composable fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Line)) }
@Composable fun Caption(text: String) { Label(text,Modifier.padding(top=20.dp,bottom=8.dp),size=12,color=Muted,weight=FontWeight.Medium) }
@Composable fun Choice(options: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        options.forEach { (id,title) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if(id==selected)Ink else Wash).clickable { onSelect(id) }.padding(horizontal=5.dp,vertical=10.dp),contentAlignment=Alignment.Center) { Label(title,size=12,color=if(id==selected)Color.White else Ink,maxLines=1) }
        }
    }
}
