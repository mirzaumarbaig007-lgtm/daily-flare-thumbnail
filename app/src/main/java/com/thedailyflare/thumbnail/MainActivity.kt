package com.thedailyflare.thumbnail

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ThumbnailScreen()
                }
            }
        }
    }

    @Composable
    private fun ThumbnailScreen() {
        var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var headline by remember { mutableStateOf("") }

        val picker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            contentResolver.openInputStream(uri)?.use { input ->
                sourceBitmap = BitmapFactory.decodeStream(input)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .background(ComposeColor.White),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Daily Flare Thumbnail", fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))

            Button(onClick = { picker.launch("image/*") }) {
                Text(if (sourceBitmap == null) "Choose Image" else "Change Image")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Headline") },
                placeholder = { Text("Enter the thumbnail headline") },
                minLines = 3
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(ComposeColor(0xFFEAEAEA)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = sourceBitmap
                if (bitmap == null) {
                    Text("Select an image to preview")
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Thumbnail source",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gradient = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.62f to ComposeColor.Transparent,
                                1f to ComposeColor.Black.copy(alpha = 0.9f)
                            )
                        )
                        drawRect(brush = gradient)

                        drawRoundRect(
                            color = ComposeColor.Black.copy(alpha = 0.75f),
                            topLeft = Offset(18f, 18f),
                            size = Size(64f, 64f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "DF",
                            29f,
                            61f,
                            Paint().apply {
                                color = Color.WHITE
                                textSize = 30f
                                typeface = Typeface.DEFAULT_BOLD
                                isAntiAlias = true
                            }
                        )
                    }

                    if (headline.isNotBlank()) {
                        Text(
                            text = headline,
                            color = ComposeColor.White,
                            fontSize = 27.sp,
                            lineHeight = 31.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp, 0.dp, 20.dp, 46.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "f   𝕏   ▶",
                            color = ComposeColor.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = {
                        sourceBitmap?.let {
                            val title = headline.trim().ifBlank { "Daily Flare" }
                            saveThumbnail(renderThumbnail(it, title), title)
                        }
                    },
                    enabled = sourceBitmap != null
                ) {
                    Text("Export 3:4 JPG")
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        headline = ""
                        sourceBitmap = null
                    }
                ) {
                    Text("Clear")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "1080 × 1440 • high-quality JPEG • fixed Daily Flare branding",
                fontSize = 12.sp
            )
        }
    }

    private fun renderThumbnail(source: Bitmap, headline: String): Bitmap {
        val width = 1080
        val height = 1440
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val scale = maxOf(
            width.toFloat() / source.width,
            height.toFloat() / source.height
        )
        val drawW = source.width * scale
        val drawH = source.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(left, top, left + drawW, top + drawH),
            imagePaint
        )

        val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        logoPaint.color = Color.argb(220, 0, 0, 0)
        canvas.drawRoundRect(24f, 24f, 124f, 124f, 20f, 20f, logoPaint)

        logoPaint.color = Color.WHITE
        logoPaint.textSize = 46f
        logoPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("DF", 43f, 91f, logoPaint)

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gradientPaint.shader = LinearGradient(
            0f, height * 0.62f,
            0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.argb(235, 0, 0, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), gradientPaint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 63f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        val textWidth = 980
        val textLayout = StaticLayout.Builder
            .obtain(headline, 0, headline.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.0f)
            .build()

        val maxTextHeight = 285
        val textTop = height - 145 - minOf(textLayout.height, maxTextHeight)

        canvas.save()
        canvas.translate((width - textWidth) / 2f, textTop.toFloat())
        textLayout.draw(canvas)
        canvas.restore()

        val socialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("f   𝕏   ▶", width - 260f, height - 42f, socialPaint)

        return output
    }

    private fun saveThumbnail(bitmap: Bitmap, headline: String) {
        val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeTitle = headline
            .lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .take(55)
            .ifBlank { "daily-flare-thumbnail" }

        val filename = "DF-$safeTitle-$time.jpg"
        val resolver = contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Daily Flare"
            )
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show()
                return
            }

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            Toast.makeText(
                this,
                "Thumbnail saved to Pictures/Daily Flare",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            bitmap.recycle()
        }
    }
}
