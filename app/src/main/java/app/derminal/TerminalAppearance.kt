package app.derminal

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView

/** Default cells are not painted by TerminalRenderer. Clear them through the
 * native View background; image mode instead lets Compose paint the backdrop. */
internal fun TerminalView.syncBackground(hasImage: Boolean) {
    val color=if(hasImage) Color.TRANSPARENT else
        mEmulator?.mColors?.mCurrentColors?.get(TextStyle.COLOR_INDEX_BACKGROUND)
            ?: TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
    if((background as? ColorDrawable)?.color!=color) setBackgroundColor(color)
}
