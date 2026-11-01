package app.derminal

import org.junit.Assert.*
import org.junit.Test
import com.termux.terminal.KeyHandler
import android.view.KeyEvent

class KeyInputTest {
    @Test fun bashInterruptAndHistoryControls() {
        assertEquals(3,KeyInput.control('c'.code));assertEquals(18,KeyInput.control('r'.code))
        assertEquals(0,KeyInput.control(' '.code));assertEquals(27,KeyInput.control('['.code))
        assertEquals(127,KeyInput.control('?'.code));assertEquals('中'.code,KeyInput.control('中'.code))
    }
    @Test fun cursorSequencesRespectApplicationMode() {
        assertEquals("\u001b[A",KeyHandler.getCode(KeyInput.keyCode("↑")!!,0,false,false))
        assertEquals("\u001bOA",KeyHandler.getCode(KeyInput.keyCode("↑")!!,0,true,false))
        assertEquals("\u001b[1;5D",KeyHandler.getCode(KeyInput.keyCode("←")!!,KeyHandler.KEYMOD_CTRL,false,false))
    }
    @Test fun allFunctionKeysAndEditableLayouts() {
        for(i in 1..12)assertEquals(KeyEvent.KEYCODE_F1+i-1,KeyInput.keyCode("F$i"))
        assertNull(KeyInput.keyCode("F13"));assertTrue(KeyInput.valid("ESC TAB CTRL ALT HOME ↑ END"))
        assertFalse(KeyInput.valid("ESC TAB"));assertFalse(KeyInput.valid("ESC TAB CTRL UNKNOWN"))
        assertEquals(listOf("CTRL","ALT","↑","↓"),KeyInput.parse("  CTRL   ALT ↑ ↓  "))
    }
}
