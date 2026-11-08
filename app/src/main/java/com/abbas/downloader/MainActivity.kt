package com.abbas.downloader

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var sharedUrlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSendIntent(intent)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = androidx.compose.ui.graphics.Color(0xFF10B981),
                        background = androidx.compose.ui.graphics.Color(0xFF121212),
                        surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        DownloaderScreen(initialUrl = sharedUrlState.value)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { rawText ->
                sharedUrlState.value = extractUrl(rawText)
            }
        }
    }

    private fun extractUrl(text: String): String {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else text
    }

    @Composable
    fun DownloaderScreen(initialUrl: String) {
        val scope = rememberCoroutineScope()
        var url by remember(initialUrl) { mutableStateOf(initialUrl) }
        var selectedOption by remember { mutableStateOf("LOW") }
        var isEngineReady by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var progress by remember { mutableFloatStateOf(0f) }
        var statusMessage by remember { mutableStateOf("جاري تهيئة محرك التحميل...") }

        val cookiesFile = File(filesDir, "cookies.txt")
        var hasCookies by remember { mutableStateOf(cookiesFile.exists()) }

        val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                contentResolver.openInputStream(it)?.use { input ->
                    cookiesFile.outputStream().use { output -> input.copyTo(output) }
                }
                hasCookies = cookiesFile.exists()
                Toast.makeText(this@MainActivity, "تم حفظ ملف الكوكيز بنجاح!", Toast.LENGTH_SHORT).show()
            }
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(application)
                    FFmpeg.getInstance().init(application)
                    isEngineReady = true
                    statusMessage = "المحرك جاهز للتحميل"
                } catch (e: Exception) {
                    statusMessage = "خطأ في التهيئة: ${e.localizedMessage}"
                }
            }
        }

        val qualityOptions = listOf(
            "LOW" to "جودة ضعيفة - 360p (افتراضي لتوفير البيانات)",
            "MED" to "جودة متوسطة - 480p / 720p",
            "HIGH" to "جودة عالية - 1080p أو أفضل جودة متاحة",
            "MP3" to "صوت فقط (MP3)",
            "IMAGE" to "تحميل صورة / منشور صور مباشر"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "تطبيق عباس للتحميل الشامل",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("رابط الفيديو أو المنشور (يوتيوب، فيسبوك، تيك توك، إكس...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "اختر صيغة وجودة التحميل:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    qualityOptions.forEach { (key, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (key == selectedOption),
                                    onClick = { selectedOption = key }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (key == selectedOption),
                                onClick = { selectedOption = key }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = statusMessage,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Button(
                onClick = {
                    if (url.isBlank()) {
                        statusMessage = "يرجى وضع الرابط أولاً!"
                        return@Button
                    }
                    scope.launch {
                        isDownloading = true
                        progress = 0f
                        statusMessage = "جاري الاتصال واستخراج الميديا..."
                        withContext(Dispatchers.IO) {
                            try {
                                val downloadDir = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    "AbbasDownloader"
                                )
                                if (!downloadDir.exists()) downloadDir.mkdirs()

                                val request = YoutubeDLRequest(url)
                                request.addOption("-o", "${downloadDir.absolutePath}/%(title).80s_%(id)s.%(ext)s")
                                request.addOption("--no-mtime")
                                request.addOption("--geo-bypass")

                                if (cookiesFile.exists()) {
                                    request.addOption("--cookies", cookiesFile.absolutePath)
                                }

                                when (selectedOption) {
                                    "LOW" -> request.addOption("-f", "worstvideo[ext=mp4]+worstaudio[ext=m4a]/worst[ext=mp4]/worst/best[height<=360]")
                                    "MED" -> request.addOption("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best")
                                    "HIGH" -> request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                                    "MP3" -> {
                                        request.addOption("-x")
                                        request.addOption("--audio-format", "mp3")
                                        request.addOption("--audio-quality", "0")
                                    }
                                    "IMAGE" -> {
                                        request.addOption("--write-thumbnail")
                                    }
                                }

                                YoutubeDL.getInstance().execute(request) { prog, _, line ->
                                    if (prog > 0) progress = prog
                                    statusMessage = "جاري التحميل: ${prog.toInt()}% \n$line"
                                }

                                MediaScannerConnection.scanFile(
                                    this@MainActivity,
                                    arrayOf(downloadDir.absolutePath),
                                    null,
                                    null
                                )
                                statusMessage = "تم التحميل بنجاح! محفوظ في مجلد Downloads/AbbasDownloader"
                            } catch (e: Exception) {
                                statusMessage = "فشل التحميل: تأكد من الرابط أو قم بتحديث المحرك.\nالخطأ: ${e.localizedMessage}"
                            } finally {
                                isDownloading = false
                            }
                        }
                    }
                },
                enabled = isEngineReady && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("بدء التحميل الآن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "إعدادات تجاوز قيود المنصات:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            statusMessage = "جاري تحديث محرك yt-dlp لتجاوز قيود المنصات الجديدة..."
                            withContext(Dispatchers.IO) {
                                try {
                                    val status = YoutubeDL.getInstance().updateYoutubeDL(
                                        application,
                                        YoutubeDL.UpdateChannel._NIGHTLY
                                    )
                                    statusMessage = "حالة التحديث: $status"
                                } catch (e: Exception) {
                                    statusMessage = "فشل التحديث: ${e.localizedMessage}"
                                }
                            }
                        }
                    },
                    enabled = isEngineReady && !isDownloading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("تحديث المحرك", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { cookiePicker.launch("text/plain") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (hasCookies) "تحديث الكوكيز ✓" else "إضافة cookies.txt", fontSize = 12.sp)
                }
            }
        }
    }
}
