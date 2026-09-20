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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private object SimpleIconsLocal {
    val facebook get() = Facebook
    val instagram get() = Instagram
    val x get() = X
    val threads get() = Threads
    val pinterest get() = Pinterest
    val tumblr get() = Tumblr
    val youtube get() = Youtube
}

private data class RssArticle(
    val title: String,
    val link: String,
    val imageUrl: String?
)

private enum class LogoPosition {
    LEFT,
    RIGHT,
    CENTER_BOTTOM
}

class MainActivity : ComponentActivity() {
    private val montserratExtraBoldTypeface: Typeface by lazy {
        val variableTypeface = Typeface.createFromAsset(assets, "Montserrat[wght].ttf")
        Typeface.create(variableTypeface, 900, false)
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
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDimension = 1024
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
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

    private fun loadLogoBitmap(uri: Uri?): Bitmap? {
        val source = loadBitmap(uri) ?: return null
        return try {
            val maxLogoDimension = 512
            val scale = minOf(
                1f,
                maxLogoDimension.toFloat() / source.width.toFloat(),
                maxLogoDimension.toFloat() / source.height.toFloat()
            )
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== source) source.recycle() }
            } else source
            cleanLogoBitmap(resized)
        } catch (_: Throwable) {
            null
        }
    }

    private fun openHttpConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DailyFlareThumbnail/1.0")
        }
    }

    private fun fetchRssArticles(): List<RssArticle> {
        val connection = openHttpConnection("https://thedailyflare.com/feed/")
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return emptyList()
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(connection.inputStream, null)
            val articles = mutableListOf<RssArticle>()
            var event = parser.eventType
            var insideItem = false
            var title = ""
            var link = ""
            var imageUrl: String? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                        "item", "entry" -> {
                            insideItem = true
                            title = ""
                            link = ""
                            imageUrl = null
                        }
                        "title" -> if (insideItem) title = parser.nextText().trim()
                        "link" -> if (insideItem) {
                            val href = parser.getAttributeValue(null, "href")
                            link = (href ?: parser.nextText()).trim()
                        }
                        "media:content", "content:content", "media:thumbnail" -> if (insideItem && imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        "enclosure" -> if (insideItem && imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")?.trim()?.takeIf { it.isNotEmpty() }
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("item", true) || parser.name.equals("entry", true)) {
                        if (title.isNotBlank() && link.isNotBlank()) articles += RssArticle(title.take(240), link, imageUrl)
                        if (articles.size >= 30) return articles
                        insideItem = false
                    }
                }
                event = parser.next()
            }
            articles
        } finally {
            connection.disconnect()
        }
    }

    private fun findFeaturedImageUrl(article: RssArticle): String? {
        article.imageUrl?.let { return it }
        if (article.link.isBlank()) return null
        val connection = openHttpConnection(article.link)
        return try {
            connection.connect()
            if (connection.responseCode !in 200..399) return null
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                var total = 0
                while (total < 1_000_000) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    out.append(buffer, 0, read)
                    total += read
                }
                out.toString()
            }
            val patterns = listOf(
                Regex("<meta[^>]+property=[\\\"']og:image[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
                Regex("<meta[^>]+name=[\\\"']twitter:image[\\\"'][^>]+content=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE),
                Regex("<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+property=[\\\"']og:image[\\\"']", RegexOption.IGNORE_CASE)
            )
            patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1)?.trim() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val boundsConnection = openHttpConnection(url)
            boundsConnection.connect()
            if (boundsConnection.responseCode !in 200..299) {
                boundsConnection.disconnect()
                return null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            boundsConnection.inputStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            boundsConnection.disconnect()
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDimension = 768
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val imageConnection = openHttpConnection(url)
            imageConnection.connect()
            if (imageConnection.responseCode !in 200..299) {
                imageConnection.disconnect()
                return null
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            imageConnection.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
                .also { imageConnection.disconnect() }
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }

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
        var rssArticles by remember { mutableStateOf<List<RssArticle>>(emptyList()) }
        var rssLoading by remember { mutableStateOf(true) }
        var rssError by remember { mutableStateOf<String?>(null) }
        var showRssDialog by remember { mutableStateOf(false) }
        var selectedArticleTitle by rememberSaveable { mutableStateOf<String?>(null) }
        val exportScope = rememberCoroutineScope()

        val mainPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = loadBitmap(uri)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            mainBitmap = bitmap
                        } else {
                            Toast.makeText(this@MainActivity, "Could not load that image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                persistUri(uri, "logo_uri")
                logoUri = uri.toString()
            }
        }
        LaunchedEffect(logoUri) {
            val uri = logoUri?.let(Uri::parse) ?: return@LaunchedEffect
            val bitmap = withContext(Dispatchers.IO) { loadLogoBitmap(uri) }
            logoBitmap = bitmap
        }


        LaunchedEffect(Unit) {
            rssLoading = true
            rssError = null
            try {
                rssArticles = withContext(Dispatchers.IO) { fetchRssArticles() }
                if (rssArticles.isEmpty()) rssError = "No Daily Flare articles found."
            } catch (e: Exception) {
                rssError = "Could not load Daily Flare RSS."
            } finally {
                rssLoading = false
            }
        }

        fun chooseArticle(article: RssArticle) {
            val safeTitle = article.title.take(240).trim()
            selectedArticleTitle = safeTitle
            headline = safeTitle
            showRssDialog = false
            mainBitmap = null
            headline = safeTitle
            exportScope.launch(Dispatchers.IO) {
                val imageUrl = findFeaturedImageUrl(article)
                val bitmap = downloadBitmap(imageUrl)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        mainBitmap = bitmap
                    } else {
                        Toast.makeText(this@MainActivity, "Could not load the article image. You can choose one from Gallery.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

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
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { showRssDialog = true }) {
                            Text("Select Daily Flare Article", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { mainPicker.launch(arrayOf("image/*")) }) {
                            Text("Choose Image from Gallery")
                        }
                    }
                } else {
                    Image(
                        mainBitmap!!.asImageBitmap(),
                        "Final thumbnail preview",
                        Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                            .clickable { mainPicker.launch(arrayOf("image/*")) },
                        contentScale = ContentScale.FillBounds
                    )

                    // Keep editable overlays above the background image.
                    if (logoBitmap != null) {
                        Image(
                            logoBitmap!!.asImageBitmap(),
                            "Logo",
                            Modifier
                                .then(
                                    when (logoPosition) {
                                        LogoPosition.LEFT -> Modifier
                                            .align(Alignment.TopStart)
                                            .padding(start = 18.dp, top = 18.dp)
                                        LogoPosition.RIGHT -> Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 18.dp, top = 18.dp)
                                        LogoPosition.CENTER_BOTTOM -> Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = -(previewHeadlineHeight + 10.dp))
                                    }
                                )
                                .size(92.dp)
                                .zIndex(4f)
                                .clickable { logoPicker.launch(arrayOf("image/*")) },
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Lightweight live headline overlay. Keep text editing independent
                    // from the bitmap renderer so submitting text cannot trigger a large
                    // StaticLayout/renderThumbnail allocation.
                    if (headline.isNotBlank()) {
                        val safeHeadline = headline.take(240)
                        val displayWords = safeHeadline.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val annotatedHeadline = buildAnnotatedString {
                            displayWords.forEachIndexed { index, word ->
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        color = if (index in highlighted) ComposeColor.Black else ComposeColor.White,
                                        background = if (index in highlighted) ComposeColor.White else ComposeColor.Transparent,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                ) { append(word) }
                                if (index < displayWords.lastIndex) append(" ")
                            }
                        }
                        Text(
                            text = annotatedHeadline,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(previewHeadlineHeight)
                                .offset(y = -(maxHeight * 0.05f))
                                .zIndex(2f)
                                .clickable { showTextPopup = true }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            fontSize = 28.sp,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                        val source = mainBitmap ?: return@Button
                        val title = headline.trim().ifBlank { "Daily Flare" }
                        exportScope.launch(Dispatchers.Default) {
                            // Export from a fresh background render. Never depend on the
                            // Compose preview bitmap, which can be stale or unavailable.
                            val bitmapToSave = try {
                                // Use the crash-safe Canvas renderer for the final image.
                                // It intentionally avoids StaticLayout/font-asset initialization
                                // while preserving word highlights and social icons.
                                renderSafeThumbnail(source, logoBitmap, title, highlighted, logoPosition)
                            } catch (_: Throwable) {
                                try {
                                    renderFallbackThumbnail(source, logoBitmap, title, logoPosition)
                                } catch (_: Throwable) {
                                    null
                                }
                            }

                            if (bitmapToSave == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Export could not be rendered. Try selecting the image again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@launch
                            }

                            saveThumbnail(bitmapToSave, title)
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
                "Tap image to replace it • headline area to edit text • logo to replace it",
                fontSize = 12.sp
            )
        }

        if (showRssDialog) {
            AlertDialog(
                onDismissRequest = { showRssDialog = false },
                title = { Text("Daily Flare Articles", fontWeight = FontWeight.Bold) },
                text = {
                    when {
                        rssLoading -> Text("Loading latest articles…")
                        rssArticles.isEmpty() -> Text(rssError ?: "No articles available.")
                        else -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            rssArticles.forEach { article ->
                                TextButton(
                                    onClick = { chooseArticle(article) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(article.title, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            rssLoading = true
                            rssError = null
                            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    rssArticles = withContext(Dispatchers.IO) { fetchRssArticles() }
                                    if (rssArticles.isEmpty()) rssError = "No Daily Flare articles found."
                                } catch (_: Exception) {
                                    rssError = "Could not load Daily Flare RSS."
                                } finally {
                                    rssLoading = false
                                }
                            }
                        }) { Text("Refresh") }
                        TextButton(onClick = { mainPicker.launch(arrayOf("image/*")); showRssDialog = false }) { Text("Gallery") }
                    }
                },
                dismissButton = { TextButton(onClick = { showRssDialog = false }) { Text("Cancel") } }
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
        var text by remember { mutableStateOf(initialText.take(240)) }
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
                            text = value.take(240)
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

    private fun renderSafeThumbnail(
        source: Bitmap,
        logo: Bitmap?,
        headline: String,
        highlighted: Set<Int>,
        logoPosition: LogoPosition
    ): Bitmap {
        // Stable final renderer: simple Canvas text layout only.
        // This keeps export independent from StaticLayout and the variable font asset.
        val width = 1080
        val height = 1350
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.58f, 0f, height.toFloat(),
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(120, 0, 0, 0),
                    Color.argb(220, 0, 0, 0),
                    Color.argb(252, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.30f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.58f, width.toFloat(), height.toFloat(), fade)

        // Social icons are part of the exported image, not just the preview.
        try {
            val socialIcons = renderSocialIconsBitmap(width, 72)
            canvas.drawBitmap(
                socialIcons,
                0f,
                height - socialIcons.height.toFloat() - (height * 0.02f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            socialIcons.recycle()
        } catch (_: Throwable) {
            // Keep export usable if a device cannot load the bundled icon drawable.
        }

        val words = headline.take(240).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Use almost the full canvas width. The headline should adapt to the amount
        // of text instead of being trapped in a narrow fixed 900px column.
        // 1025px leaves about 2.5% margin on each side of the 1080px canvas.
        val textWidth = width * 0.949f
        val headlineTop = height * 0.700f
        val headlineBottom = height * 0.950f
        val maxLines = 4

        fun makeLines(textSize: Float): List<List<Int>> {
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSize
                typeface = try {
                    montserratExtraBoldTypeface
                } catch (_: Throwable) {
                    Typeface.create("sans-serif", Typeface.BOLD)
                }
            }
            val lines = mutableListOf<MutableList<Int>>()
            var current = mutableListOf<Int>()
            var currentWidth = 0f

            words.forEachIndexed { index, word ->
                val wordWidth = measurePaint.measureText(word)
                val spaceWidth = if (current.isEmpty()) 0f else measurePaint.measureText(" ")
                if (current.isNotEmpty() && currentWidth + spaceWidth + wordWidth > textWidth) {
                    lines += current
                    current = mutableListOf(index)
                    currentWidth = wordWidth
                } else {
                    current += index
                    currentWidth += spaceWidth + wordWidth
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        // Find the largest size that both:
        // 1) uses the available width naturally (up to ~95%), and
        // 2) fits within the headline area without forcing an arbitrary font size.
        var textSize = 54f
        var lines = makeLines(textSize)
        for (candidate in 110 downTo 54) {
            val candidateLines = makeLines(candidate.toFloat())
            val candidateLineHeight = candidate * 1.10f
            val candidateBlockHeight = candidateLines.size * candidateLineHeight
            if (candidateLines.size <= maxLines &&
                candidateBlockHeight <= (headlineBottom - headlineTop) * 1.08f
            ) {
                textSize = candidate.toFloat()
                lines = candidateLines
                break
            }
        }
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).map { it.toMutableList() }
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = try {
                montserratExtraBoldTypeface
            } catch (_: Throwable) {
                Typeface.create("sans-serif", Typeface.BOLD)
            }
            this.textSize = textSize
            textAlign = Paint.Align.LEFT
        }

        val lineHeight = textSize * 1.10f
        val blockHeight = lines.size * lineHeight
        val startBaseline = headlineTop + ((headlineBottom - headlineTop) - blockHeight) / 2f - textPaint.ascent()

        lines.forEachIndexed { lineIndex, lineWords ->
            var lineWidth = 0f
            lineWords.forEachIndexed { position, wordIndex ->
                if (position > 0) lineWidth += textPaint.measureText(" ")
                lineWidth += textPaint.measureText(words[wordIndex])
            }

            var x = (width - lineWidth) / 2f
            val baseline = startBaseline + lineIndex * lineHeight
            lineWords.forEachIndexed { position, wordIndex ->
                if (position > 0) x += textPaint.measureText(" ")

                val word = words[wordIndex]
                val wordWidth = textPaint.measureText(word)
                if (wordIndex in highlighted) {
                    // Keep adjacent highlighted words visually separate while preserving
                    // the normal word-space between them. The old padding made neighboring
                    // highlight boxes touch and made the boxes too tall between lines.
                    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                    }
                    val horizontalPadding = 6f
                    val verticalPadding = 4f
                    canvas.drawRoundRect(
                        x - horizontalPadding,
                        baseline + textPaint.ascent() + verticalPadding,
                        x + wordWidth + horizontalPadding,
                        baseline + textPaint.descent() - verticalPadding,
                        11f,
                        11f,
                        highlightPaint
                    )
                    textPaint.color = Color.BLACK
                } else {
                    textPaint.color = Color.WHITE
                }
                canvas.drawText(word, x, baseline, textPaint)
                x += wordWidth
            }
        }

        // Logo remains the top/final branding layer, as before.
        logo?.let {
            try {
                when (logoPosition) {
                    LogoPosition.LEFT -> drawLogoWithShadow(canvas, it, 48f, 48f, 210f)
                    LogoPosition.RIGHT -> {
                        val maxLogo = 210f
                        val scaleLogo = minOf(maxLogo / it.width, maxLogo / it.height)
                        val lw = it.width * scaleLogo
                        drawLogoWithShadow(canvas, it, width - 48f - lw, 48f, maxLogo)
                    }
                    LogoPosition.CENTER_BOTTOM -> {
                        drawCenterLogoFeature(canvas, it, width, height * 0.725f)
                    }
                }
            } catch (_: Throwable) {
                // Simple logo fallback; never let branding break export.
                try {
                    val maxLogo = 210f
                    val scaleLogo = minOf(maxLogo / it.width, maxLogo / it.height)
                    val lw = it.width * scaleLogo
                    val lh = it.height * scaleLogo
                    val x = when (logoPosition) {
                        LogoPosition.LEFT -> 48f
                        LogoPosition.RIGHT -> width - 48f - lw
                        LogoPosition.CENTER_BOTTOM -> (width - lw) / 2f
                    }
                    val y = when (logoPosition) {
                        LogoPosition.CENTER_BOTTOM -> height * 0.725f - lh / 2f
                        else -> 48f
                    }
                    canvas.drawBitmap(
                        it,
                        null,
                        android.graphics.RectF(x, y, x + lw, y + lh),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                } catch (_: Throwable) {}
            }
        }

        return output
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

    private fun renderFallbackThumbnail(
        source: Bitmap,
        logo: Bitmap?,
        headline: String,
        logoPosition: LogoPosition
    ): Bitmap {
        // Last-resort renderer: deliberately uses only simple Canvas operations.
        // This keeps the preview/export usable even if StaticLayout, vector icons,
        // or a font resource fails on a particular device.
        val width = 1080
        val height = 1350
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)

        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val fade = Paint().apply {
            shader = LinearGradient(
                0f, height * 0.58f, 0f, height.toFloat(),
                Color.TRANSPARENT,
                Color.argb(235, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height * 0.58f, width.toFloat(), height.toFloat(), fade)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val words = headline.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""
        val maxWidth = width * 0.90f
        val textSize = 86f
        paint.textSize = textSize

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        val visibleLines = lines.takeLast(4)
        val lineHeight = 92f
        val startY = height - 115f - (visibleLines.size - 1) * lineHeight

        visibleLines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, startY + index * lineHeight, paint)
        }

        logo?.let {
            try {
                val maxLogo = 210f
                val scaleLogo = minOf(maxLogo / it.width, maxLogo / it.height)
                val lw = it.width * scaleLogo
                val lh = it.height * scaleLogo
                val x = when (logoPosition) {
                    LogoPosition.LEFT -> 48f
                    LogoPosition.RIGHT -> width - 48f - lw
                    LogoPosition.CENTER_BOTTOM -> (width - lw) / 2f
                }
                val y = when (logoPosition) {
                    LogoPosition.CENTER_BOTTOM -> height * 0.725f - lh / 2f
                    else -> 48f
                }
                canvas.drawBitmap(
                    it,
                    null,
                    android.graphics.RectF(x, y, x + lw, y + lh),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            } catch (_: Throwable) {
                // Keep the thumbnail usable even if the logo bitmap is invalid.
            }
        }

        return output
    }

    private suspend fun saveThumbnail(bitmap: Bitmap, headline: String) {
        val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeTitle = headline.lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "-").trim('-').take(55)
            .ifBlank { "daily-flare-thumbnail" }
        val filename = "DF-$safeTitle-$time.jpg"

        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Daily Flare")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Could not create image file", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

            try {
                val success = contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
                } ?: false

                if (success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Thumbnail saved to Pictures/Daily Flare",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    contentResolver.delete(uri, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Export failed: Could not write image.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Throwable) {
                contentResolver.delete(uri, null, null)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Export failed: " + (e.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                // The preview bitmap can still be referenced by Compose.
                // Do not recycle it here.
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Export failed safely: " + (e.message ?: "unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}