package app.derminal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class Preferences(
    val theme: String = "paper",
    val fontSize: Int = 14,
    val font: String = "system",
    val bold: Boolean = false,
    val cursor: Int = 0,
    val blink: Boolean = true,
    val scrollback: Int = 10000,
    val keepScreen: Boolean = false,
    val wakeLock: Boolean = false,
    val bell: Boolean = false,
    val autoKeyboard: Boolean = true,
    val extraAlways: Boolean = true,
    val keyHaptic: Boolean = false,
    val keyHeight: Int = 38,
    val volumeCtrl: Boolean = true,
    val workingDirectory: String = "/root",
    val startup: String = "",
    val background: Boolean = false,
    val backgroundDim: Int = 88,
    val customForeground: String = "",
    val customBackground: String = "",
    val row1: String = "ESC TAB CTRL ALT HOME ↑ END",
    val row2: String = "FN SHIFT / - ← ↓ →",
    val seccomp: Boolean = true,
) {
    fun json() = JSONObject().apply {
        put("theme",theme); put("fontSize",fontSize); put("font",font); put("bold",bold)
        put("cursor",cursor); put("blink",blink); put("scrollback",scrollback)
        put("keepScreen",keepScreen); put("wakeLock",wakeLock); put("bell",bell)
        put("autoKeyboard",autoKeyboard); put("extraAlways",extraAlways); put("keyHaptic",keyHaptic)
        put("keyHeight",keyHeight); put("volumeCtrl",volumeCtrl); put("workingDirectory",workingDirectory)
        put("startup",startup); put("background",background); put("backgroundDim",backgroundDim)
        put("customForeground",customForeground); put("customBackground",customBackground)
        put("row1",row1); put("row2",row2); put("seccomp",seccomp)
    }
    companion object {
        fun from(j: JSONObject): Preferences {
            val d = Preferences()
            return Preferences(j.optString("theme",d.theme),j.optInt("fontSize",14).coerceIn(9,32),j.optString("font",d.font),j.optBoolean("bold"),j.optInt("cursor",0).coerceIn(0,2),j.optBoolean("blink",true),j.optInt("scrollback",10000).coerceIn(1000,50000),j.optBoolean("keepScreen"),j.optBoolean("wakeLock"),j.optBoolean("bell"),j.optBoolean("autoKeyboard",true),j.optBoolean("extraAlways",true),j.optBoolean("keyHaptic"),j.optInt("keyHeight",d.keyHeight).coerceIn(32,56),j.optBoolean("volumeCtrl",true),j.optString("workingDirectory","/root"),j.optString("startup",""),j.optBoolean("background"),j.optInt("backgroundDim",88).coerceIn(20,100),j.optString("customForeground",""),j.optString("customBackground",""),j.optString("row1",d.row1),j.optString("row2",d.row2),j.optBoolean("seccomp",true))
        }
    }
}

class PreferenceStore(context: Context) {
    val disk = context.getSharedPreferences("derminal", Context.MODE_PRIVATE)
    var value by mutableStateOf(runCatching { Preferences.from(JSONObject(disk.getString("preferences","{}")!!)) }.getOrDefault(Preferences()))
        private set
    init {
        if(disk.getInt("key-deck-style",0)<1) {
            if(value.keyHeight==42) update {it.copy(keyHeight=38)}
            disk.edit().putInt("key-deck-style",1).apply()
        }
    }
    fun update(transform: (Preferences) -> Preferences) {
        value = transform(value)
        disk.edit().putString("preferences",value.json().toString()).apply()
    }
}

data class Palette(val id: String, val name: String, val foreground: String, val background: String, val cursor: String, val ansi: List<String>)
val palettes = listOf(
    Palette("paper","纸白","#22252A","#FFFFFF","#3C4655",listOf("#24292E","#B52D35","#26753B","#926300","#255AB5","#8543A8","#1D7081","#D7DCE1","#67717D","#C53B45","#348B4C","#A77715","#376DC6","#9956BA","#288598","#F5F6F7")),
    Palette("ink","墨黑","#E2E4E9","#14161A","#CBD5E1",listOf("#23262E","#F07178","#A3BE8C","#EBCB8B","#81A1C1","#B48EAD","#88C0D0","#D8DEE9","#626B7A","#F38BA8","#B3D6A0","#F9E2AF","#A0C4E8","#CBA6D9","#A8DFEB","#F8F9FC")),
    Palette("solar","暖纸","#514B40","#F7F3E8","#877450",listOf("#39352D","#BA403D","#657932","#A66F16","#386A97","#97547B","#357F7B","#DDD5C2","#8E8570","#CE5752","#7B8F42","#BA8727","#4D84AF","#AF6B91","#4C9792","#FFFDF5")),
    Palette("nord","极夜","#D8DEE9","#2E3440","#88C0D0",listOf("#3B4252","#BF616A","#A3BE8C","#EBCB8B","#81A1C1","#B48EAD","#88C0D0","#E5E9F0","#4C566A","#BF616A","#A3BE8C","#EBCB8B","#81A1C1","#B48EAD","#8FBCBB","#ECEFF4")),
)
