package app.derminal

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native Canvas regression checks on the CI JVM, without an Android virtual device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TerminalRenderingTest {
    private val backdrop=Color.rgb(23,29,35)
    private companion object {const val WIDTH=320;const val HEIGHT=240}

    private fun terminal(): TerminalView {
        val output=object: TerminalOutput() {
            override fun write(data: ByteArray,offset: Int,count: Int) {}
            override fun titleChanged(oldTitle: String?,newTitle: String?) {}
            override fun onCopyTextToClipboard(text: String?) {}
            override fun onPasteTextFromClipboard() {}
            override fun onBell() {}
            override fun onColorsChanged() {}
        }
        return TerminalView(RuntimeEnvironment.getApplication(),null).apply {
            setTextSize(20)
            layout(0,0,WIDTH,HEIGHT)
            mEmulator=TerminalEmulator(output,20,8,12,24,100,null)
            mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]=backdrop
            feed(this,"\u001b[?25l")
        }
    }

    private fun feed(view: TerminalView,text: String) {
        val bytes=text.toByteArray(Charsets.UTF_8)
        view.mEmulator.append(bytes,bytes.size)
    }

    private fun bitmap()=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
    private fun pixels(image: Bitmap)=IntArray(WIDTH*HEIGHT).also {image.getPixels(it,0,WIDTH,0,0,WIDTH,HEIGHT)}

    @Test fun eraseRemovesPreviousTextAndCellBackgrounds() {
        val view=terminal()
        view.syncBackground(false)
        val frame=bitmap()
        feed(view,"\u001b[41mOVERLAP\u001b[0m")
        view.draw(Canvas(frame))
        assertTrue("The first frame must actually draw terminal content",pixels(frame).any {it!=backdrop})
        feed(view,"\r\u001b[2K")
        view.draw(Canvas(frame))
        assertTrue("Erased cells must repaint their entire previous frame",pixels(frame).all {it==backdrop})
        assertTrue(view.isOpaque)
    }

    @Test fun leavingAlternateScreenRestoresThePrimaryFrame() {
        val view=terminal()
        view.syncBackground(false)
        feed(view,"PRIMARY")
        val expected=bitmap().also {view.draw(Canvas(it))}
        val frame=bitmap().also {view.draw(Canvas(it))}
        feed(view,"\u001b[?1049h\u001b[44mALTERNATE SCREEN\u001b[0m")
        view.draw(Canvas(frame))
        assertFalse(expected.sameAs(frame))
        feed(view,"\u001b[?1049l")
        view.draw(Canvas(frame))
        assertTrue("Alternate-screen output must not remain over the primary screen",expected.sameAs(frame))
    }

    @Test fun imageBackdropRemainsVisibleAfterEraseAndThemeChanges() {
        val view=terminal()
        view.syncBackground(true)
        assertFalse("Transparent cells must not occlude the Compose backdrop",view.isOpaque)
        val image=bitmap().apply {
            eraseColor(Color.rgb(60,90,120))
            Canvas(this).drawRect(0f,0f,160f,HEIGHT.toFloat(),android.graphics.Paint().apply {color=Color.rgb(90,60,40)})
        }
        val parent=FrameLayout(view.context).apply {
            background=BitmapDrawable(resources,image).apply {setBounds(0,0,WIDTH,HEIGHT)}
            addView(view,FrameLayout.LayoutParams(WIDTH,HEIGHT))
            measure(android.view.View.MeasureSpec.makeMeasureSpec(WIDTH,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(HEIGHT,android.view.View.MeasureSpec.EXACTLY))
            layout(0,0,WIDTH,HEIGHT)
        }
        val frame=bitmap()
        feed(view,"\u001b[42mOLD CONTENT\u001b[0m")
        parent.draw(Canvas(frame))
        assertFalse(image.sameAs(frame))
        feed(view,"\r\u001b[2K")
        parent.draw(Canvas(frame))
        assertTrue("Cleared cells must reveal the original image",image.sameAs(frame))
        feed(view,"\u001b]11;#24506a\u0007")
        view.syncBackground(false)
        view.draw(Canvas(frame))
        assertTrue(pixels(frame).all {it==Color.rgb(36,80,106)})
        assertTrue(view.isOpaque)
    }
}
