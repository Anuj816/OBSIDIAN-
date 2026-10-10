package com.anuj.obsidian.audio.lyrics

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

@Immutable
data class WordSync(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

@Immutable
data class KaraokeLine(
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val romanizedText: String = "",
    val translatedText: String = "",
    val words: List<WordSync> = emptyList()
)

object SyncedKaraokeLyricsManager {
    private val TIME_TAG_REGEX = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val WORD_TAG_REGEX = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>([^<]+)")

    /**
     * Parses raw LRC text into a list of structured KaraokeLine items.
     */
    suspend fun parseKaraokeLyrics(rawLrc: String): List<KaraokeLine> = withContext(Dispatchers.Default) {
        if (rawLrc.isBlank()) return@withContext emptyList()

        val parsedLines = mutableListOf<KaraokeLine>()
        val lines = rawLrc.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || 
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) {
                continue
            }

            val matcher = TIME_TAG_REGEX.matcher(trimmed)
            val timeOffsets = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3) ?: "0"
                val millis = when (millisRaw.length) {
                    1 -> millisRaw.toLong() * 100
                    2 -> millisRaw.toLong() * 10
                    else -> millisRaw.take(3).toLong()
                }
                val totalMs = min * 60000L + sec * 1000L + millis
                timeOffsets.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timeOffsets.isEmpty()) continue

            val textContent = trimmed.substring(lastMatchEnd).trim()
            if (textContent.isBlank()) continue

            val words = parseWordSyncs(textContent, timeOffsets.first())
            val cleanText = if (words.isNotEmpty()) words.joinToString(" ") { it.word } else textContent
            val romanized = romanizeText(cleanText)

            for (startTime in timeOffsets) {
                parsedLines.add(
                    KaraokeLine(
                        startMs = startTime,
                        endMs = startTime + 4500L, // Default estimated end until adjusted
                        originalText = cleanText,
                        romanizedText = romanized,
                        words = words
                    )
                )
            }
        }

        val sorted = parsedLines.sortedBy { it.startMs }
        // Adjust endMs to next line start
        val result = mutableListOf<KaraokeLine>()
        for (i in sorted.indices) {
            val curr = sorted[i]
            val nextStart = if (i < sorted.size - 1) sorted[i + 1].startMs else (curr.startMs + 6000L)
            val adjustedEnd = if (nextStart > curr.startMs) minOf(curr.startMs + 8000L, nextStart) else curr.startMs + 4000L
            result.add(curr.copy(endMs = adjustedEnd))
        }

        result
    }

    private fun parseWordSyncs(text: String, lineStartMs: Long): List<WordSync> {
        val wordMatcher = WORD_TAG_REGEX.matcher(text)
        val words = mutableListOf<WordSync>()
        var lastWordStart = lineStartMs

        while (wordMatcher.find()) {
            val min = wordMatcher.group(1)?.toLongOrNull() ?: 0L
            val sec = wordMatcher.group(2)?.toLongOrNull() ?: 0L
            val millisRaw = wordMatcher.group(3) ?: "0"
            val millis = when (millisRaw.length) {
                1 -> millisRaw.toLong() * 100
                2 -> millisRaw.toLong() * 10
                else -> millisRaw.take(3).toLong()
            }
            val wordTime = min * 60000L + sec * 1000L + millis
            val wordText = wordMatcher.group(4)?.trim() ?: ""

            if (wordText.isNotBlank()) {
                words.add(WordSync(word = wordText, startMs = lastWordStart, endMs = wordTime))
                lastWordStart = wordTime
            }
        }

        return words
    }

    /**
     * Romanization helper: Transliterates East Asian scripts (Hiragana, Katakana, Hangul syllables)
     * into easy-to-pronounce Romaji for karaoke singing.
     */
    fun romanizeText(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder()
        var hasNonLatin = false

        for (ch in text) {
            val code = ch.code
            when {
                // Hiragana range 0x3041 - 0x3096
                code in 0x3041..0x3096 -> {
                    hasNonLatin = true
                    sb.append(HIRAGANA_MAP[ch] ?: ch.toString())
                }
                // Katakana range 0x30A1 - 0x30FA
                code in 0x30A1..0x30FA -> {
                    hasNonLatin = true
                    sb.append(KATAKANA_MAP[ch] ?: ch.toString())
                }
                // Hangul Syllables 0xAC00 - 0xD7A3
                code in 0xAC00..0xD7A3 -> {
                    hasNonLatin = true
                    sb.append(hangulToRomaji(ch))
                }
                else -> sb.append(ch)
            }
        }

        return if (hasNonLatin) sb.toString() else ""
    }

    private fun hangulToRomaji(ch: Char): String {
        val code = ch.code - 0xAC00
        val initialIdx = code / (21 * 28)
        val medialIdx = (code % (21 * 28)) / 28
        val finalIdx = code % 28

        val initial = HANGUL_INITIALS.getOrElse(initialIdx) { "" }
        val medial = HANGUL_MEDIALS.getOrElse(medialIdx) { "" }
        val final = HANGUL_FINALS.getOrElse(finalIdx) { "" }

        return "$initial$medial$final"
    }

    private val HANGUL_INITIALS = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val HANGUL_MEDIALS = arrayOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
    private val HANGUL_FINALS = arrayOf("", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h")

    private val HIRAGANA_MAP = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n"
    )

    private val KATAKANA_MAP = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヲ' to "wo", 'ン' to "n"
    )
}
