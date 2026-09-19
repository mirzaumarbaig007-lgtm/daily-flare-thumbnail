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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object SimpleIconsLocal {
    val facebook get() = Facebook
    val instagram get() = Instagram
    val x get() = X
    val threads get() = Threads
    val pinterest get() = Pinterest
    val tumblr get() = Tumblr
    val youtube get() = Youtube
}

private enum class LogoPosition {
    LEFT,
    RIGHT,
    CENTER_BOTTOM
}

class MainActivity : ComponentActivity() {
    private val montserratExtraBoldTypeface: Typeface by lazy {
        val variableTypeface = Typeface.createFromAsset(assets, "Montserrat[wght].ttf")
        Typeface.create(variableTypeface, 800, false)
    }

    private val montserratExtraBoldFontFamily: FontFamily by lazy {
        FontFamily(montserratExtraBoldTypeface)
    }

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

    // Decode user-selected images safely. Phone photos can be 10K+ pixels and
    // decoding the full source bitmap can exhaust the app heap immediately after
    // the picker closes. We only need enough detail for the 1080x1350 output.
    private fun loadBitmap(uri: Uri?): Bitmap? {
        if (uri == null) return null

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDimension = 4096
            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > maxDimension ||
                bounds.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

    // The logo asset can contain a dark square/matte around white artwork.
    // Convert that matte to transparency before both preview and export so the
    // shadow is generated from the visible logo silhouette only.
    private fun cleanLogoBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        /*
         * The logo artwork is white. Do not use the logo's brightness as a
         * semi-transparent shadow or matte. Instead create a clean binary-ish
         * alpha silhouette: dark background = transparent, bright artwork =
         * opaque, with only a narrow transition for anti-aliased edges.
         */
        for (i in pixels.indices) {
            val p = pixels[i]
            val srcAlpha = Color.alpha(p)
            if (srcAlpha == 0) {
                pixels[i] = Color.TRANSPARENT
                continue
            }

            val luminance =
                0.299f * Color.red(p) +
                0.587f * Color.green(p) +
                0.114f * Color.blue(p)

            val alpha = when {
                luminance <= 100f -> 0f
                luminance >= 170f -> srcAlpha.toFloat()
                else -> srcAlpha * ((luminance - 100f) / 70f)
            }

            pixels[i] = Color.argb(
                alpha.toInt().coerceIn(0, 255),
                255,
                255,
                255
            )
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun loadLogoBitmap(uri: Uri?): Bitmap? = loadBitmap(uri)?.let(::cleanLogoBitmap)

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
        var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var headline by rememberSaveable { mutableStateOf("") }
        var highlighted by rememberSaveable { mutableStateOf(emptySet<Int>()) }
        var logoPosition by rememberSaveable { mutableStateOf(LogoPosition.LEFT) }
        var showTextPopup by remember { mutableStateOf(false) }

        val mainPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) mainBitmap = loadBitmap(uri)
        }
        val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistUri(uri, "logo_uri")
                logoUri = uri.toString()
                logoBitmap = loadLogoBitmap(uri)
            }
        }
        LaunchedEffect(logoUri) { logoBitmap = loadLogoBitmap(logoUri?.let(Uri::parse)) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Daily Flare Thumbnail", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // All four inputs are interactive areas inside the thumbnail preview.
            // There are no separate input buttons above the preview.
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ComposeColor(0xFFEAEAEA))
            ) {
                val previewHeadlineHeight = maxHeight * 0.20f

                if (mainBitmap == null) {
                    Button(
                        onClick = { mainPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("＋", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val previewBitmap = remember(
                        mainBitmap,
                        logoBitmap,
                        headline,
                        highlighted,
                        logoPosition
                    ) {
                        renderThumbnail(
                            mainBitmap!!,
                            logoBitmap,
                            headline.trim().ifBlank { "Daily Flare" },
                            highlighted,
                            logoPosition
                        )
                    }

                    Image(
                        previewBitmap.asImageBitmap(),
                        "Final thumbnail preview",
                        Modifier
                            .fillMaxSize()
                            .clickable { mainPicker.launch(arrayOf("image/*")) },
                        contentScale = ContentScale.FillBounds
                    )

                    when (logoPosition) {
                        LogoPosition.LEFT -> {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 12.dp)
                                    .size(150.dp)
                                    .zIndex(3f)
                                    .clickable { logoPicker.launch(arrayOf("image/*")) }
                            )
                        }
                        LogoPosition.RIGHT -> {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 12.dp, top = 12.dp)
                                    .size(150.dp)
                                    .zIndex(3f)
                                    .clickable { logoPicker.launch(arrayOf("image/*")) }
                            )
                        }
                        LogoPosition.CENTER_BOTTOM -> {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(previewHeadlineHeight * 0.30f)
                                    .offset(y = -(previewHeadlineHeight + 4.dp))
                                    .zIndex(3f)
                                    .clickable { logoPicker.launch(arrayOf("image/*")) }
                            )
                        }
                    }

                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(previewHeadlineHeight)
                            .offset(y = -(maxHeight * 0.05f))
                            .zIndex(2f)
                            .clickable { showTextPopup = true }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Logo position", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = logoPosition == LogoPosition.LEFT,
                    onClick = { logoPosition = LogoPosition.LEFT },
                    label = { Text("Left") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = logoPosition == LogoPosition.RIGHT,
                    onClick = { logoPosition = LogoPosition.RIGHT },
                    label = { Text("Right") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = logoPosition == LogoPosition.CENTER_BOTTOM,
                    onClick = { logoPosition = LogoPosition.CENTER_BOTTOM },
                    label = { Text("Bottom") }
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    enabled = mainBitmap != null,
                    onClick = {
                        mainBitmap?.let {
                            val title = headline.trim().ifBlank { "Daily Flare" }
                            saveThumbnail(
                                renderThumbnail(it, logoBitmap, title, highlighted, logoPosition),
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
    private fun SocialIconsRow(
        modifier: Modifier,
        iconSizePx: Float
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val iconSize = with(density) { iconSizePx.toDp() }
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            Icon(SimpleIconsLocal.facebook, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.instagram, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.x, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.threads, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.pinterest, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.tumblr, null, Modifier.size(iconSize), tint = ComposeColor.White)
            Icon(SimpleIconsLocal.youtube, null, Modifier.size(iconSize), tint = ComposeColor.White)
        }
    }

    private fun renderSocialIconsBitmap(width: Int, height: Int): Bitmap {
        // Export the same bundled vector artwork without creating a ComposeView.
        // This avoids lifecycle/window dependencies that can crash during export.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.social_icons_white)
            ?: return bitmap
        val iconHeight = 34
        val iconWidth = (504f * iconHeight / 24f).toInt()
        drawable.setBounds(
            (width - iconWidth) / 2,
            height - iconHeight,
            (width - iconWidth) / 2 + iconWidth,
            height
        )
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Composable
    private fun HeadlinePreview(
        headline: String,
        highlighted: Set<Int>,
        headlineHeight: androidx.compose.ui.unit.Dp,
        modifier: Modifier
    ) {
        if (headline.isBlank()) return

        val density = androidx.compose.ui.platform.LocalDensity.current
        val headlineHeightPx = with(density) { headlineHeight.toPx() }
        val sidePx = with(density) { 18.dp.toPx() }
        val screenWidthPx = with(density) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
        }
        val previewWidthPx = screenWidthPx - sidePx * 2f

        val wrappedHeadline = wrapHeadlineText(headline, previewWidthPx, headlineHeightPx)
        val matches = Regex("\\S+").findAll(wrappedHeadline).toList()

        val annotated = buildAnnotatedString {
            matches.forEachIndexed { index, match ->
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = if (index in highlighted) ComposeColor.Black else ComposeColor.White,
                        background = if (index in highlighted) ComposeColor.White else ComposeColor.Transparent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = montserratExtraBoldFontFamily
                    )
                ) { append(match.value) }

                if (index < matches.lastIndex) {
                    val nextStart = matches[index + 1].range.first
                    val separator = wrappedHeadline.substring(match.range.last + 1, nextStart)
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = ComposeColor.White,
                            background = if (index in highlighted && (index + 1) in highlighted)
                                ComposeColor.White else ComposeColor.Transparent,
                            fontWeight = FontWeight.Bold
                        )
                    ) { append(separator) }
                }
            }
        }

        val metrics = dynamicHeadlineMetrics(wrappedHeadline, previewWidthPx, headlineHeightPx)
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
                                ComposeColor.Black.copy(alpha = 0.58f),
                                ComposeColor.Black.copy(alpha = 0.96f)
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
                    fontFamily = montserratExtraBoldFontFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                )
            }
        }
    }

    private data class HeadlineMetrics(
        val textSize: Float,
        val lineHeight: Float,
        val lineCount: Int
    )

    /*
     * Choose the line count from the ACTUAL final font size, not from a
     * tiny reference font. This was the reason the old version produced
     * nearly the same-sized headline regardless of line count.
     *
     * The 20% headline zone is 270 px at export size:
     *   1 line = 100% of the zone
     *   2 lines = 50%
     *   3 lines = 33.33%
     *   4 lines = 24%
     *
     * We test those real sizes from one line upward and keep the first size
     * that can contain the complete headline. StaticLayout still breaks only
     * at word boundaries and hyphenation remains disabled.
     */
    private fun wrapHeadlineText(
        text: String,
        maxWidth: Float,
        headlineAreaHeight: Float
    ): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return ""

        val words = clean.split(" ").filter { it.isNotEmpty() }

        /*
         * Choose the line breaks using the REAL target font size for each
         * possible line count. The old reference-size wrap could decide that
         * "$24.3" did not fit on line 1 and push it onto a separate line.
         *
         * We now test 1..4 lines and, for each count, find the word-boundary
         * arrangement with the most balanced line widths. This lets:
         *
         *   U.S. Approves Potential $24.3
         *   Billion F-35 Sale to Saudi
         *   Arabia
         *
         * stay together instead of isolating "$24.3".
         */
        fun targetTextSize(lineCount: Int): Float {
            val fraction = when (lineCount) {
                1 -> 1f
                2 -> 0.50f
                3 -> 1f / 3f
                else -> 0.24f
            }
            return headlineAreaHeight * fraction
        }

        fun paintFor(size: Float): TextPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = montserratExtraBoldTypeface
                textSize = size
            }

        fun bestWrapFor(lineCount: Int): String? {
            if (words.size < lineCount) return null

            val paint = paintFor(targetTextSize(lineCount))
            val n = words.size
            val widths = FloatArray(n)
            for (i in 0 until n) {
                widths[i] = paint.measureText(words[i])
            }

            // prefix width includes one normal space between adjacent words.
            val prefix = FloatArray(n + 1)
            for (i in 0 until n) {
                prefix[i + 1] = prefix[i] + widths[i] + if (i == 0) 0f else paint.measureText(" ")
            }

            fun lineWidth(from: Int, to: Int): Float =
                prefix[to] - prefix[from] -
                    if (from > 0) paint.measureText(" ") else 0f

            // Dynamic programming: minimize squared raggedness while keeping
            // every line within the actual text width.
            val inf = Double.POSITIVE_INFINITY
            val cost = Array(lineCount + 1) { DoubleArray(n + 1) { inf } }
            val previous = Array(lineCount + 1) { IntArray(n + 1) { -1 } }
            cost[0][0] = 0.0

            for (line in 1..lineCount) {
                for (endWord in line..n) {
                    for (startWord in (line - 1) until endWord) {
                        if (!cost[line - 1][startWord].isFinite()) continue
                        val width = lineWidth(startWord, endWord)
                        if (width > maxWidth) continue

                        val slack = (maxWidth - width).toDouble()
                        // Squared slack discourages one very short line while
                        // still allowing natural word-boundary wrapping.
                        val candidate = cost[line - 1][startWord] + slack * slack
                        if (candidate < cost[line][endWord]) {
                            cost[line][endWord] = candidate
                            previous[line][endWord] = startWord
                        }
                    }
                }
            }

            if (!cost[lineCount][n].isFinite()) return null

            val ranges = ArrayList<IntRange>(lineCount)
            var endWord = n
            for (line in lineCount downTo 1) {
                val startWord = previous[line][endWord]
                if (startWord < 0) return null
                ranges += startWord until endWord
                endWord = startWord
            }
            ranges.reverse()

            return ranges.joinToString("\n") { range ->
                words.subList(range.first, range.last + 1).joinToString(" ")
            }
        }

        // Prefer the fewest lines that can fit at the intended design size.
        // This is what keeps medium headlines from being unnecessarily pushed
        // into four lines just because of a smaller reference-font wrap.
        for (lineCount in 1..4) {
            bestWrapFor(lineCount)?.let { return it }
        }

        // Last-resort four-line word-boundary wrap. This is only reached for
        // unusually long headlines that cannot fit even at the four-line target.
        val fallbackPaint = paintFor(targetTextSize(4))
        val fallback = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && fallbackPaint.measureText(candidate) > maxWidth) {
                fallback += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) fallback += current
        return fallback.take(4).joinToString("\n")
    }

    private fun dynamicHeadlineMetrics(
        text: String,
        maxWidth: Float,
        headlineAreaHeight: Float
    ): HeadlineMetrics {
        val clean = text.trim()
        if (clean.isEmpty()) return HeadlineMetrics(1f, headlineAreaHeight, 1)

        val lineCount = clean.count { it == '\n' }.plus(1).coerceIn(1, 4)
        val fraction = when (lineCount) {
            1 -> 1f
            2 -> 0.50f
            3 -> 1f / 3f
            else -> 0.24f
        }

        // Requested size is the design target. It is never allowed to make the
        // complete headline overflow its hard 20% zone.
        val requestedSize = headlineAreaHeight * fraction

        fun measuredHeight(size: Float): Float {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = montserratExtraBoldTypeface
                textSize = size
            }
            val layout = StaticLayout.Builder.obtain(
                clean, 0, clean.length, paint, maxWidth.toInt()
            )
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                .setLineSpacing(0f, 1f)
                .build()
            return layout.height.toFloat()
        }

        // Binary-search the largest size that keeps every line inside the zone.
        var low = 8f
        var high = requestedSize.coerceAtLeast(low)
        repeat(12) {
            val mid = (low + high) / 2f
            if (measuredHeight(mid) <= headlineAreaHeight) {
                low = mid
            } else {
                high = mid
            }
        }

        val textSize = low
        val lineHeight = headlineAreaHeight / lineCount

        return HeadlineMetrics(
            textSize = textSize,
            lineHeight = lineHeight,
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
                        onValueChange = { value ->
                            text = value
                            val count = value.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                            selected = selected.filter { it < count }.toSet()
                        },
                        modifier = Modifier.fillMaxWidth(),
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
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            maxItemsInEachRow = Int.MAX_VALUE
                        ) {
                            words.forEachIndexed { index, word ->
                                val isSelected = index in selected
                                Surface(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clickable {
                                            selected = if (isSelected) selected - index else selected + index
                                        },
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        ComposeColor.Transparent
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            ComposeColor(0xFF85808A)
                                        }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = word,
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
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
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    private fun drawLogoWithShadow(
        canvas: Canvas,
        logo: Bitmap,
        x: Float,
        y: Float,
        maxLogo: Float
    ) {
        val scaleLogo = minOf(maxLogo / logo.width, maxLogo / logo.height)
        val lw = logo.width * scaleLogo
        val lh = logo.height * scaleLogo

        // Build every shadow pixel from the logo's actual alpha silhouette.
        // Nothing is blurred from the rectangular image bounds.
        val padding = 44f
        val localW = (lw + padding * 2f).toInt().coerceAtLeast(1)
        val localH = (lh + padding * 2f).toInt().coerceAtLeast(1)

        val mask = Bitmap.createBitmap(localW, localH, Bitmap.Config.ALPHA_8)
        Canvas(mask).drawBitmap(
            logo,
            null,
            android.graphics.RectF(padding, padding, padding + lw, padding + lh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val shadow = Bitmap.createBitmap(localW, localH, Bitmap.Config.ARGB_8888)
        val shadowCanvas = Canvas(shadow)
        shadowCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        // Tight contact shadow: noticeably dark immediately behind the artwork.
        val contactShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 220
            maskFilter = android.graphics.BlurMaskFilter(
                6f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        shadowCanvas.drawBitmap(mask, 1f, 2f, contactShadow)

        // Wider halo: black, but progressively dissolving into the photo.
        val outerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 185
            maskFilter = android.graphics.BlurMaskFilter(
                18f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        shadowCanvas.drawBitmap(mask, 2f, 3f, outerShadow)

        canvas.drawBitmap(
            shadow,
            x - padding,
            y - padding,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        // The real logo is always the final layer of this helper.
        canvas.drawBitmap(
            logo,
            null,
            android.graphics.RectF(x, y, x + lw, y + lh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        shadow.recycle()
        mask.recycle()
    }

    private fun drawCenterLogoFeature(
        canvas: Canvas,
        logo: Bitmap,
        width: Int,
        centerY: Float
    ) {
        val maxLogo = 42f
        val scaleLogo = minOf(maxLogo / logo.width, maxLogo / logo.height)
        val lw = logo.width * scaleLogo
        val lh = logo.height * scaleLogo
        val x = (width - lw) / 2f
        val y = centerY - lh / 2f

        drawLogoWithShadow(canvas, logo, x, y, maxLogo)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 215
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val lineGap = 34f
        val sidePadding = 40f
        canvas.drawLine(
            sidePadding,
            centerY,
            width / 2f - lineGap,
            centerY,
            linePaint
        )
        canvas.drawLine(
            width / 2f + lineGap,
            centerY,
            width - sidePadding,
            centerY,
            linePaint
        )
    }

    private fun renderThumbnail(
        source: Bitmap,
        logo: Bitmap?,
        headline: String,
        highlighted: Set<Int>,
        logoPosition: LogoPosition
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

        // Headline fade: begins subtly at 60%, transitions through 75%,
        // becomes strongly black from roughly 78% and reaches near-solid black by 90%.
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.60f, 0f, height * 0.90f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(75, 0, 0, 0),
                    Color.argb(165, 0, 0, 0),
                    Color.argb(238, 0, 0, 0),
                    Color.argb(250, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.50f, 0.65f, 0.80f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.60f, width.toFloat(), height.toFloat(), fade)

        // Draw the same bundled vector icons used by the preview.
        // The icon row is rendered locally into a transparent bitmap only for
        // the final JPG export; there is no network dependency.
        val socialIcons = renderSocialIconsBitmap(width, (height * 0.05f).toInt())
        canvas.drawBitmap(
            socialIcons,
            0f,
            height - socialIcons.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        socialIcons.recycle()

        val fullText = wrapHeadlineText(headline, 1010f, 270f)
        val wordMatches = Regex("\\S+").findAll(fullText).toList()
        val words = wordMatches.map { it.value }
        // The headline owns exactly the bottom 20%: 270 px of the 1350 px output.
        // Line allocation is dynamic: 1 line = 100%, 2 = 50%, 3 = 33.33%, 4 = 24%.
        val socialAreaHeight = height * 0.05f
        val headlineAreaTop = height * 0.75f
        val headlineAreaHeight = height * 0.20f
        val textWidth = 1010
        // Use the full 20% headline zone for sizing, but position the finished
        // block immediately above the bottom social icons.
        val metrics = dynamicHeadlineMetrics(fullText, textWidth.toFloat(), headlineAreaHeight)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = metrics.textSize
            typeface = montserratExtraBoldTypeface
        }

        val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

        // Keep the headline as one compact block immediately above the social icons.
        // Do not distribute the text across the whole 20% zone; the 20% zone
        // controls the font size, while this anchor controls the final position.
        val headlineZoneBottom = height - socialAreaHeight
        val headlineZoneTop = headlineZoneBottom - headlineAreaHeight
        val layoutHeight = layout.height.toFloat()
        val textTop = headlineZoneTop + maxOf(0f, (headlineAreaHeight - layoutHeight) / 2f)

        canvas.save()
        canvas.translate((width - textWidth) / 2f, textTop)

        // Draw tight white highlight rectangles behind the actual glyph runs.
        // Adjacent highlighted words on the same line are merged, including their
        // real intervening space. A wrapped word starts a new rectangle.
        if (highlighted.isNotEmpty()) {
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            var offset = 0
            var groupLeft = 0f
            var groupRight = 0f
            var groupLine = -1
            var hasGroup = false

            fun wordRight(start: Int, end: Int): Float {
                if (end <= start) return layout.getPrimaryHorizontal(start)
                val last = end - 1
                return layout.getPrimaryHorizontal(last) + textPaint.measureText(fullText[last].toString())
            }

            fun flushGroup() {
                if (!hasGroup || groupLine < 0) return
                // Use the same glyph-based vertical box for every line.
                // StaticLayout lineTop/lineBottom can include line-specific font
                // metrics, which made the first highlighted line visibly taller.
                val baseline = layout.getLineBaseline(groupLine).toFloat()
                val fm = textPaint.fontMetrics
                val verticalPad = 0.5f
                val boxTop = baseline + fm.ascent - verticalPad
                val boxBottom = baseline + fm.descent + verticalPad
                canvas.drawRect(
                    groupLeft - 6f,
                    boxTop,
                    groupRight + 6f,
                    boxBottom,
                    highlightPaint
                )
                hasGroup = false
                groupLine = -1
            }

            wordMatches.forEachIndexed { index, match ->
                val startOffset = match.range.first
                val endOffset = match.range.last + 1
                val line = layout.getLineForOffset(startOffset)

                if (index in highlighted) {
                    val left = layout.getPrimaryHorizontal(startOffset)
                    val right = wordRight(startOffset, endOffset)

                    if (hasGroup && line == groupLine) {
                        groupRight = maxOf(groupRight, right)
                    } else {
                        flushGroup()
                        groupLeft = minOf(left, right)
                        groupRight = maxOf(left, right)
                        groupLine = line
                        hasGroup = true
                    }
                } else {
                    flushGroup()
                }
            }
            flushGroup()
        }

        layout.draw(canvas)

        // Redraw selected words in black exactly where StaticLayout placed them.
        if (highlighted.isNotEmpty()) {
            val blackPaint = TextPaint(textPaint).apply { color = Color.BLACK }
            wordMatches.forEachIndexed { index, match ->
                if (index in highlighted) {
                    val startOffset = match.range.first
                    val line = layout.getLineForOffset(startOffset)
                    val x = layout.getPrimaryHorizontal(startOffset)
                    canvas.drawText(
                        match.value,
                        x,
                        layout.getLineBaseline(line).toFloat(),
                        blackPaint
                    )
                }
            }
        }
        canvas.restore()


        // Logo is deliberately rendered last so it is above every other export layer.
        // The shadow remains silhouette-based and therefore can never create a square box.
        logo?.let {
            when (logoPosition) {
                LogoPosition.LEFT -> drawLogoWithShadow(canvas, it, 48f, 48f, 210f)
                LogoPosition.RIGHT -> {
                    val maxLogo = 210f
                    val scaleLogo = minOf(maxLogo / it.width, maxLogo / it.height)
                    val lw = it.width * scaleLogo
                    drawLogoWithShadow(canvas, it, width - 48f - lw, 48f, maxLogo)
                }
                LogoPosition.CENTER_BOTTOM -> {
                    val featureCenterY = height * 0.725f
                    drawCenterLogoFeature(canvas, it, width, featureCenterY)
                }
            }
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