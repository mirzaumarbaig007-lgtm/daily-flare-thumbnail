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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
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

            // All four inputs are interactive areas inside the thumbnail preview.
            // There are no separate input buttons above the preview.
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ComposeColor(0xFFEAEAEA))
            ) {
                val previewHeadlineHeight = maxHeight * 0.20f
                val previewSocialHeight = maxHeight * 0.20f
                // Main image: a centered + button until an image is selected.
                if (mainBitmap == null) {
                    Button(
                        onClick = { mainPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("＋", fontSize = 32.sp, fontWeight = FontWeight.Bold)
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

                // Logo: match the reference output — large, top-left, with a soft shadow.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 28.dp, top = 24.dp)
                        .height(128.dp)
                        .width(128.dp)
                        .clickable { logoPicker.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (logoBitmap == null) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = ComposeColor.Black.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Select logo",
                                    color = ComposeColor.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Image(
                            logoBitmap!!.asImageBitmap(),
                            "Logo",
                            Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Headline: click the final text area to open the text/highlight editor.
                if (headline.isBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(previewHeadlineHeight)
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                            .offset(y = -(previewSocialHeight * 0.625f))
                            .clickable { showTextPopup = true },
                        color = ComposeColor.Black.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Tap to add headline",
                                color = ComposeColor.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    HeadlinePreview(
                        headline,
                        highlighted,
                        previewHeadlineHeight,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(previewHeadlineHeight)
                            .offset(y = -(previewSocialHeight * 0.6666667f))
                            .clickable { showTextPopup = true }
                    )
                }

                // Social icons: a small bottom strip in their final position.
                if (socialsBitmap == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(previewSocialHeight)
                            .clickable { socialsPicker.launch(arrayOf("image/*")) },
                        color = ComposeColor.White.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Select your social icons",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Image(
                        socialsBitmap!!.asImageBitmap(),
                        "Social icons",
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(previewSocialHeight)
                            .clickable { socialsPicker.launch(arrayOf("image/*")) },
                        // Social asset occupies the full 1080px canvas width and
                        // exactly the bottom 20% (270px) of the 1350px output.
                        contentScale = ContentScale.FillBounds
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
                ) { Text("Export 4:5 JPG", fontWeight = FontWeight.Bold) }

                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    mainBitmap = null
                    headline = ""
                    highlighted = emptySet()
                }) { Text("Clear", fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tap logo • + for main image • headline area for text • social strip for icons",
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

    @Composable
    private fun HeadlinePreview(
        headline: String,
        highlighted: Set<Int>,
        headlineHeight: androidx.compose.ui.unit.Dp,
        modifier: Modifier
    ) {
        if (headline.isBlank()) return

        val words = headline.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val annotated = buildAnnotatedString {
            words.forEachIndexed { index, word ->
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = if (index in highlighted) ComposeColor.Black else ComposeColor.White,
                        background = if (index in highlighted) ComposeColor.White else ComposeColor.Transparent,
                        fontWeight = FontWeight.Bold
                    )
                ) { append(word) }
                if (index < words.lastIndex) {
                    // Preserve the real space. When adjacent words are highlighted,
                    // the white highlight continues through that space so the blocks merge.
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = ComposeColor.White,
                            background = if (index in highlighted && (index + 1) in highlighted)
                                ComposeColor.White else ComposeColor.Transparent,
                            fontWeight = FontWeight.Bold
                        )
                    ) { append(" ") }
                }
            }
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val headlineHeightPx = with(density) { headlineHeight.toPx() }
        // The preview uses the same 4:5 geometry as the export: 18dp side margins
        // and exactly 20% of the thumbnail height for the headline.
        val sidePx = with(density) { 18.dp.toPx() }
        val screenWidthPx = with(density) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
        }
        val previewWidthPx = screenWidthPx - sidePx * 2f
        val metrics = dynamicHeadlineMetrics(headline, previewWidthPx, headlineHeightPx)
        val fontSp = with(density) { metrics.textSize.toSp() }
        val lineSp = with(density) { metrics.lineHeight.toSp() }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(headlineHeight)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ComposeColor.Transparent,
                                ComposeColor.Black.copy(alpha = 0.82f),
                                ComposeColor.Black.copy(alpha = 0.98f)
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = annotated,
                    color = ComposeColor.White,
                    fontSize = fontSp,
                    lineHeight = lineSp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    private data class HeadlineMetrics(
        val textSize: Float,
        val lineHeight: Float,
        val lineCount: Int
    )

    private fun dynamicHeadlineMetrics(
        text: String,
        maxWidth: Float,
        headlineAreaHeight: Float
    ): HeadlineMetrics {
        val clean = text.trim()
        if (clean.isEmpty()) return HeadlineMetrics(1f, headlineAreaHeight, 1)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        // Wrap only at word boundaries. Spaces are kept between words, and a line
        // is filled as far as possible before the next word moves to the next line.
        fun layoutFor(size: Float): StaticLayout {
            paint.textSize = size
            return StaticLayout.Builder.obtain(clean, 0, clean.length, paint, maxWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                .setLineSpacing(0f, 1f)
                .build()
        }

        // Find a size that fits the headline into the maximum four lines.
        var low = 8f
        var high = headlineAreaHeight
        repeat(20) {
            val mid = (low + high) / 2f
            val test = layoutFor(mid)
            if (test.lineCount <= 4) low = mid else high = mid
        }

        var finalSize = low
        var layout = layoutFor(finalSize)
        var lineCount = layout.lineCount

        // For the actual line count, each line gets an equal share of the 20% area.
        // Reduce only when glyph height would exceed that allocated line slot.
        repeat(20) {
            val allocated = headlineAreaHeight / lineCount.coerceAtLeast(1)
            paint.textSize = finalSize
            val glyphHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
            if (glyphHeight <= allocated) return@repeat
            finalSize *= 0.96f
            layout = layoutFor(finalSize)
            lineCount = layout.lineCount
        }

        layout = layoutFor(finalSize)
        lineCount = layout.lineCount.coerceIn(1, 4)

        return HeadlineMetrics(
            textSize = finalSize,
            lineHeight = headlineAreaHeight / lineCount,
            lineCount = lineCount
        )
    }


    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Tap any word to highlight it:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = Int.MAX_VALUE
                        ) {
                            words.forEachIndexed { index, word ->
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
                }
            },
            confirmButton = {
                TextButton(onClick = { onApply(text.trim(), selected) }) {
                    Text("Submit", fontWeight = FontWeight.Bold)
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
        // Reference output is 4:5, 1080x1350.
        val width = 1080
        val height = 1350
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Main image: cover-crop exactly like the preview.
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(
            source, null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        // Large top-left logo with a soft dark shadow.
        logo?.let {
            val maxLogo = 210f
            val s = minOf(maxLogo / it.width, maxLogo / it.height)
            val lw = it.width * s
            val lh = it.height * s
            val x = 48f
            val y = 48f
            // Create ONLY a local transparent shadow around the logo.
            // Never create/fill a full 1080x1350 black bitmap.
            val blurRadius = 16f
            val shadowPadding = 32f
            val localW = (lw + shadowPadding * 2f).toInt().coerceAtLeast(1)
            val localH = (lh + shadowPadding * 2f).toInt().coerceAtLeast(1)

            // The logo is a transparent PNG. Use its alpha channel directly;
            // never inspect its colors and never create a rectangular shadow.
            val logoMask = Bitmap.createBitmap(localW, localH, Bitmap.Config.ALPHA_8)
            val maskCanvas = Canvas(logoMask)
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            maskCanvas.drawBitmap(
                it, null,
                android.graphics.RectF(
                    shadowPadding,
                    shadowPadding,
                    shadowPadding + lw,
                    shadowPadding + lh
                ),
                maskPaint
            )

            val blurred = logoMask.extractAlpha(
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    maskFilter = android.graphics.BlurMaskFilter(
                        blurRadius,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                },
                null
            )

            // Black pixels are masked only by the blurred TDF silhouette.
            val shadow = Bitmap.createBitmap(localW, localH, Bitmap.Config.ARGB_8888)
            val shadowCanvas = Canvas(shadow)
            shadowCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            shadowCanvas.drawColor(Color.BLACK)
            blackPaint.xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN
            )
            shadowCanvas.drawBitmap(blurred, 0f, 0f, blackPaint)
            blackPaint.xfermode = null
            blackPaint.alpha = 145

            canvas.drawBitmap(
                shadow,
                x - shadowPadding,
                y - shadowPadding + 5f,
                blackPaint
            )

            shadow.recycle()
            blurred.recycle()
            logoMask.recycle()

            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                it, null,
                android.graphics.RectF(x, y, x + lw, y + lh),
                logoPaint
            )
        }

        // Reference-style black fade rising behind the headline.
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.48f, 0f, height * 0.92f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(190, 0, 0, 0),
                    Color.argb(250, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.44f, width.toFloat(), height.toFloat(), fade)

        // Social icons use the complete user-provided asset across the
        // full 1080px output width and the exact bottom 20% = 270px.
        // No crop or trim; the bitmap is drawn into the complete social area.
        socials?.let {
            val socialAreaTop = height * 0.80f
            canvas.drawBitmap(
                it,
                null,
                android.graphics.RectF(
                    0f,
                    socialAreaTop,
                    width.toFloat(),
                    height.toFloat()
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }

        val words = headline.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val fullText = words.joinToString(" ")
        // The headline owns exactly the bottom 20%: 270 px of the 1350 px output.
        // Line allocation is dynamic: 1 line = 100%, 2 = 50%, 3 = 33.33%, 4 = 25%.
        val socialAreaHeight = height * 0.05f
        val headlineAreaTop = height * 0.75f
        val headlineAreaHeight = height * 0.20f
        val textWidth = 1010
        val metrics = dynamicHeadlineMetrics(fullText, textWidth.toFloat(), headlineAreaHeight)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = metrics.textSize
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(metrics.lineHeight - (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent), 1f)
            .build()

        // Keep the headline 12.5% of the full canvas height above the bottom edge.
        // This leaves the social layer underneath/behind the lower part of the text.
        val textBottom = height * 0.875f
        val textTop = textBottom - metrics.lineHeight * metrics.lineCount

        canvas.save()
        canvas.translate((width - textWidth) / 2f, textTop)

        // Draw exact white highlight rectangles behind selected words.
        if (highlighted.isNotEmpty()) {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            var offset = 0
            var groupStartOffset = -1
            var groupEndOffset = -1
            var groupLine = -1

            fun drawGroup() {
                if (groupStartOffset < 0 || groupEndOffset < 0 || groupLine < 0) return
                val x1 = layout.getPrimaryHorizontal(groupStartOffset)
                val x2 = layout.getPrimaryHorizontal(groupEndOffset)
                canvas.drawRect(
                    minOf(x1, x2) - 5f,
                    layout.getLineTop(groupLine).toFloat(),
                    maxOf(x1, x2) + 5f,
                    layout.getLineBottom(groupLine).toFloat(),
                    highlightPaint
                )
                groupStartOffset = -1
                groupEndOffset = -1
                groupLine = -1
            }

            words.forEachIndexed { index, word ->
                val startOffset = offset
                val endOffset = startOffset + word.length
                val line = layout.getLineForOffset(startOffset)
                val isHighlighted = index in highlighted

                if (isHighlighted) {
                    if (groupStartOffset >= 0 && line == groupLine) {
                        // Include the real space between adjacent highlighted words.
                        groupEndOffset = endOffset
                    } else {
                        drawGroup()
                        groupStartOffset = startOffset
                        groupEndOffset = endOffset
                        groupLine = line
                    }
                } else {
                    drawGroup()
                }
                offset += word.length + 1
            }
            drawGroup()
        }

        layout.draw(canvas)

        // Redraw selected words in black on their white rectangles.
        if (highlighted.isNotEmpty()) {
            val blackPaint = TextPaint(textPaint).apply { color = Color.BLACK }
            var offset = 0
            words.forEachIndexed { index, word ->
                if (index in highlighted) {
                    val startOffset = offset
                    val line = layout.getLineForOffset(startOffset)
                    canvas.drawText(
                        word,
                        layout.getPrimaryHorizontal(startOffset),
                        layout.getLineBaseline(line).toFloat(),
                        blackPaint
                    )
                }
                offset += word.length + 1
            }
        }
        canvas.restore()

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
