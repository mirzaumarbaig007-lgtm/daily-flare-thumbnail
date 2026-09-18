package com.thedailyflare.thumbnail

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy {
        getSharedPreferences("daily_flare_thumbnail", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ThumbnailScreen() }
            }
        }
    }

    private fun loadBitmap(uri: Uri?): Bitmap? = try {
        uri?.let { contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
    } catch (_: Exception) { null }

    private fun persistUri(uri: Uri, key: String) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        prefs.edit().putString(key, uri.toString()).apply()
    }

    @Composable
    private fun ThumbnailScreen() {
        var mainBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var logoUri by rememberSaveable { mutableStateOf(prefs.getString("logo_uri", null)) }
        var socialsUri by rememberSaveable { mutableStateOf(prefs.getString("socials_uri", null)) }
        var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var socialsBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var headline by rememberSaveable { mutableStateOf("") }
        var highlighted by rememberSaveable { mutableStateOf(emptySet<Int>()) }
        var showTextPopup by remember { mutableStateOf(false) }

        val mainPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) mainBitmap = loadBitmap(uri)
        }
        val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistUri(uri, "logo_uri")
                logoUri = uri.toString()
                logoBitmap = loadBitmap(uri)
            }
        }
        val socialsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistUri(uri, "socials_uri")
                socialsUri = uri.toString()
                socialsBitmap = loadBitmap(uri)
            }
        }

        LaunchedEffect(logoUri) { logoBitmap = loadBitmap(logoUri?.let(Uri::parse)) }
        LaunchedEffect(socialsUri) { socialsBitmap = loadBitmap(socialsUri?.let(Uri::parse)) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Daily Flare Thumbnail", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // The four inputs live directly inside the preview at the exact
            // positions where their final content will appear.
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ComposeColor(0xFFEAEAEA))
            ) {
                if (mainBitmap == null) {
                    Button(
                        onClick = { mainPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("1. Main Image", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Image(
                        mainBitmap!!.asImageBitmap(),
                        "Main image",
                        Modifier.fillMaxSize().clickable {
                            mainPicker.launch(arrayOf("image/*"))
                        },
                        contentScale = ContentScale.Crop
                    )
                }

                // Logo input occupies its final top-left position.
                if (logoBitmap == null) {
                    Button(
                        onClick = { logoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(18.dp)
                            .height(92.dp)
                            .width(92.dp)
                    ) {
                        Text(
                            "2.\nLogo",
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Image(
                        logoBitmap!!.asImageBitmap(),
                        "Logo",
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(18.dp)
                            .height(92.dp)
                            .width(92.dp)
                            .clickable { logoPicker.launch(arrayOf("image/*")) },
                        contentScale = ContentScale.Fit
                    )
                }

                // Headline input occupies the final bottom text area.
                if (headline.isBlank()) {
                    Button(
                        onClick = { showTextPopup = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(230.dp)
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                        Text(
                            "4. Headline",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    HeadlinePreview(
                        headline,
                        highlighted,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clickable { showTextPopup = true }
                    )
                }

                // Social input occupies its final bottom strip.
                if (socialsBitmap == null) {
                    Button(
                        onClick = { socialsPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 24.dp, vertical = 7.dp)
                    ) {
                        Text("3. Social Icons", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Image(
                        socialsBitmap!!.asImageBitmap(),
                        "Social icons",
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 24.dp, vertical = 7.dp)
                            .clickable { socialsPicker.launch(arrayOf("image/*")) },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    enabled = mainBitmap != null,
                    onClick = {
                        mainBitmap?.let {
                            val title = headline.trim().ifBlank { "Daily Flare" }
                            saveThumbnail(
                                renderThumbnail(it, logoBitmap, socialsBitmap, title, highlighted),
                                title
                            )
                        }
                    }
                ) { Text("Export 3:4 JPG", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    mainBitmap = null
                    headline = ""
                    highlighted = emptySet()
                }) { Text("Clear", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "1080 × 1440 • bold text • 20% text area • 25% black fade • tap each area to change",
                fontSize = 12.sp
            )
        }

        if (showTextPopup) {
            TextHighlightDialog(
                initialText = headline,
                initialHighlighted = highlighted,
                onDismiss = { showTextPopup = false },
                onApply = { value, selection ->
                    headline = value
                    highlighted = selection
                    showTextPopup = false
                }
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun HeadlinePreview(
        headline: String,
        highlighted: Set<Int>,
        modifier: Modifier
    ) {
        if (headline.isBlank()) return
        val words = headline.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

        Column(
            modifier = modifier
                .background(ComposeColor.Black.copy(alpha = 0.18f))
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 74.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                words.forEachIndexed { index, word ->
                    Text(
                        "$word ",
                        color = if (index in highlighted) ComposeColor.Black else ComposeColor.White,
                        fontSize = 27.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (index in highlighted) ComposeColor(0xFFFFC107)
                                else ComposeColor.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun TextHighlightDialog(
        initialText: String,
        initialHighlighted: Set<Int>,
        onDismiss: () -> Unit,
        onApply: (String, Set<Int>) -> Unit
    ) {
        var text by remember { mutableStateOf(initialText) }
        var selected by remember { mutableStateOf(initialHighlighted) }
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Headline & Highlights", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            val count = it.trim().split(Regex("\\s+")).count { w -> w.isNotEmpty() }
                            selected = selected.filter { index -> index < count }.toSet()
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("Headline", fontWeight = FontWeight.Bold) },
                        minLines = 3
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Tap any word to highlight it:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(words) { index, word ->
                            FilterChip(
                                selected = index in selected,
                                onClick = {
                                    selected = if (index in selected) selected - index else selected + index
                                },
                                label = { Text(word, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onApply(text.trim(), selected) }) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", fontWeight = FontWeight.Bold) }
            }
        )
    }

    private fun renderThumbnail(
        source: Bitmap,
        logo: Bitmap?,
        socials: Bitmap?,
        headline: String,
        highlighted: Set<Int>
    ): Bitmap {
        val width = 1080
        val height = 1440
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + dw, top + dh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        logo?.let {
            val s = minOf(120f / it.width, 120f / it.height)
            val lw = it.width * s
            val lh = it.height * s
            canvas.drawBitmap(it, null, android.graphics.RectF(24f, 24f, 24f + lw, 24f + lh),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        // Black fade covers the lower 25%; the headline itself is centered in the bottom 20%.
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.50f, 0f, height * 0.80f,
                intArrayOf(Color.TRANSPARENT, Color.argb(235, 0, 0, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), fade)

        val words = headline.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val fullText = words.joinToString(" ")
        val normalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 60f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val textWidth = 940
        val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, normalPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

        val areaTop = height * 0.80f
        val areaHeight = height * 0.20f
        val textTop = areaTop + maxOf(10f, (areaHeight - layout.height) / 2f)

        canvas.save()
        canvas.translate((width - textWidth) / 2f, textTop)

        // Highlight rectangles are painted first, then the bold text is painted on top.
        if (highlighted.isNotEmpty()) {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 193, 7)
            }
            var offset = 0
            words.forEachIndexed { index, word ->
                val start = offset
                val line = layout.getLineForOffset(start)
                val lineStart = layout.getLineStart(line)
                val prefix = fullText.substring(lineStart, start)
                val x1 = normalPaint.measureText(prefix)
                val x2 = x1 + normalPaint.measureText(word)
                if (index in highlighted) {
                    canvas.drawRect(
                        x1 - 5f,
                        layout.getLineTop(line).toFloat() + 2f,
                        x2 + 5f,
                        layout.getLineBottom(line).toFloat() - 2f,
                        highlightPaint
                    )
                }
                offset += word.length + 1
            }
        }

        layout.draw(canvas)

        if (highlighted.isNotEmpty()) {
            val blackPaint = TextPaint(normalPaint).apply { color = Color.BLACK }
            var offset = 0
            words.forEachIndexed { index, word ->
                if (index in highlighted) {
                    val line = layout.getLineForOffset(offset)
                    val lineStart = layout.getLineStart(line)
                    val prefix = fullText.substring(lineStart, offset)
                    val x = normalPaint.measureText(prefix)
                    canvas.drawText(word, x, layout.getLineBaseline(line).toFloat(), blackPaint)
                }
                offset += word.length + 1
            }
        }
        canvas.restore()

        socials?.let {
            val maxW = 900f
            val maxH = 105f
            val s = minOf(maxW / it.width, maxH / it.height)
            val sw = it.width * s
            val sh = it.height * s
            val x = (width - sw) / 2f
            val y = height - sh - 16f
            canvas.drawBitmap(it, null, android.graphics.RectF(x, y, x + sw, y + sh),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        return output
    }

    private fun saveThumbnail(bitmap: Bitmap, headline: String) {
        val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeTitle = headline.lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "-").trim('-').take(55)
            .ifBlank { "daily-flare-thumbnail" }
        val filename = "DF-$safeTitle-$time.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Daily Flare")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show()
                return
            }

        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            Toast.makeText(this, "Thumbnail saved to Pictures/Daily Flare", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            Toast.makeText(this, "Export failed: \${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            bitmap.recycle()
        }
    }
}
