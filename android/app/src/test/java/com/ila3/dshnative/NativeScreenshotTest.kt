package com.ila3.dshnative

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ila3.dshnative.ui.AttachSheetPreviewContent
import com.ila3.dshnative.ui.ChatPreviewContent
import com.ila3.dshnative.ui.ProductChatPreview
import com.ila3.dshnative.ui.ProductHomePreview
import com.ila3.dshnative.ui.ProductInboxPreview
import com.ila3.dshnative.ui.ProductSettingsPreview
import com.ila3.dshnative.ui.DshNativeTheme
import com.ila3.dshnative.ui.DshTheme
import com.ila3.dshnative.ui.DshThemeMode
import com.ila3.dshnative.ui.SessionsPreviewContent
import com.ila3.dshnative.ui.QuestionPreviewContent
import com.ila3.dshnative.ui.SettingsPreviewContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeScreenshotTest {
    @Test
    fun capturePairScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        capture(activity, "native-pair.png")
    }

    @Test
    fun captureSessionsScreen() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { SessionsPreviewContent() } }
        capture(activity, "native-sessions.png")
    }

    @Test
    fun captureChatScreen() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { ChatPreviewContent() } }
        capture(activity, "native-chat.png")
    }

    @Test
    fun captureSettings() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { SettingsPreviewContent() } }
        capture(activity, "native-settings.png")
    }

    @Test
    fun captureQuestion() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { QuestionPreviewContent() } }
        capture(activity, "native-question.png")
    }

    @Test
    fun captureAttachSheet() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { AttachSheetPreviewContent() } }
        capture(activity, "native-attach-sheet.png")
    }

    @Test
    fun captureProductHome() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { ProductHomePreview() }
        capture(activity, "product-home.png")
    }

    @Test
    fun captureProductChat() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { ProductChatPreview() }
        capture(activity, "product-chat.png")
    }

    @Test
    fun captureProductInbox() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { DshNativeTheme { ProductInboxPreview() } }
        capture(activity, "product-inbox.png")
    }

    @Test
    fun captureProductSettings() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { ProductSettingsPreview() }
        capture(activity, "product-settings.png")
    }

    @Test
    fun captureAuroraChat() {
        DshTheme.mode = DshThemeMode.AURORA
        try {
            val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
            activity.setContent { DshNativeTheme { ChatPreviewContent() } }
            capture(activity, "native-chat-aurora.png")
        } finally {
            DshTheme.mode = DshThemeMode.PAPER
        }
    }

    private fun capture(activity: ComponentActivity, name: String) {
        val root: View = activity.window.decorView
        val width = 1080
        val height = 2340
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
