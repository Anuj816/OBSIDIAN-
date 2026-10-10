package com.anuj.obsidian.ui.visualizer

/**
 * Severity level for preset parsing, expression compilation, or shader validation diagnostics.
 */
enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

/**
 * Structured diagnostic recording details of any malformed line, unknown token, or unsupported feature
 * without aborting the loading of the preset.
 */
data class PresetDiagnostic(
    val severity: DiagnosticSeverity,
    val line: Int,
    val key: String,
    val message: String
)
