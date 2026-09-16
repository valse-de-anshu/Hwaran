package com.ballade.hwaran.frontend.player.book

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ballade.hwaran.backend.novel.NovelBook
import com.ballade.hwaran.backend.novel.NovelParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBookViewer(
    bookUri: Uri,
    eyeCareMode: EyeCareMode,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var bookContentData by remember { mutableStateOf<Pair<String?, String>?>(null) }

    // Prepare eye care CSS injection string
    val themeCss = remember(eyeCareMode) {
        when (eyeCareMode) {
            EyeCareMode.SEPIA -> """
                javascript:(function() {
                    var style = document.getElementById('hwaran-eyecare-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'hwaran-eyecare-style';
                        document.head.appendChild(style);
                    }
                    style.innerHTML =
                        'html, body { background-color: #F4ECD8 !important; color: #3A2E2B !important; } ' +
                        'div, section, article, main, header, footer, table, tr, td, th, aside, figure, [class*="box"], [class*="panel"], [class*="card"], [class*="frame"] { background-color: transparent !important; color: inherit !important; border-color: rgba(120,84,56,0.2) !important; } ' +
                        'p, span, li, dd, dt { color: inherit !important; } ' +
                        'h1, h2, h3, h4, h5, h6, strong, b, th { color: #2A1F18 !important; } ' +
                        'a { color: #795548 !important; } a:visited { color: #4E342E !important; } ' +
                        'blockquote { background: rgba(120,84,56,0.06) !important; border-left-color: rgba(121,85,72,0.5) !important; color: #5D4037 !important; } ' +
                        'code { background: rgba(120,84,56,0.10) !important; color: #6D4C41 !important; } ' +
                        'pre { background: rgba(90,60,40,0.10) !important; border-color: rgba(120,84,56,0.2) !important; color: #4E342E !important; } ' +
                        'th { background: rgba(120,84,56,0.12) !important; color: #2A1F18 !important; border-color: rgba(120,84,56,0.2) !important; } ' +
                        'td { border-color: rgba(120,84,56,0.15) !important; color: #3A2E2B !important; } ' +
                        'tr:nth-child(even) td { background: rgba(120,84,56,0.04) !important; } ' +
                        '.note, [role="note"], .notice, #pg-header, #pg-footer, #pg-machine-header, .pg-boilerplate, [class*="note"], [class*="notice"] { background: rgba(121,85,72,0.08) !important; border: 1px solid rgba(121,85,72,0.3) !important; color: #5D4037 !important; } ' +
                        '.note *, [role="note"] *, .notice *, #pg-header *, #pg-footer *, #pg-machine-header *, .pg-boilerplate * { color: inherit !important; background: transparent !important; } ' +
                        'img, video { filter: sepia(0.2) contrast(0.95); }';
                })()
            """.trimIndent()

            EyeCareMode.MINT -> """
                javascript:(function() {
                    var style = document.getElementById('hwaran-eyecare-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'hwaran-eyecare-style';
                        document.head.appendChild(style);
                    }
                    style.innerHTML =
                        'html, body { background-color: #E8F5E9 !important; color: #1B382B !important; } ' +
                        'div, section, article, main, header, footer, table, tr, td, th, aside, figure, [class*="box"], [class*="panel"], [class*="card"], [class*="frame"] { background-color: transparent !important; color: inherit !important; border-color: rgba(46,125,50,0.2) !important; } ' +
                        'p, span, li, dd, dt { color: inherit !important; } ' +
                        'h1, h2, h3, h4, h5, h6, strong, b, th { color: #0D2B1E !important; } ' +
                        'a { color: #2E7D32 !important; } a:visited { color: #1B5E20 !important; } ' +
                        'blockquote { background: rgba(46,125,50,0.06) !important; border-left-color: rgba(46,125,50,0.5) !important; color: #2E7D32 !important; } ' +
                        'code { background: rgba(46,125,50,0.10) !important; color: #2E7D32 !important; } ' +
                        'pre { background: rgba(27,94,32,0.08) !important; border-color: rgba(46,125,50,0.2) !important; color: #1B5E20 !important; } ' +
                        'th { background: rgba(46,125,50,0.10) !important; color: #0D2B1E !important; border-color: rgba(46,125,50,0.2) !important; } ' +
                        'td { border-color: rgba(46,125,50,0.12) !important; color: #1B382B !important; } ' +
                        'tr:nth-child(even) td { background: rgba(46,125,50,0.04) !important; } ' +
                        '.note, [role="note"], .notice, #pg-header, #pg-footer, #pg-machine-header, .pg-boilerplate, [class*="note"], [class*="notice"] { background: rgba(46,125,50,0.08) !important; border: 1px solid rgba(46,125,50,0.3) !important; color: #2E7D32 !important; } ' +
                        '.note *, [role="note"] *, .notice *, #pg-header *, #pg-footer *, #pg-machine-header *, .pg-boilerplate * { color: inherit !important; background: transparent !important; } ' +
                        'img, video { filter: hue-rotate(15deg) brightness(0.95); }';
                })()
            """.trimIndent()

            EyeCareMode.NIGHT -> """
                javascript:(function() {
                    var style = document.getElementById('hwaran-eyecare-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'hwaran-eyecare-style';
                        document.head.appendChild(style);
                    }
                    style.innerHTML =
                        'html, body { background-color: #000000 !important; color: #D6D6D6 !important; } ' +
                        'div, section, article, main, header, footer, table, tr, td, th, aside, figure, [class*="box"], [class*="panel"], [class*="card"], [class*="frame"] { background-color: transparent !important; color: inherit !important; border-color: rgba(255,255,255,0.1) !important; } ' +
                        'p, span, li, dd, dt { color: inherit !important; } ' +
                        'h1, h2, h3, h4, h5, h6, strong, b, th { color: #EFEFEF !important; } ' +
                        'a { color: #90CAF9 !important; } a:visited { color: #CE93D8 !important; } ' +
                        'blockquote { background: rgba(255,255,255,0.03) !important; border-left-color: rgba(144,202,249,0.4) !important; color: #B0B0C0 !important; } ' +
                        'code { background: rgba(255,255,255,0.07) !important; color: #F48FB1 !important; } ' +
                        'pre { background: rgba(0,0,0,0.5) !important; border-color: rgba(255,255,255,0.08) !important; color: #C8E6C9 !important; } ' +
                        'th { background: rgba(144,202,249,0.10) !important; color: #EFEFEF !important; border-color: rgba(255,255,255,0.10) !important; } ' +
                        'td { border-color: rgba(255,255,255,0.07) !important; color: #D6D6D6 !important; } ' +
                        'tr:nth-child(even) td { background: rgba(255,255,255,0.02) !important; } ' +
                        '.note, [role="note"], .notice, #pg-header, #pg-footer, #pg-machine-header, .pg-boilerplate, [class*="note"], [class*="notice"] { background: rgba(255,255,255,0.06) !important; border: 1px solid rgba(255,255,255,0.2) !important; color: #90CAF9 !important; } ' +
                        '.note *, [role="note"] *, .notice *, #pg-header *, #pg-footer *, #pg-machine-header *, .pg-boilerplate * { color: inherit !important; background: transparent !important; } ' +
                        'img, video { filter: brightness(0.85) contrast(1.1); }';
                })()
            """.trimIndent()

            EyeCareMode.OFF -> """
                javascript:(function() {
                    var style = document.getElementById('hwaran-eyecare-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'hwaran-eyecare-style';
                        document.head.appendChild(style);
                    }
                    style.innerHTML =
                        'html, body { background-color: #12131A !important; color: #E2E2E6 !important; } ' +
                        'div, section, article, main, header, footer, table, tr, td, th, aside, figure, [class*="box"], [class*="panel"], [class*="card"], [class*="frame"] { background-color: transparent !important; color: inherit !important; border-color: rgba(255,255,255,0.12) !important; } ' +
                        'p, span, li, dd, dt { color: inherit !important; } ' +
                        'h1, h2, h3, h4, h5, h6, strong, b, th { color: #FFFFFF !important; } ' +
                        'a { color: #82B8F5 !important; } a:visited { color: #BB86FC !important; } ' +
                        'blockquote { background: rgba(255,255,255,0.035) !important; border-left-color: rgba(130,184,245,0.5) !important; color: #C2C2D0 !important; } ' +
                        'code { background: rgba(255,255,255,0.09) !important; color: #F48FB1 !important; } ' +
                        'pre { background: rgba(0,0,0,0.4) !important; border-color: rgba(255,255,255,0.09) !important; color: #C8E6C9 !important; } ' +
                        'th { background: rgba(130,184,245,0.10) !important; color: #E8E8F0 !important; border-color: rgba(255,255,255,0.10) !important; } ' +
                        'td { border-color: rgba(255,255,255,0.07) !important; color: #E2E2E6 !important; } ' +
                        'tr:nth-child(even) td { background: rgba(255,255,255,0.025) !important; } ' +
                        '.note, [role="note"], .notice, #pg-header, #pg-footer, #pg-machine-header, .pg-boilerplate, [class*="note"], [class*="notice"] { background: rgba(255,255,255,0.05) !important; border: 1px solid rgba(255,255,255,0.12) !important; color: #E2E2E6 !important; } ' +
                        '.note *, [role="note"] *, .notice *, #pg-header *, #pg-footer *, #pg-machine-header *, .pg-boilerplate * { color: inherit !important; background: transparent !important; } ' +
                        'img, video { filter: none !important; }';
                })()
            """.trimIndent()
        }
    }


    LaunchedEffect(bookUri) {
        isLoadingPage = true
        val result = prepareBookHtml(context, bookUri)
        bookContentData = result
    }

    LaunchedEffect(bookContentData, webViewInstance) {
        val data = bookContentData
        val wv = webViewInstance
        if (data != null && wv != null) {
            wv.loadDataWithBaseURL(data.first, data.second, "text/html", "UTF-8", null)
        }
    }

    LaunchedEffect(webViewInstance, themeCss) {
        webViewInstance?.evaluateJavascript(themeCss, null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                            }
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            isLoadingPage = newProgress < 100
                        }
                    }

                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun onUserTap() {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    onTap()
                                }
                            }
                        },
                        "HwaranBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return false
                            val scheme = url.scheme?.lowercase() ?: ""
                            return if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                true
                            } else {
                                false
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoadingPage = false
                            view?.evaluateJavascript(themeCss, null)
                        }
                    }

                    webViewInstance = this
                }
            },
            update = { wv ->
                webViewInstance = wv
            }
        )

        if (isLoadingPage) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = Color(0xFFE6E8EC)
            )
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    if (uri.scheme == "file") {
        return File(uri.path ?: "").name
    }
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            val name = doc?.name
            if (!name.isNullOrBlank()) return name
        } catch (_: Exception) {}
    }
    return uri.lastPathSegment ?: "book"
}

private fun resolveRealFileFromContentUri(uri: Uri): File? {
    try {
        val uriStr = uri.toString()
        if (uri.authority == "com.android.externalstorage.documents") {
            val docId = try {
                android.provider.DocumentsContract.getDocumentId(uri)
            } catch (_: Exception) {
                uri.path?.substringAfterLast("document/") ?: uri.path?.substringAfterLast("tree/") ?: ""
            }
            val decoded = Uri.decode(docId)
            if (decoded.contains("primary:")) {
                val relPath = decoded.substringAfter("primary:")
                val realFile = File(android.os.Environment.getExternalStorageDirectory(), relPath)
                if (realFile.exists()) return realFile
            }
        }
        if (uriStr.contains("/storage/emulated/0/")) {
            val sub = "/storage/emulated/0/" + uriStr.substringAfter("/storage/emulated/0/")
            val f = File(Uri.decode(sub))
            if (f.exists()) return f
        }
    } catch (_: Exception) {}
    return null
}

private suspend fun prepareBookHtml(context: Context, bookUri: Uri): Pair<String?, String> = withContext(Dispatchers.IO) {
    val uriString = bookUri.toString()
    val resolvedName = getFileNameFromUri(context, bookUri)

    var file: File? = if (uriString.startsWith("file://")) {
        File(Uri.parse(uriString).path ?: "")
    } else if (uriString.startsWith("/")) {
        File(uriString)
    } else null

    // Try resolving directly from SAF external storage provider without copying
    if (file == null && bookUri.scheme == "content") {
        file = resolveRealFileFromContentUri(bookUri)
    }

    // Verify the resolved file actually exists and can be read directly
    if (file != null && (!file.exists() || !file.canRead())) {
        file = null
    }

    // Track whether we created a temp copy so we can delete it after use
    var isTempFile = false

    // If direct File access failed or URI is content://, copy stream to temp cache file
    if (file == null) {
        try {
            val safeName = resolvedName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tempFile = File(context.cacheDir, "temp_book_${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(bookUri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                file = tempFile
                isTempFile = true
            }
        } catch (_: Exception) {}
    }

    try {
        if (file != null && file.exists() && file.canRead()) {
            // A. Directory (Web-Book folder with HTML, images, CSS, or loose book files)
            if (file.isDirectory) {
                val mainHtml = findMainHtml(file)
                if (mainHtml != null && mainHtml.exists()) {
                    val baseUrl = "file://${(mainHtml.parentFile ?: file).absolutePath}/"
                    val rawHtml = try { mainHtml.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                    if (rawHtml.isNotBlank()) {
                        return@withContext baseUrl to injectBaseStyling(rawHtml)
                    }
                }
                try {
                    val novelBook = NovelParser.parseNovelFromFile(file)
                    if (novelBook.chapters.isNotEmpty()) {
                        return@withContext null to buildHtmlFromNovelBook(novelBook)
                    }
                } catch (_: Exception) {}
            }

            val fileNameLower = file.name.lowercase()
            val isZipHeader = try {
                file.inputStream().use { stream ->
                    val b = ByteArray(4)
                    val read = stream.read(b)
                    read == 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && (b[2] == 0x03.toByte() || b[2] == 0x05.toByte() || b[2] == 0x07.toByte())
                }
            } catch (_: Exception) { false }

            // B. Standalone HTML / XHTML
            if (fileNameLower.endsWith(".html") || fileNameLower.endsWith(".htm") || fileNameLower.endsWith(".xhtml")) {
                val baseUrl = "file://${file.parentFile?.absolutePath ?: file.absolutePath}/"
                val rawHtml = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                if (rawHtml.isNotBlank()) {
                    return@withContext baseUrl to injectBaseStyling(rawHtml)
                }
            }

            // C. EPUB / ZIP Archive
            if (fileNameLower.endsWith(".epub") || fileNameLower.endsWith(".epub3") || isZipHeader) {
                val cacheKey = "${file.name.hashCode().coerceAtLeast(0)}_${file.length()}"
                val epubCacheDir = File(context.cacheDir, "epub_$cacheKey")
                if (!epubCacheDir.exists() || epubCacheDir.list()?.isEmpty() == true) {
                    epubCacheDir.mkdirs()
                    try { unzip(file, epubCacheDir) } catch (e: Exception) { e.printStackTrace() }
                }
                val assembled = assembleEpubHtml(epubCacheDir)
                if (assembled != null) {
                    val baseUrl = "file://${assembled.first.absolutePath}/"
                    return@withContext baseUrl to injectBaseStyling(assembled.second)
                }
                // Fallback to NovelParser EPUB extractor
                try {
                    val novelBook = NovelParser.parseNovelFromFile(file)
                    if (novelBook.chapters.isNotEmpty()) {
                        return@withContext null to buildHtmlFromNovelBook(novelBook)
                    }
                } catch (_: Exception) {}
            }

            // D. Kindle / MOBI / KF8 / AZW / AZW3 / PRC
            if (NovelParser.isKindleOrMobi(fileNameLower)) {
                try {
                    val novelBook = NovelParser.parseNovelFromFile(file)
                    if (novelBook.chapters.isNotEmpty()) {
                        return@withContext null to buildHtmlFromNovelBook(novelBook)
                    }
                } catch (_: Exception) {}
            }

            // E. Markdown
            if (fileNameLower.endsWith(".md") || fileNameLower.endsWith(".markdown")) {
                val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                if (raw.isNotBlank()) {
                    return@withContext null to injectBaseStyling(markdownToHtml(raw, file.nameWithoutExtension))
                }
            }

            // F. FB2 (FictionBook)
            if (fileNameLower.endsWith(".fb2")) {
                val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                if (raw.isNotBlank()) {
                    return@withContext null to injectBaseStyling(fb2ToHtml(raw))
                }
            }

            // G. Plain text / logs / docs
            if (fileNameLower.endsWith(".txt") || fileNameLower.endsWith(".text") || fileNameLower.endsWith(".log") || fileNameLower.endsWith(".csv") || fileNameLower.endsWith(".json") || fileNameLower.endsWith(".xml")) {
                val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                if (raw.isNotBlank()) {
                    return@withContext null to injectBaseStyling(plainTextToHtml(raw, file.nameWithoutExtension))
                }
            }

            // H. Universal NovelParser fallback on file
            try {
                val novelBook = NovelParser.parseNovelFromFile(file)
                if (novelBook.chapters.isNotEmpty()) {
                    return@withContext null to buildHtmlFromNovelBook(novelBook)
                }
            } catch (_: Exception) {}
        }

        // Direct stream reading fallback for text / HTML / FB2 / Markdown
        try {
            val content = context.contentResolver.openInputStream(bookUri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (content.isNotBlank()) {
                val uriName = resolvedName.lowercase()
                val result: Pair<String?, String> = when {
                    uriName.endsWith(".html") || uriName.endsWith(".htm") || uriName.endsWith(".xhtml") || content.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) || content.trimStart().startsWith("<html", ignoreCase = true) ->
                        null to injectBaseStyling(content)
                    uriName.endsWith(".md") || uriName.endsWith(".markdown") ->
                        null to injectBaseStyling(markdownToHtml(content, resolvedName.substringBeforeLast(".")))
                    uriName.endsWith(".fb2") || content.contains("<FictionBook", ignoreCase = true) ->
                        null to injectBaseStyling(fb2ToHtml(content))
                    else ->
                        null to injectBaseStyling(plainTextToHtml(content, resolvedName.substringBeforeLast(".")))
                }
                return@withContext result
            }
        } catch (_: Exception) {}

        // Universal stream fallback via NovelParser (handles MOBI, EPUB, TXT, etc. from URI)
        try {
            val streamedNovel = NovelParser.parseNovel(context, bookUri, resolvedName)
            if (streamedNovel.chapters.isNotEmpty()) {
                return@withContext null to buildHtmlFromNovelBook(streamedNovel)
            }
        } catch (_: Exception) {}

        // Ultimate safe fallback: Extract readable text bytes
        try {
            val rawBytes = context.contentResolver.openInputStream(bookUri)?.use { it.readBytes() }
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val rawText = try { String(rawBytes, Charsets.UTF_8) } catch (_: Exception) { String(rawBytes, Charsets.ISO_8859_1) }
                val clean = rawText.filter { it.isLetterOrDigit() || it.isWhitespace() || "\",.:;!?'\"-–—()[]{}/*&@#%<>+=_".contains(it) }
                if (clean.length > 50) {
                    return@withContext null to injectBaseStyling(plainTextToHtml(clean, resolvedName.substringBeforeLast(".")))
                }
            }
        } catch (_: Exception) {}

        return@withContext null to "<!DOCTYPE html><html><body><div style='display:flex;flex-direction:column;align-items:center;justify-content:center;height:80vh;color:#E2E2E6;font-family:sans-serif;text-align:center;padding:24px;'><p style='font-size:18px;font-weight:bold;margin-bottom:8px;'>Unable to load book content</p><p style='font-size:14px;color:#9E9E9E;'>The file format could not be decoded.</p></div></body></html>"
    } finally {
        // Always delete temp copy regardless of which path succeeded or failed
        if (isTempFile) file?.delete()
    }
}


private fun findMainHtml(dir: File): File? {
    val candidates = listOf("index.html", "index.htm", "index.xhtml", "titlepage.html", "main.html", "book.html")
    val direct = dir.listFiles()
    direct?.firstOrNull { f -> f.isFile && candidates.any { c -> f.name.equals(c, ignoreCase = true) } }?.let { return it }
    direct?.firstOrNull { f -> f.isFile && f.name.contains("images", ignoreCase = true) && (f.name.endsWith(".html", true) || f.name.endsWith(".htm", true)) }?.let { return it }
    direct?.firstOrNull { f -> f.isFile && (f.name.endsWith(".html", true) || f.name.endsWith(".htm", true) || f.name.endsWith(".xhtml", true)) }?.let { return it }
    // Search subdirectories up to depth 3
    dir.walkTopDown().maxDepth(3).firstOrNull { f ->
        f.isFile && (f.name.endsWith(".html", true) || f.name.endsWith(".htm", true) || f.name.endsWith(".xhtml", true))
    }?.let { return it }
    return null
}

private fun unzip(zipFile: File, targetDir: File) {
    if (!targetDir.exists()) targetDir.mkdirs()
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            // Reject any zip-slip entries containing .. or starting with /
            if (entry.name.contains("../") || entry.name.contains("..\\") || entry.name.startsWith("/")) continue
            val destFile = File(targetDir, entry.name)
            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                destFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

private fun assembleEpubHtml(epubDir: File): Pair<File, String>? {
    val container = File(epubDir, "META-INF/container.xml").takeIf { it.exists() }
        ?: File(epubDir, "meta-inf/container.xml").takeIf { it.exists() }
    var opfPath = "OEBPS/content.opf"
    if (container != null) {
        val text = container.readText()
        Regex("""full-path\s*=\s*["']([^"']+)["']""").find(text)?.let {
            opfPath = it.groupValues[1]
        }
    }

    var opfFile = File(epubDir, opfPath)
    if (!opfFile.exists()) {
        opfFile = epubDir.walkTopDown().firstOrNull { it.extension.equals("opf", ignoreCase = true) } ?: return null
    }

    val opfDir = opfFile.parentFile ?: epubDir
    val opfContent = try { opfFile.readText() } catch (_: Exception) { return null }

    val manifest = mutableMapOf<String, String>()
    Regex("""<item\s+[^>]*>""", RegexOption.IGNORE_CASE).findAll(opfContent).forEach { match ->
        val tag = match.value
        val id = Regex("""id=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
        val href = Regex("""href=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
        if (id != null && href != null) {
            manifest[id] = href
        }
    }

    val spine = mutableListOf<String>()
    Regex("""<itemref\s+[^>]*idref=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).findAll(opfContent).forEach { match ->
        spine.add(match.groupValues[1])
    }

    val bodyBuilder = StringBuilder()
    // Collect linked CSS hrefs (relative to opfDir) and inline <style> blocks from all chapters
    val collectedCssLinks = linkedSetOf<String>()
    val collectedInlineStyles = StringBuilder()

    var title = "EPUB Book"
    Regex("""<dc:title[^>]*>([^<]+)</dc:title>""", RegexOption.IGNORE_CASE).find(opfContent)?.let {
        title = it.groupValues[1].trim()
    }

    for (idref in spine) {
        val href = manifest[idref] ?: continue
        var chapterFile = File(opfDir, href)
        if (!chapterFile.exists()) {
            val decoded = try { Uri.decode(href) } catch (_: Exception) { href }
            chapterFile = File(opfDir, decoded)
        }
        if (!chapterFile.exists()) continue
        val html = try { chapterFile.readText(Charsets.UTF_8) } catch (_: Exception) { "" }

        // Collect <link rel="stylesheet" href="..."> from each chapter's <head>
        Regex("""<link\s+[^>]*rel=["']stylesheet["'][^>]*>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val cssHref = Regex("""href=["']([^"']+)["']""").find(m.value)?.groupValues?.get(1)
            if (!cssHref.isNullOrBlank() && !cssHref.startsWith("http")) {
                val chapterDir = chapterFile.parentFile ?: opfDir
                try {
                    val resolved = File(chapterDir, cssHref)
                    val relToOpf = resolved.relativeToOrNull(opfDir)?.path ?: resolved.name
                    collectedCssLinks.add(relToOpf)
                } catch (_: Exception) {}
            }
        }

        // Collect inline <style> blocks from chapter heads (deduplicate identical blocks)
        Regex("""<style[^>]*>(.*?)</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(html).forEach { m ->
            val block = m.groupValues[1].trim()
            if (block.isNotBlank() && !collectedInlineStyles.contains(block)) {
                collectedInlineStyles.append(block).append("\n")
            }
        }

        val bodyMatch = Regex("""<body[^>]*>(.*?)</body>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(html)
        if (bodyMatch != null) {
            bodyBuilder.append("<div class='hwaran-chapter'>").append(bodyMatch.groupValues[1]).append("</div><hr class='hwaran-chapter-divider'/>")
        } else if (html.isNotBlank()) {
            bodyBuilder.append("<div class='hwaran-chapter'>").append(html).append("</div><hr class='hwaran-chapter-divider'/>")
        }
    }

    // Build link tags for all collected CSS files (baseUrl is opfDir so hrefs resolve correctly)
    val cssLinkTags = collectedCssLinks.joinToString("\n") { "    <link rel='stylesheet' href='$it'>" }
    val inlineStyleBlock = if (collectedInlineStyles.isNotBlank()) "<style>\n$collectedInlineStyles</style>" else ""

    val fullHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <title>$title</title>
$cssLinkTags
$inlineStyleBlock
        </head>
        <body>
            $bodyBuilder
        </body>
        </html>
    """.trimIndent()

    return opfDir to fullHtml
}

private fun buildHtmlFromNovelBook(book: NovelBook): String {
    val sb = StringBuilder()
    sb.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
            <title>${book.title.escapeHtml()}</title>
        </head>
        <body>
            <h1 class='hwaran-book-title'>${book.title.escapeHtml()}</h1>
            ${if (!book.author.isNullOrBlank()) "<p class='hwaran-book-author'>By ${book.author.escapeHtml()}</p>" else ""}
            ${if (!book.description.isNullOrBlank()) "<p class='hwaran-book-desc'>${book.description.escapeHtml()}</p>" else ""}
    """.trimIndent())

    book.chapters.forEach { ch ->
        sb.append("<div class='hwaran-chapter'>")
        if (ch.title.isNotBlank()) {
            sb.append("<h2 class='hwaran-chapter-title'>${ch.title.escapeHtml()}</h2>")
        }

        // Split on blank lines — each block is either a prose paragraph or a verse stanza
        val blocks = ch.content.split(Regex("""(?:\r?\n){2,}"""))
        blocks.forEach { block ->
            val trimmed = block.trim()
            if (trimmed.isBlank()) return@forEach

            val lines = trimmed.lines()

            // Detect verse/poetry block:
            // — multiple lines, majority are short (< 72 chars), consistent length variance
            val isVerse = lines.size >= 2 && run {
                val shortLines = lines.count { it.length < 72 }
                val avgLen = lines.sumOf { it.length } / lines.size.coerceAtLeast(1)
                shortLines.toFloat() / lines.size > 0.65f && avgLen < 65
            }

            if (isVerse) {
                // Render as a verse/poem stanza
                sb.append("<div class='stanza'>")
                lines.forEach { line ->
                    val l = line.escapeHtml()
                    // Leading whitespace → indentation
                    val indent = line.length - line.trimStart().length
                    val cls = when {
                        indent >= 6 -> " class='line i3'"
                        indent >= 4 -> " class='line i2'"
                        indent >= 2 -> " class='line i1'"
                        else -> " class='line'"
                    }
                    sb.append("<span$cls>$l</span>")
                }
                sb.append("</div>")
            } else {
                // Prose paragraph — join lines with a space (soft wraps), escape HTML
                val prose = lines.joinToString(" ") { it.trim() }.trim().escapeHtml()
                if (prose.isNotBlank()) {
                    sb.append("<p>$prose</p>")
                }
            }
        }

        sb.append("</div><hr class='hwaran-chapter-divider'/>")
    }

    sb.append("</body></html>")
    return injectBaseStyling(sb.toString())
}

/** Escape HTML special characters so raw text never breaks layout. */
private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun injectBaseStyling(rawHtml: String): String {
    // language=HTML
    val injection = """
<style id="hwaran-base-style">
/* ═══════════════════════════════════════════════
   HWARAN READER — UNIVERSAL BOOK STYLESHEET
   Handles: EPUB, MOBI/Kindle, HTML, Web-books,
   Plain text, Gutenberg, FB2, poetry, drama,
   letters, RTL, footnotes, tables, figures.
   ═══════════════════════════════════════════════ */

/* ── 0. Box model reset & container background override ── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

div, section, article, main, header, footer, table, tr, td, th, aside, figure, figcaption,
[class*="box"], [class*="panel"], [class*="card"], [class*="frame"], [class*="container"], [class*="wrapper"] {
    background-color: transparent !important;
    color: inherit;
    border-color: rgba(255,255,255,0.12) !important;
}

/* ── 1. Root ── */
html {
    font-size: 17px;
    -webkit-text-size-adjust: 100%;
    text-size-adjust: 100%;
    scroll-behavior: smooth;
    background: transparent;
}
body {
    margin: 0 auto !important;
    max-width: 740px !important;
    width: auto !important;
    padding: 24px 20px 120px 20px !important;
    font-family: "Georgia", "Palatino Linotype", "Book Antiqua", "Times New Roman", serif;
    font-size: 1.05rem;
    line-height: 1.8;
    word-wrap: break-word;
    overflow-wrap: break-word;
    color: #E2E2E6 !important;
    background-color: #12131A !important;
    -webkit-font-smoothing: antialiased;
    text-rendering: optimizeLegibility;
}

/* ── 2. Paragraphs ── */
p {
    margin: 0 0 1.2em 0 !important;
    text-align: left !important;
    line-height: 1.8 !important;
    text-indent: 0 !important;
    word-spacing: normal !important;
    letter-spacing: normal !important;
}
p:last-child { margin-bottom: 0 !important; }

/* ── 3. Headings ── */
h1, h2, h3, h4, h5, h6 {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: #FFFFFF;
    line-height: 1.25;
    font-weight: 700;
    margin: 2em 0 0.7em 0;
    letter-spacing: -0.01em;
    text-align: left;
    text-indent: 0 !important;
}
h1 { font-size: 1.75rem; margin-top: 1.2em; }
h2 { font-size: 1.4rem;  border-bottom: 1px solid rgba(255,255,255,0.12); padding-bottom: 6px; }
h3 { font-size: 1.18rem; }
h4 { font-size: 1.05rem; }
h5 { font-size: 0.95rem; color: #B8B8C8; font-weight: 600; }
h6 { font-size: 0.88rem; color: #9898B0; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; }

/* ── 4. Inline text ── */
a { color: #82B8F5; text-decoration: underline; text-underline-offset: 2px; }
a:visited { color: #BB86FC; }
a:hover, a:active { color: #90CAF9; }
strong, b { color: #FFFFFF; font-weight: 700; }
em, i { font-style: italic; color: inherit; }
u { text-decoration: underline; text-underline-offset: 2px; }
s, del { text-decoration: line-through; color: #888899; }
ins { text-decoration: underline; color: #A5D6A7; background: rgba(165,214,167,0.08); }
mark { background: rgba(255,235,59,0.22); color: #FFFDE7; padding: 1px 3px; border-radius: 2px; }
abbr[title] { text-decoration: underline dotted; cursor: help; }
cite { font-style: italic; color: #B0B0C0; }
q::before { content: "\201C"; }
q::after  { content: "\201D"; }
small { font-size: 0.82em; color: #9E9E9E; }
/* Small-caps used extensively in Gutenberg / classic books */
.smcap, .sc, span[style*="small-caps"] {
    font-variant: small-caps;
    font-size: 0.95em;
    letter-spacing: 0.04em;
}
/* Translation / foreign text */
.trans, .transnote, [lang]:not([lang=""]):not([lang="en"]) {
    font-style: italic;
    color: #BCBCD0;
}

/* ── 5. Superscript / Subscript / Footnote markers ── */
sup, sub { font-size: 0.72em; line-height: 0; position: relative; vertical-align: baseline; }
sup { top: -0.45em; }
sub { bottom: -0.25em; }
/* Footnote anchors — make them obvious but not disruptive */
a.footnote, a.endnote, a[epub|type~="noteref"],
sup > a, a[href^="#fn"], a[href^="#note"] {
    color: #82B8F5;
    font-size: 0.75em;
    text-decoration: none;
    background: rgba(130,184,245,0.12);
    border-radius: 3px;
    padding: 0 3px;
    vertical-align: super;
}

/* ── 6. Superscript / Subscript ── */
sup, sub { font-size: 0.72em; line-height: 0; position: relative; vertical-align: baseline; }

/* ── 7. Lists ── */
ul, ol {
    margin: 0.6em 0 1em 0;
    padding-left: 1.8em;
}
li { margin-bottom: 0.3em; line-height: 1.65; }
ul ul, ol ol, ul ol, ol ul { margin: 0.2em 0; }
/* Description lists */
dl { margin: 0.6em 0 1em 0; }
dt { font-weight: 700; color: #FFFFFF; margin-top: 0.8em; }
dd { margin-left: 1.6em; color: #C4C4CC; }

/* ── 8. Blockquote ── */
blockquote {
    margin: 1.4em 0;
    padding: 12px 18px 12px 22px;
    border-left: 4px solid rgba(130,184,245,0.5);
    background: rgba(255,255,255,0.035);
    border-radius: 0 8px 8px 0;
    color: #C2C2D0;
    font-style: italic;
}
blockquote p { margin: 0; text-indent: 0; }
blockquote p + p { margin-top: 0.6em; text-indent: 0; }
blockquote cite,
blockquote footer {
    display: block;
    margin-top: 8px;
    font-size: 0.85em;
    color: #8888A0;
    font-style: normal;
}
blockquote cite::before { content: "— "; }

/* ── 9. Code & Pre ── */
code {
    font-family: "Fira Code", "JetBrains Mono", "Courier New", monospace;
    font-size: 0.84em;
    background: rgba(255,255,255,0.09);
    padding: 1px 6px;
    border-radius: 4px;
    color: #F48FB1;
    word-break: break-all;
}
pre {
    font-family: "Fira Code", "JetBrains Mono", "Courier New", monospace;
    font-size: 0.81em;
    background: rgba(0,0,0,0.4);
    border: 1px solid rgba(255,255,255,0.09);
    border-radius: 10px;
    padding: 16px 18px;
    margin: 1.4em 0;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    white-space: pre;
    color: #C8E6C9;
    line-height: 1.5;
    tab-size: 4;
    -moz-tab-size: 4;
}
pre code { background: none; padding: 0; color: inherit; font-size: inherit; border-radius: 0; word-break: normal; }
kbd {
    font-family: "Fira Code", "JetBrains Mono", monospace;
    font-size: 0.82em;
    background: rgba(255,255,255,0.12);
    border: 1px solid rgba(255,255,255,0.25);
    border-bottom-width: 2px;
    border-radius: 4px;
    padding: 1px 6px;
    color: #E2E2E6;
}
samp, var { font-family: inherit; font-style: italic; color: #FFD54F; }

/* ── 10. Tables ── */
.hwaran-table-wrap {
    width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    margin: 1.6em 0;
    border-radius: 10px;
    border: 1px solid rgba(255,255,255,0.10);
}
table {
    border-collapse: collapse;
    width: 100%;
    min-width: 260px;
    font-size: 0.87rem;
    line-height: 1.5;
    color: #D8D8E4;
}
caption {
    caption-side: top;
    font-size: 0.85em;
    color: #9898B0;
    padding: 8px 14px;
    text-align: left;
    font-style: italic;
}
colgroup, col { /* no style, just allow it */ }
thead tr { border-bottom: 2px solid rgba(255,255,255,0.18); }
th {
    background: rgba(130,184,245,0.10);
    color: #E8E8F0;
    font-weight: 700;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    text-align: left;
    padding: 10px 14px;
    border: 1px solid rgba(255,255,255,0.10);
}
td {
    padding: 8px 14px;
    border: 1px solid rgba(255,255,255,0.07);
    vertical-align: top;
}
tr:nth-child(even) > td { background: rgba(255,255,255,0.025); }
tfoot td, tfoot th { font-style: italic; color: #9898B0; border-top: 1px solid rgba(255,255,255,0.15); }

/* ── 11. Horizontal rules ── */
hr {
    border: 0;
    height: 1px;
    background: rgba(255,255,255,0.14);
    margin: 2.4em auto;
    max-width: 60%;
}
hr.full, hr.section { max-width: 100%; }
hr.hwaran-chapter-divider {
    height: 1px;
    max-width: 100%;
    background: linear-gradient(to right, transparent, rgba(255,255,255,0.22), transparent);
    margin: 3.2em 0;
}
/* Ornamental breaks common in literary EPUBs */
hr.ornamental::before,
hr.fancy::before,
p.tb::before,
p.center.star::before {
    content: "⁂";
    display: block;
    text-align: center;
    border: 0;
    color: rgba(255,255,255,0.35);
    font-size: 1.2em;
    background: transparent;
}

/* ── 12. Figures & Captions ── */
figure {
    margin: 1.8em 0;
    text-align: center;
    clear: both;
}
figcaption {
    font-size: 0.82em;
    color: #8888A0;
    margin-top: 8px;
    font-style: italic;
    line-height: 1.45;
}

/* ── 13. Images / Video / SVG ── */
img {
    max-width: 100% !important;
    height: auto !important;
    display: block;
    margin: 1.2em auto;
    border-radius: 6px;
}
/* Inline images (decorative, small) */
img[width], img[style*="width"] {
    display: inline;
    margin: 0 4px;
    vertical-align: middle;
    border-radius: 3px;
}
/* Cover images should be large */
img.cover, .cover img, img[id*="cover"], img[class*="cover"] {
    max-width: 80% !important;
    border-radius: 10px;
    box-shadow: 0 8px 32px rgba(0,0,0,0.5);
    margin: 2em auto;
}
video, audio {
    max-width: 100%;
    display: block;
    margin: 1.2em auto;
    border-radius: 8px;
}
svg { max-width: 100%; height: auto; }

/* ── 14. Poetry & Verse ── */
/*
  Common class names: .poem, .poetry, .verse, .stanza,
  .line, .poetry-block, .lg, .l (TEI-style in Gutenberg),
  [epub:type~="z3998:poem"], [epub:type~="z3998:verse"]
*/
.poem, .poetry, .verse, .poetry-block,
[epub\:type~="z3998:poem"], [epub\:type~="z3998:verse"],
.lg {
    margin: 1.8em auto;
    padding-left: 2em;
    font-style: italic;
    color: #D0D0DE;
    white-space: normal;
    max-width: 90%;
}
.stanza, .lg + .lg { margin-top: 1.4em; }
.line, .l {
    display: block;
    text-indent: 0 !important;
    margin-bottom: 0 !important;
    line-height: 1.65;
}
/* Indented continuation lines */
.line.indent, .l.indent,
.line.i1, .l.i1 { padding-left: 2em; }
.line.i2, .l.i2 { padding-left: 4em; }
.line.i3, .l.i3 { padding-left: 6em; }
.stanza + .stanza, .lg + .lg { margin-top: 1em; }
/* Speaker / stage directions in drama */
.speaker { font-weight: 700; font-style: normal; color: #FFFFFF; display: block; margin-top: 0.9em; }
.speech, .stagedir, .direction {
    display: block;
    margin: 0.3em 0 0.3em 2em;
    font-style: italic;
    color: #A0A0B8;
}
.drama, .play { margin: 1.6em 0; }

/* ── 15. Letters, Documents, Centered text ── */
.letter, .document, .manuscript {
    border-left: 3px solid rgba(255,255,255,0.15);
    padding-left: 1.4em;
    margin: 1.6em 0;
    font-style: italic;
    color: #C8C8D8;
}
.letter .salutation,
.letter .valediction { font-style: normal; font-weight: 600; color: #E0E0EC; display: block; }
.letter .salutation { margin-bottom: 0.5em; }
.letter .valediction { margin-top: 0.8em; }
/* Center & Right align classes used extensively in Gutenberg */
.center, .centered, .align-center,
p.c1, p.c2, p.c3,
[align="center"] {
    text-align: center !important;
    text-indent: 0 !important;
}
.right, .align-right, [align="right"] {
    text-align: right !important;
    text-indent: 0 !important;
}
.left, .align-left, [align="left"] {
    text-align: left !important;
}

/* ── 16. Footnotes / Endnotes ── */
.footnote, .endnote, .note-anchor,
[epub\:type~="footnote"], [epub\:type~="endnote"],
[epub\:type~="footnotes"] section,
[epub\:type~="rearnotes"] section {
    font-size: 0.84em;
    color: #A0A0B8;
    border-top: 1px solid rgba(255,255,255,0.10);
    margin-top: 2em;
    padding-top: 0.8em;
    line-height: 1.55;
}
.footnote p, .endnote p { margin-bottom: 0.4em; text-indent: 0; }
/* Highlighted footnote on target (when user taps a footnote link) */
.footnote:target, .endnote:target,
[id^="fn"]:target, [id^="note"]:target {
    background: rgba(130,184,245,0.10);
    border-radius: 6px;
    padding: 4px 8px;
    outline: 1px solid rgba(130,184,245,0.3);
    scroll-margin-top: 80px;
}

/* ── 17. Aside / Sidebar ── */
aside {
    border-left: 3px solid rgba(255,255,255,0.20);
    padding: 10px 16px;
    margin: 1.4em 0;
    background: rgba(255,255,255,0.03);
    border-radius: 0 8px 8px 0;
    font-size: 0.9em;
    color: #B8B8CC;
}
aside p { text-indent: 0; }

/* ── 18. Details / Summary (interactive disclosure) ── */
details {
    border: 1px solid rgba(255,255,255,0.12);
    border-radius: 8px;
    padding: 12px 16px;
    margin: 1.2em 0;
    background: rgba(255,255,255,0.03);
}
summary {
    cursor: pointer;
    font-weight: 600;
    color: #FFFFFF;
    list-style: none;
    padding: 2px 0;
}
summary::before { content: "▶ "; font-size: 0.75em; color: #82B8F5; }
details[open] > summary::before { content: "▼ "; }
details[open] { padding-bottom: 14px; }
details > *:not(summary) { margin-top: 10px; }

/* ── 19. Gutenberg / Classic book specific classes & layout reset ── */
#pg-header, #pg-footer, #pg-machine-header, .pg-boilerplate, .header, [id*="pg-header"] {
    text-indent: 0 !important;
    margin: 1.5em 0 !important;
    padding: 16px 20px !important;
    background: rgba(255, 255, 255, 0.05) !important;
    border: 1px solid rgba(255, 255, 255, 0.12) !important;
    border-radius: 12px !important;
    font-size: 0.9rem !important;
    line-height: 1.6 !important;
    color: #D0D0DC !important;
}
#pg-header *, #pg-footer *, #pg-machine-header *, .pg-boilerplate * {
    text-indent: 0 !important;
    margin-left: 0 !important;
    text-align: left !important;
    color: inherit;
}
#pg-header p, #pg-machine-header p, .pg-boilerplate p {
    margin: 6px 0 !important;
    text-indent: 0 !important;
    text-align: left !important;
}
#pg-header strong, #pg-machine-header strong, .pg-boilerplate strong {
    color: #FFFFFF !important;
}
#pg-start-separator, #pg-header-heading {
    text-align: center !important;
    margin: 1.4em 0 !important;
    font-weight: 700 !important;
    color: #FFFFFF !important;
}
#pg-start-separator span {
    color: #A0A0B5 !important;
    font-size: 0.8em !important;
    letter-spacing: 0.08em !important;
}

/* Page number markers */
.pagenum, .page-num, .pgnumber,
[epub\:type~="pagebreak"] {
    display: block;
    color: #606072;
    font-size: 0.72em;
    text-align: right;
    margin: -0.5em 0 0.5em 0;
    font-style: normal;
    text-indent: 0;
    user-select: none;
}
/* Section / chapter header area */
.chapter, .chapter-head {
    text-align: center;
    margin-bottom: 2em;
}
/* Notices (Gutenberg transcription notices, translators' notes, commentary boxes) */
.note, .notice, .transnote,
[role="note"], [epub\:type~="note"], [class*="note"], [class*="notice"] {
    background: rgba(255, 255, 255, 0.05) !important;
    border: 1px solid rgba(255, 255, 255, 0.14) !important;
    border-radius: 10px !important;
    padding: 14px 18px !important;
    margin: 1.4em 0 !important;
    font-size: 0.9em !important;
    color: #E2E2E6 !important;
    line-height: 1.65 !important;
}
.note *, .notice *, .transnote *, [role="note"] *, [epub\:type~="note"] *, [class*="note"] *, [class*="notice"] * {
    color: inherit !important;
    background: transparent !important;
}
.note p, .notice p, .transnote p { text-indent: 0 !important; margin-bottom: 0.4em !important; }
/* Warning / caution boxes */
.warning, .caution {
    background: rgba(255,167,38,0.07);
    border: 1px solid rgba(255,167,38,0.25);
    border-radius: 8px;
    padding: 12px 16px;
    margin: 1.4em 0;
    color: #FFCC80;
    font-size: 0.88em;
}
/* Tip / important boxes */
.tip, .important {
    background: rgba(129,199,132,0.07);
    border: 1px solid rgba(129,199,132,0.25);
    border-radius: 8px;
    padding: 12px 16px;
    margin: 1.4em 0;
    color: #A5D6A7;
    font-size: 0.88em;
}
/* Gutenberg printer / typographer marks */
.gesture, .sign, .flourish {
    text-align: center;
    display: block;
    color: rgba(255,255,255,0.3);
    margin: 1em 0;
}

/* ── 20. Drop Cap (first letter of chapters) ── */
.dropcap::first-letter,
p.dropcap::first-letter {
    float: left;
    font-size: 3.6em;
    line-height: 0.72;
    padding-right: 8px;
    padding-top: 4px;
    font-weight: 700;
    color: #FFFFFF;
}

/* ── 21. Float clearing ── */
.clearfix::after,
.clear::after { content: ""; display: table; clear: both; }
.clear-left { clear: left; }
.clear-right { clear: right; }
.clear-both, .cb { clear: both; }
img.floatleft, img.float-left { float: left; margin: 0 16px 8px 0; max-width: 45%; border-radius: 6px; }
img.floatright, img.float-right { float: right; margin: 0 0 8px 16px; max-width: 45%; border-radius: 6px; }

/* ── 22. RTL / bidirectional text ── */
[dir="rtl"], .rtl {
    direction: rtl;
    text-align: right;
    unicode-bidi: embed;
}
[dir="rtl"] blockquote { border-left: 0; border-right: 4px solid rgba(130,184,245,0.5); padding-left: 18px; padding-right: 22px; }

/* ── 23. Hwaran chapter wrappers ── */
.hwaran-chapter { margin-bottom: 2.4em; }
.hwaran-book-title {
    font-size: 1.8rem;
    font-weight: 700;
    text-align: center;
    margin: 1.4em auto 0.3em auto;
    color: #FFFFFF;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
    letter-spacing: -0.02em;
    line-height: 1.2;
    max-width: 90%;
}
.hwaran-book-author {
    text-align: center;
    color: #9898B0;
    font-size: 0.92rem;
    margin-bottom: 2.4em;
    font-style: italic;
}
.hwaran-chapter-title {
    font-size: 1.22rem;
    border-bottom: 1px solid rgba(255,255,255,0.10);
    padding-bottom: 8px;
    margin-top: 2.2em;
    margin-bottom: 1em;
    text-indent: 0 !important;
}

/* ── 24. Generic overflow safety ── */
* { max-width: 100%; }
img, video, iframe, object, embed, canvas { max-width: 100% !important; height: auto; }
table { max-width: none; } /* tables get scroll wrapper, so release this */
pre, code { max-width: none; word-wrap: normal; }
</style>
<script>
(function() {
  function enhance() {
    /* 1. Wrap tables in a horizontally-scrollable container */
    document.querySelectorAll('table').forEach(function(t) {
      if (t.parentElement && !t.parentElement.classList.contains('hwaran-table-wrap')) {
        var w = document.createElement('div');
        w.className = 'hwaran-table-wrap';
        t.parentNode.insertBefore(w, t);
        w.appendChild(t);
      }
    });

    /* 2. Make all images lazy-load and hide gracefully if missing */
    document.querySelectorAll('img').forEach(function(img) {
      if (!img.getAttribute('loading')) img.setAttribute('loading', 'lazy');
      img.addEventListener('error', function() {
        img.style.display = 'none';
      });
      // Inline images (tiny width) stay inline
      var w = parseInt(img.getAttribute('width') || '0', 10);
      if (w > 0 && w < 60) {
        img.style.display = 'inline';
        img.style.verticalAlign = 'middle';
        img.style.margin = '0 3px';
        img.style.borderRadius = '3px';
      }
    });

    /* 3. Wrap bare pre/code blocks that overflow */
    document.querySelectorAll('pre').forEach(function(pre) {
      if (!pre.parentElement.classList.contains('hwaran-pre-wrap')) {
        /* already scrollable via CSS overflow-x; just ensure no forced width */
        pre.style.maxWidth = 'calc(100vw - 40px)';
      }
    });

    /* 4. Footnote highlight: flash target footnote when clicked */
    document.querySelectorAll('a[href^="#"]').forEach(function(a) {
      a.addEventListener('click', function() {
        var id = a.getAttribute('href').substring(1);
        var el = document.getElementById(id);
        if (!el) return;
        el.classList.add('hwaran-fn-flash');
        setTimeout(function() { el.classList.remove('hwaran-fn-flash'); }, 1800);
      });
    });

    /* 6. Tables without thead — promote first row to thead */
    document.querySelectorAll('table').forEach(function(t) {
      if (!t.querySelector('thead') && !t.querySelector('th')) {
        var firstRow = t.querySelector('tr');
        if (firstRow) {
          var thead = document.createElement('thead');
          t.insertBefore(thead, firstRow);
          thead.appendChild(firstRow);
          firstRow.querySelectorAll('td').forEach(function(td) {
            var th = document.createElement('th');
            th.innerHTML = td.innerHTML;
            Array.from(td.attributes).forEach(function(a) { th.setAttribute(a.name, a.value); });
            firstRow.replaceChild(th, td);
          });
        }
      }
    });

    /* 7. Center-align paragraphs that contain only an image */
    document.querySelectorAll('p').forEach(function(p) {
      var kids = Array.from(p.childNodes).filter(function(n) {
        return !(n.nodeType === 3 && n.textContent.trim() === '');
      });
      if (kids.length === 1 && kids[0].nodeName === 'IMG') {
        p.style.textAlign = 'center';
        p.style.textIndent = '0';
      }
    });

    /* 8. Ensure <details> elements have a visible open animation */
    document.querySelectorAll('details').forEach(function(d) {
      d.addEventListener('toggle', function() {
        if (d.open) d.style.transition = 'all 0.2s ease';
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', enhance);
  } else {
    enhance();
  }

  /* Fallback: also run after full load in case content renders late */
  window.addEventListener('load', enhance);
})();
</script>
<style id="hwaran-fn-flash-style">
@keyframes hwaran-fn-flash {
  0%   { background: rgba(130,184,245,0.30); }
  60%  { background: rgba(130,184,245,0.15); }
  100% { background: transparent; }
}
.hwaran-fn-flash {
  animation: hwaran-fn-flash 1.8s ease-out forwards;
  border-radius: 4px;
  scroll-margin-top: 80px;
}
</style>
    """.trimIndent()

    return when {
        // Has <head> tag — inject immediately after it
        rawHtml.contains("<head>", ignoreCase = true) ||
        rawHtml.contains("<head ", ignoreCase = true) ->
            rawHtml.replace(Regex("""<head[^>]*>""", RegexOption.IGNORE_CASE)) { "${it.value}\n$injection" }

        // Has <html> but no <head> — inject a head
        rawHtml.contains("<html", ignoreCase = true) ->
            rawHtml.replace(Regex("""<html[^>]*>""", RegexOption.IGNORE_CASE)) { "${it.value}<head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>\n$injection</head>" }

        // Raw fragment — wrap fully
        else ->
            "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'>$injection</head><body>$rawHtml</body></html>"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Markdown → HTML converter (CommonMark-like, no external deps)
// Handles: ATX headings, setext headings, paragraphs, blockquotes, fenced code,
// indented code, ordered/unordered lists, horizontal rules, images, links,
// bold, italic, strikethrough, inline code, line breaks, HTML pass-through.
// ─────────────────────────────────────────────────────────────────────────────

private fun markdownToHtml(markdown: String, title: String = ""): String {
    val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").lines()
    val sb = StringBuilder()
    sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'>")
    if (title.isNotBlank()) sb.append("<title>${title.escapeHtml()}</title>")
    sb.append("</head><body>")

    var i = 0
    val inList = ArrayDeque<String>() // stack of "ul" or "ol"
    var inCodeFence = false
    var codeFenceChar = ""
    var codeFenceLang = ""
    val codeFenceContent = StringBuilder()
    var inBlockquote = false
    val blockquoteLines = mutableListOf<String>()

    fun closeList() {
        while (inList.isNotEmpty()) {
            sb.append("</${inList.removeLast()}>")
        }
    }

    fun flushBlockquote() {
        if (blockquoteLines.isNotEmpty()) {
            val inner = markdownToHtml(blockquoteLines.joinToString("\n"), "")
                .removePrefix("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'></head><body>")
                .removeSuffix("</body></html>")
            sb.append("<blockquote>$inner</blockquote>")
            blockquoteLines.clear()
        }
        inBlockquote = false
    }

    while (i < lines.size) {
        val line = lines[i]

        // ── Code fence ──
        if (!inCodeFence) {
            val fenceMatch = Regex("""^(`{3,}|~{3,})\s*(\S*)""").find(line)
            if (fenceMatch != null) {
                closeList()
                flushBlockquote()
                inCodeFence = true
                codeFenceChar = fenceMatch.groupValues[1].first().toString()
                codeFenceLang = fenceMatch.groupValues[2].escapeHtml()
                codeFenceContent.clear()
                i++; continue
            }
        } else {
            if (line.trimStart().startsWith(codeFenceChar.repeat(3))) {
                val lang = if (codeFenceLang.isNotBlank()) " class='language-$codeFenceLang'" else ""
                sb.append("<pre><code$lang>${codeFenceContent}</code></pre>")
                codeFenceContent.clear()
                inCodeFence = false
            } else {
                codeFenceContent.append(line.escapeHtml()).append("\n")
            }
            i++; continue
        }

        // ── Blockquote ──
        if (line.startsWith(">")) {
            inBlockquote = true
            blockquoteLines.add(line.removePrefix(">").trimStart())
            i++; continue
        } else if (inBlockquote) {
            flushBlockquote()
        }

        // ── Blank line ──
        if (line.isBlank()) {
            closeList()
            i++; continue
        }

        // ── ATX Headings (# through ######) ──
        val atxMatch = Regex("""^(#{1,6})\s+(.+?)(?:\s+#+)?$""").find(line)
        if (atxMatch != null) {
            closeList()
            val level = atxMatch.groupValues[1].length
            val text = inlineMarkdown(atxMatch.groupValues[2].trim())
            sb.append("<h$level>$text</h$level>")
            i++; continue
        }

        // ── Setext Headings (underline style) ──
        if (i + 1 < lines.size) {
            val next = lines[i + 1]
            if (next.matches(Regex("""={3,}\s*"""))) {
                closeList()
                sb.append("<h1>${inlineMarkdown(line.trim())}</h1>")
                i += 2; continue
            } else if (next.matches(Regex("""-{3,}\s*"""))) {
                closeList()
                sb.append("<h2>${inlineMarkdown(line.trim())}</h2>")
                i += 2; continue
            }
        }

        // ── Horizontal rule ──
        if (line.matches(Regex("""^(\*\*\*+|---+|___+)\s*$"""))) {
            closeList()
            sb.append("<hr>")
            i++; continue
        }

        // ── Unordered list ──
        val ulMatch = Regex("""^(\s*)([-*+])\s+(.*)""").find(line)
        if (ulMatch != null) {
            if (inList.isEmpty() || inList.last() != "ul") {
                closeList()
                inList.addLast("ul")
                sb.append("<ul>")
            }
            sb.append("<li>${inlineMarkdown(ulMatch.groupValues[3])}</li>")
            i++; continue
        }

        // ── Ordered list ──
        val olMatch = Regex("""^(\s*)\d+[.)]\s+(.*)""").find(line)
        if (olMatch != null) {
            if (inList.isEmpty() || inList.last() != "ol") {
                closeList()
                inList.addLast("ol")
                sb.append("<ol>")
            }
            sb.append("<li>${inlineMarkdown(olMatch.groupValues[2])}</li>")
            i++; continue
        }

        // ── Indented code block (4 spaces) ──
        if (line.startsWith("    ") || line.startsWith("\t")) {
            closeList()
            sb.append("<pre><code>")
            while (i < lines.size && (lines[i].startsWith("    ") || lines[i].startsWith("\t"))) {
                sb.append(lines[i].removePrefix("    ").removePrefix("\t").escapeHtml()).append("\n")
                i++
            }
            sb.append("</code></pre>")
            continue
        }

        // ── Paragraph (also handles HTML pass-through and trailing line-break) ──
        closeList()
        val paraLines = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank()
            && !lines[i].startsWith("#")
            && !lines[i].matches(Regex("""^(\*\*\*+|---+|___+)\s*$"""))
            && !lines[i].startsWith(">")
            && !Regex("""^(`{3,}|~{3,})""").containsMatchIn(lines[i])
            && !Regex("""^(\s*)([-*+])\s+""").containsMatchIn(lines[i])
            && !Regex("""^\d+[.)]\s+""").containsMatchIn(lines[i])
        ) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            val raw = paraLines.joinToString("\n")
            // Pass raw HTML blocks through unchanged
            if (raw.trimStart().startsWith("<") && raw.trimStart().let {
                    it.startsWith("<div") || it.startsWith("<table") || it.startsWith("<figure") ||
                    it.startsWith("<ul") || it.startsWith("<ol") || it.startsWith("<blockquote") ||
                    it.startsWith("<pre") || it.startsWith("</")
                }) {
                sb.append(raw)
            } else {
                // Soft line breaks (two trailing spaces) → <br>
                val processed = paraLines.mapIndexed { idx, l ->
                    if (idx < paraLines.size - 1 && l.endsWith("  ")) inlineMarkdown(l.trimEnd()) + "<br>" else inlineMarkdown(l)
                }.joinToString(" ")
                sb.append("<p>$processed</p>")
            }
        }
        continue
    }

    // Flush remaining
    closeList()
    flushBlockquote()
    if (inCodeFence && codeFenceContent.isNotEmpty()) {
        sb.append("<pre><code>${codeFenceContent}</code></pre>")
    }

    sb.append("</body></html>")
    return sb.toString()
}

/** Convert Markdown inline syntax to HTML. */
private fun inlineMarkdown(text: String): String {
    return text
        // Inline code — must come first to protect content inside backticks
        .replace(Regex("""`([^`]+)`""")) { "<code>${it.groupValues[1].escapeHtml()}</code>" }
        // Images before links
        .replace(Regex("""!\[([^\]]*)\]\(([^)]+)\)""")) { "<img src='${it.groupValues[2].escapeHtml()}' alt='${it.groupValues[1].escapeHtml()}'>" }
        // Links
        .replace(Regex("""\[([^\]]+)\]\(([^)]+)\)""")) { "<a href='${it.groupValues[2].escapeHtml()}'>${it.groupValues[1]}</a>" }
        // Bold + italic
        .replace(Regex("""\*\*\*(.+?)\*\*\*""")) { "<strong><em>${it.groupValues[1]}</em></strong>" }
        .replace(Regex("""___(.+?)___""")) { "<strong><em>${it.groupValues[1]}</em></strong>" }
        // Bold
        .replace(Regex("""\*\*(.+?)\*\*""")) { "<strong>${it.groupValues[1]}</strong>" }
        .replace(Regex("""__(.+?)__""")) { "<strong>${it.groupValues[1]}</strong>" }
        // Italic
        .replace(Regex("""\*(.+?)\*""")) { "<em>${it.groupValues[1]}</em>" }
        .replace(Regex("""_([^_]+)_""")) { "<em>${it.groupValues[1]}</em>" }
        // Strikethrough
        .replace(Regex("""~~(.+?)~~""")) { "<s>${it.groupValues[1]}</s>" }
        // Auto-links
        .replace(Regex("""<(https?://[^\s>]+)>""")) { "<a href='${it.groupValues[1]}'>${it.groupValues[1]}</a>" }
        .replace(Regex("""<([a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,})>""")) { "<a href='mailto:${it.groupValues[1]}'>${it.groupValues[1]}</a>" }
        // Escape remaining < > & that aren't inside HTML tags we already emitted
        .let { s ->
            // Only escape bare & that aren't already &entity;
            s.replace(Regex("""&(?!(?:[a-zA-Z]+|#\d+|#x[0-9a-fA-F]+);)"""), "&amp;")
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// FB2 (FictionBook 2.x) → HTML converter (pure regex/string, no XML parser needed)
// Handles: title-info, body sections, paragraphs, poetry, epigraphs, emphasis,
// strong, strikethrough, code, superscript, subscript, images (base64 inline),
// notes, links, tables.
// ─────────────────────────────────────────────────────────────────────────────

private fun fb2ToHtml(fb2: String): String {
    val sb = StringBuilder()
    sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'>")

    // Extract metadata
    val title = Regex("""<book-title[^>]*>(.*?)</book-title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .find(fb2)?.groupValues?.get(1)?.stripFb2Tags() ?: ""
    val author = buildString {
        val firstName = Regex("""<first-name>(.*?)</first-name>""", RegexOption.IGNORE_CASE).find(fb2)?.groupValues?.get(1) ?: ""
        val lastName = Regex("""<last-name>(.*?)</last-name>""", RegexOption.IGNORE_CASE).find(fb2)?.groupValues?.get(1) ?: ""
        if (firstName.isNotBlank() || lastName.isNotBlank()) append("$firstName $lastName".trim())
    }
    val description = Regex("""<annotation[^>]*>(.*?)</annotation>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .find(fb2)?.groupValues?.get(1)?.stripFb2Tags() ?: ""

    if (title.isNotBlank()) sb.append("<title>${title.escapeHtml()}</title>")
    sb.append("</head><body>")

    if (title.isNotBlank()) sb.append("<h1 class='hwaran-book-title'>${title.escapeHtml()}</h1>")
    if (author.isNotBlank()) sb.append("<p class='hwaran-book-author'>By ${author.escapeHtml()}</p>")
    if (description.isNotBlank()) sb.append("<blockquote>${description.escapeHtml()}</blockquote>")

    // Extract all <binary> base64 images and map id → data URL
    val binaryMap = mutableMapOf<String, String>()
    Regex("""<binary\s+[^>]*id=["']([^"']+)["'][^>]*content-type=["']([^"']+)["'][^>]*>(.*?)</binary>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(fb2).forEach { m ->
        binaryMap[m.groupValues[1]] = "data:${m.groupValues[2]};base64,${m.groupValues[3].replace(Regex("""\s"""), "")}"
    }
    // Also match reversed attribute order
    Regex("""<binary\s+[^>]*content-type=["']([^"']+)["'][^>]*id=["']([^"']+)["'][^>]*>(.*?)</binary>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(fb2).forEach { m ->
        binaryMap[m.groupValues[2]] = "data:${m.groupValues[1]};base64,${m.groupValues[3].replace(Regex("""\s"""), "")}"
    }

    // Process all <body> sections
    Regex("""<body[^>]*>(.*?)</body>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(fb2).forEach { bodyMatch ->
        sb.append(convertFb2Body(bodyMatch.groupValues[1], binaryMap))
    }

    sb.append("</body></html>")
    return sb.toString()
}

private fun convertFb2Body(body: String, binaryMap: Map<String, String>): String {
    val sb = StringBuilder()

    // Sections become chapters
    Regex("""<section[^>]*>(.*?)</section>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(body).forEach { secMatch ->
        sb.append("<div class='hwaran-chapter'>")
        sb.append(convertFb2Section(secMatch.groupValues[1], binaryMap))
        sb.append("</div><hr class='hwaran-chapter-divider'>")
    }

    // If no sections, treat whole body as one section
    if (!body.contains("<section", ignoreCase = true)) {
        sb.append(convertFb2Section(body, binaryMap))
    }

    return sb.toString()
}

private fun convertFb2Section(section: String, binaryMap: Map<String, String>): String {
    var html = section

    // Titles
    html = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .replace(html) { m -> "<h2 class='hwaran-chapter-title'>${m.groupValues[1].stripFb2Tags().escapeHtml()}</h2>" }

    // Epigraphs
    html = Regex("""<epigraph[^>]*>(.*?)</epigraph>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .replace(html) { m -> "<blockquote>${convertFb2Inline(m.groupValues[1], binaryMap)}</blockquote>" }

    // Poetry stanzas
    html = Regex("""<poem[^>]*>(.*?)</poem>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .replace(html) { m ->
            val inner = m.groupValues[1]
            val stanzas = Regex("""<stanza[^>]*>(.*?)</stanza>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(inner).joinToString("") { stanza ->
                    val lines = Regex("""<v[^>]*>(.*?)</v>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                        .findAll(stanza.groupValues[1]).joinToString("") { v ->
                            "<span class='line'>${convertFb2Inline(v.groupValues[1], binaryMap)}</span>"
                        }
                    "<div class='stanza'>$lines</div>"
                }
            "<div class='poem'>$stanzas</div>"
        }

    // Images
    html = Regex("""<image\s+[^>]*(?:href|l:href|xlink:href)=["']#?([^"']+)["'][^>]*/?>""", RegexOption.IGNORE_CASE)
        .replace(html) { m ->
            val id = m.groupValues[1]
            val src = binaryMap[id] ?: ""
            if (src.isNotBlank()) "<img src='$src' alt=''>" else ""
        }

    // Paragraphs
    html = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .replace(html) { m -> "<p>${convertFb2Inline(m.groupValues[1], binaryMap)}</p>" }

    // Empty-line elements (visual spacers in FB2)
    html = Regex("""<empty-line\s*/?>""", RegexOption.IGNORE_CASE).replace(html, "<br>")

    // Strip any remaining FB2 tags
    html = html.replace(Regex("""<(?!(?:p|h[1-6]|br|hr|div|blockquote|span|strong|em|s|code|sup|sub|img|a|ul|ol|li|figure|figcaption)[^>]*>)/?[a-zA-Z][^>]*>"""), "")

    return html
}

private fun convertFb2Inline(text: String, binaryMap: Map<String, String>): String {
    return text
        .replace(Regex("""<emphasis[^>]*>(.*?)</emphasis>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<em>${it.groupValues[1]}</em>" }
        .replace(Regex("""<strong[^>]*>(.*?)</strong>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<strong>${it.groupValues[1]}</strong>" }
        .replace(Regex("""<strikethrough[^>]*>(.*?)</strikethrough>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<s>${it.groupValues[1]}</s>" }
        .replace(Regex("""<code[^>]*>(.*?)</code>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<code>${it.groupValues[1].escapeHtml()}</code>" }
        .replace(Regex("""<sup[^>]*>(.*?)</sup>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<sup>${it.groupValues[1]}</sup>" }
        .replace(Regex("""<sub[^>]*>(.*?)</sub>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { "<sub>${it.groupValues[1]}</sub>" }
        .replace(Regex("""<a\s+[^>]*(?:href|l:href|xlink:href)=["']#?([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { m ->
            "<a href='#${m.groupValues[1]}'>${m.groupValues[2]}</a>"
        }
        .replace(Regex("""<[^>]+>"""), "") // strip any remaining tags
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", "\u00A0")
}

private fun String.stripFb2Tags(): String =
    this.replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&nbsp;", " ").trim()

// ─────────────────────────────────────────────────────────────────────────────
// Plain Text → HTML converter (smart paragraph detection)
// Separate from buildHtmlFromNovelBook; used for direct stream rendering
// ─────────────────────────────────────────────────────────────────────────────

private fun plainTextToHtml(text: String, title: String = ""): String {
    val sb = StringBuilder()
    sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'>")
    if (title.isNotBlank()) sb.append("<title>${title.escapeHtml()}</title>")
    sb.append("</head><body>")
    if (title.isNotBlank()) sb.append("<h1 class='hwaran-book-title'>${title.escapeHtml()}</h1>")

    val clean = text.trimStart('\uFEFF')
        .replace(Regex("""(?si)<style\b[^>]*>.*?</style>"""), "")
        .replace(Regex("""(?si)<script\b[^>]*>.*?</script>"""), "")
        .replace(Regex("""(?si)@(?:page|charset|import|media|font-face|keyframes|supports)[^{]*\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}"""), "")
        .replace(Regex("""(?si)(?:^|\n)\s*[a-zA-Z0-9_\-#.:*~>\[\]"=' ,]+\s*\{[^{}]*\}"""), "")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    val blocks = clean.split(Regex("""\n{2,}"""))

    blocks.forEach { block ->
        val trimmed = block.trim()
        if (trimmed.isBlank()) return@forEach
        val lines = trimmed.lines()
        val isVerse = lines.size >= 2 && run {
            val shortLines = lines.count { it.length < 72 }
            shortLines.toFloat() / lines.size > 0.65f && lines.sumOf { it.length } / lines.size < 65
        }
        if (isVerse) {
            sb.append("<div class='stanza'>")
            lines.forEach { line ->
                val indent = line.length - line.trimStart().length
                val cls = when { indent >= 6 -> "line i3"; indent >= 4 -> "line i2"; indent >= 2 -> "line i1"; else -> "line" }
                sb.append("<span class='$cls'>${line.escapeHtml()}</span>")
            }
            sb.append("</div>")
        } else {
            val prose = lines.joinToString(" ") { it.trim() }.trim().escapeHtml()
            if (prose.isNotBlank()) sb.append("<p>$prose</p>")
        }
    }

    sb.append("</body></html>")
    return sb.toString()
}
