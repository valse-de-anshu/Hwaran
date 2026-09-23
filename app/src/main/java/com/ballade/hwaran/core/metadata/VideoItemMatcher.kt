package com.ballade.hwaran.core.metadata

/**
 * Utility for matching video file titles (e.g. "1. What is Vibe Coding (And Why It Might Ruin Your Codebase) - Devsplainers.mp4")
 * with JSON metadata entries (e.g. "What is Vibe Coding? (And Why It Might Ruin Your Codebase)").
 *
 * Specially aware that Zine Scraper prefixes files with numbers representing oldest first (oldest = 1),
 * while YouTube / scraper JSON video arrays are ordered newest first (index 0 = newest, index N-1 = oldest).
 */
object VideoItemMatcher {

    /**
     * Cleans up a title string by:
     * 1. Lowercasing
     * 2. Removing file extensions (.mp4, .mkv, .webm, .avi, .flv, .mov, etc.)
     * 3. Removing leading numbers (e.g. "1. ", "01 - ", "1 - ", "[1] ")
     * 4. Removing channel/author name suffix if provided (e.g. " - Devsplainers")
     * 5. Stripping non-alphanumeric characters.
     */
    fun cleanTitle(raw: String, authorOrChannel: String? = null): String {
        var text = raw.lowercase()
        // Strip extension
        text = text.replace(Regex("""\.(mp4|mkv|webm|avi|flv|mov|m4v|3gp)$""", RegexOption.IGNORE_CASE), "")
        // Strip leading numbering e.g. "1. ", "01 - ", "1 - ", "[1] "
        text = text.replace(Regex("""^\s*\[?\s*\d+\s*\]?\s*[.\-_)]\s*"""), "")
        text = text.replace(Regex("""^\s*\d+\s+"""), "")

        // Strip author/channel suffix if present
        if (!authorOrChannel.isNullOrBlank()) {
            val authorClean = authorOrChannel.trim().lowercase()
            text = text.replace(Regex("""\s*[-–—]?\s*${Regex.escape(authorClean)}\s*$""", RegexOption.IGNORE_CASE), "")
        }

        // Retain letters and digits
        return text.replace(Regex("""[^\p{L}0-9]"""), "")
    }

    /**
     * Finds the best matching VideoItemMetadata for a given video/chapter title and position.
     */
    fun findMatch(
        videoTitle: String,
        position: Int = -1,
        authorOrChannel: String? = null,
        items: List<VideoItemMetadata>
    ): VideoItemMetadata? {
        if (items.isEmpty()) return null

        val cleanVideo = cleanTitle(videoTitle, authorOrChannel)
        val cleanVideoNoAuthor = cleanTitle(videoTitle, null)

        // 1. Direct clean match
        for (item in items) {
            val cleanItem = cleanTitle(item.title, authorOrChannel)
            val cleanItemNoAuthor = cleanTitle(item.title, null)
            if (cleanVideo.isNotBlank() && (cleanVideo == cleanItem || cleanVideo == cleanItemNoAuthor || cleanVideoNoAuthor == cleanItem || cleanVideoNoAuthor == cleanItemNoAuthor)) {
                return item
            }
        }

        // 2. Substring match
        for (item in items) {
            val cleanItem = cleanTitle(item.title, authorOrChannel)
            val cleanItemNoAuthor = cleanTitle(item.title, null)
            if (cleanVideo.length >= 5 && cleanItem.length >= 5) {
                if (cleanVideo.contains(cleanItem) || cleanItem.contains(cleanVideo) ||
                    cleanVideoNoAuthor.contains(cleanItemNoAuthor) || cleanItemNoAuthor.contains(cleanVideoNoAuthor)
                ) {
                    return item
                }
            }
        }

        val videoTokens = tokenize(videoTitle)

        // 3. Zine Scraper number prefix match:
        // Zine Scraper assigns 1 to the OLDEST video.
        // In the JSON array, index 0 is newest, and index (items.size - 1) is oldest.
        // Therefore, prefix `k` corresponds to reverse index `items.size - k`.
        val indexMatch = Regex("""^\s*(\d+)\s*[.\-_)]""").find(videoTitle)
        if (indexMatch != null) {
            val k = indexMatch.groupValues[1].toIntOrNull()
            if (k != null && k >= 1 && k <= items.size) {
                val reverseIndex = items.size - k
                val reverseCandidate = items[reverseIndex]
                val cleanReverse = cleanTitle(reverseCandidate.title, authorOrChannel)
                // If title matches reverse candidate or has shared tokens
                if (cleanVideo == cleanReverse || cleanVideo.contains(cleanReverse) || cleanReverse.contains(cleanVideo) ||
                    videoTokens.intersect(tokenize(reverseCandidate.title)).isNotEmpty()
                ) {
                    return reverseCandidate
                }

                // Also check 1-based forward index just in case JSON is already oldest-first
                val forwardIndex = k - 1
                val forwardCandidate = items[forwardIndex]
                val cleanForward = cleanTitle(forwardCandidate.title, authorOrChannel)
                if (cleanVideo == cleanForward || cleanVideo.contains(cleanForward) || cleanForward.contains(cleanVideo) ||
                    videoTokens.intersect(tokenize(forwardCandidate.title)).isNotEmpty()
                ) {
                    return forwardCandidate
                }

                // If no tokens matched, reverseCandidate is still the most likely candidate for Zine scraper
                return reverseCandidate
            }
        }

        // 4. Chapter position match (0-indexed or 1-indexed)
        if (position >= 0 && position < items.size) {
            val posCandidate = items[position]
            if (videoTokens.intersect(tokenize(posCandidate.title)).isNotEmpty()) {
                return posCandidate
            }
        }

        // 5. Token overlap / Jaccard similarity fallback
        var bestItem: VideoItemMetadata? = null
        var bestScore = 0.0

        if (videoTokens.isNotEmpty()) {
            for (item in items) {
                val itemTokens = tokenize(item.title)
                if (itemTokens.isEmpty()) continue
                val intersection = videoTokens.intersect(itemTokens).size
                val union = videoTokens.union(itemTokens).size
                if (union > 0) {
                    val jaccard = intersection.toDouble() / union.toDouble()
                    if (jaccard > bestScore && jaccard >= 0.25) {
                        bestScore = jaccard
                        bestItem = item
                    }
                }
            }
        }

        return bestItem
    }

    private fun tokenize(text: String): Set<String> {
        val stopWords = setOf("the", "a", "an", "and", "or", "in", "on", "at", "to", "for", "of", "with", "by", "is", "it", "this", "that")
        return text.lowercase()
            .replace(Regex("""\.(mp4|mkv|webm|avi|flv|mov)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length > 1 && !stopWords.contains(it) }
            .toSet()
    }
}
