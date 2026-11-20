package mx.uv.miuv2

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.uv.miuv2.data.Repo
import mx.uv.miuv2.ui.App
import mx.uv.miuv2.ui.MiuvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Repo.init(this)
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MiuvTheme { App() } }
    }
}
