package com.anuj.obsidian.ui.visualizer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class PresetValidationResult {
    data class Valid(
        val sanitizedFile: File?,
        val sanitizedContent: String,
        val presetIR: MilkPresetIR?,
        val isNativeSafe: Boolean
    ) : PresetValidationResult()

    data class Invalid(
        val reason: String,
        val fallbackPresetIR: MilkPresetIR?
    ) : PresetValidationResult()
}

/**
 * Robust Background Validator for .milk Presets.
 * Validates file integrity, sanitizes syntax, pre-compiles IR, and guards the native C++ engine.
 */
object MilkdropPresetValidator {
    private const val TAG = "MilkdropPresetValidator"
    private const val MAX_PRESET_BYTES = 5 * 1024 * 1024 // 5MB limit

    /**
     * Validates and sanitizes a .milk file on Dispatchers.IO before passing to the native projectM C++ engine.
     */
    suspend fun validateAndSanitize(
        context: Context,
        path: String?,
        rawContent: String?
    ): PresetValidationResult = withContext(Dispatchers.IO) {
        try {
            var content = rawContent
            var sourceFile: File? = null

            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    sourceFile = f
                    val length = f.length()
                    if (length > MAX_PRESET_BYTES) {
                        return@withContext PresetValidationResult.Invalid(
                            "Preset file exceeds 5MB safety limit ($length bytes)",
                            fallbackPresetIR = null
                        )
                    }
                    if (length < 8) {
                        return@withContext PresetValidationResult.Invalid(
                            "Preset file is too small or empty ($length bytes)",
                            fallbackPresetIR = null
                        )
                    }
                    if (content.isNullOrBlank()) {
                        content = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                    }
                }
            }

            if (content.isNullOrBlank()) {
                return@withContext PresetValidationResult.Invalid(
                    "No readable preset content or file not accessible",
                    fallbackPresetIR = null
                )
            }

            // 1. Binary corruption check (check for excessive null bytes / non-text binaries)
            var nullByteCount = 0
            val checkLen = minOf(content.length, 1024)
            for (i in 0 until checkLen) {
                if (content[i] == '\u0000') nullByteCount++
            }
            if (nullByteCount > 2) {
                return@withContext PresetValidationResult.Invalid(
                    "Preset contains binary null bytes (corrupted .milk format)",
                    fallbackPresetIR = null
                )
            }

            // 2. Syntax & Section Sanitization
            val sanitized = sanitizeMilkContent(content)
            
            // 3. Mathematical parenthetical balance check
            val parenCheck = validateParentheses(sanitized)
            if (!parenCheck.isValid) {
                Log.w(TAG, "Parenthesis imbalance detected in preset: ${parenCheck.error}")
            }

            // 4. Parse IR
            val parsedIR = try {
                MilkdropPresetParser.parse(sanitized)
            } catch (e: Throwable) {
                Log.w(TAG, "IR parser notice: ${e.message}")
                null
            }

            // 5. Native safety check
            val isNativeSafe = isContentSafeForNativeProjectM(sanitized)

            // Cache sanitized file in native cache folder for C++ file loading
            val cachedFile = sourceFile ?: try {
                val fileName = if (!path.isNullOrBlank()) File(path).name else "validated_${System.currentTimeMillis()}.milk"
                MilkdropFileStructure.getNativeCachePresetFile(context, fileName, sanitized)
            } catch (e: Throwable) {
                null
            }

            return@withContext PresetValidationResult.Valid(
                sanitizedFile = cachedFile,
                sanitizedContent = sanitized,
                presetIR = parsedIR,
                isNativeSafe = isNativeSafe
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Preset validation exception: ${t.message}", t)
            return@withContext PresetValidationResult.Invalid(
                "Exception during validation: ${t.message}",
                fallbackPresetIR = null
            )
        }
    }

    private fun sanitizeMilkContent(raw: String): String {
        val lines = raw.lines()
        val sb = StringBuilder(raw.length + 64)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cleanLine = trimmed.filter { it == '\t' || it in ' '..'~' || it >= '\u00A0' }
            sb.append(cleanLine).append("\n")
        }
        return sb.toString()
    }

    private data class ParenValidation(val isValid: Boolean, val error: String? = null)

    private fun validateParentheses(content: String): ParenValidation {
        var balance = 0
        var lineNum = 1
        for (ch in content) {
            if (ch == '\n') lineNum++
            if (ch == '(') balance++
            if (ch == ')') {
                balance--
                if (balance < 0) return ParenValidation(false, "Unmatched closing parenthesis at line $lineNum")
            }
        }
        return if (balance == 0) ParenValidation(true) else ParenValidation(false, "Unclosed parenthesis ($balance missing)")
    }

    private fun isContentSafeForNativeProjectM(content: String): Boolean {
        val lower = content.lowercase()
        if (lower.contains("#include") || lower.contains("__declspec")) {
            return false
        }
        return true
    }
}
