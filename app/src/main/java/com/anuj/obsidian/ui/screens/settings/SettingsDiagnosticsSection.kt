package com.anuj.obsidian.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.RecurringCrashGroup
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.data.model.AppSettings
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.PitchBlack
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.getActiveThemePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * =========================================================================================
 * Settings Diagnostics Section (Tab 4)
 * =========================================================================================
 * RESPONSIBILITY:
 * System diagnostics, Crashlytics telemetry monitor, Hardware/Runtime overview,
 * crash logs inspection with expandable stack traces, recurring crash signature groups,
 * and diagnostic report generation.
 * =========================================================================================
 */
fun LazyListScope.settingsDiagnosticsSection(
    settings: AppSettings,
    viewModel: ObsidianViewModel,
    context: Context,
    clipboardManager: ClipboardManager
) {
    item {
        val crashLogs by viewModel.crashLogs.collectAsStateWithLifecycle()
        val recurringCrashGroups by viewModel.recurringCrashGroups.collectAsStateWithLifecycle()
        val activePalette = getActiveThemePalette(settings)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Storage & Cache Cleanup Tool
            StorageCleanupToolSection(
                viewModel = viewModel,
                context = context,
                activePalette = activePalette
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM DIAGNOSTICS & TELEMETRY",
                        color = activePalette.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Crash Logs & Firebase Monitor",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = activePalette.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Hardware & System Architecture Overview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Hardware & Runtime Environment",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Device Hardware:", color = TextSecondary, fontSize = 12.5.sp)
                        Text(
                            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                            color = activePalette.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Android System Release:", color = TextSecondary, fontSize = 12.5.sp)
                        Text(
                            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Playback Engine:", color = TextSecondary, fontSize = 12.5.sp)
                        Text(
                            "MediaSessionService + Media3 ExoPlayer",
                            color = FlacLosslessCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Crashlytics Telemetry:", color = TextSecondary, fontSize = 12.5.sp)
                        Text(
                            "ACTIVE (Auto-Catch & Custom Keys)",
                            color = FlacHiResGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // Actions Row: Refresh, Copy All, Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshCrashLogs() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = activePalette.primary),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Refresh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val report = ObsidianCrashLogger.generateFullDiagnosticReport(context)
                        clipboardManager.setText(AnnotatedString(report))
                        Toast.makeText(context, "Full system report copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FlacLosslessCyan),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (crashLogs.isNotEmpty()) {
                            val sb = StringBuilder()
                            sb.append("=== OBSIDIAN SYSTEM CRASH LOGS ===\n")
                            sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android API ${android.os.Build.VERSION.SDK_INT})\n\n")
                            crashLogs.forEach { log ->
                                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp))
                                sb.append("[$dateStr] [${log.level}] [${log.tag}]: ${log.message}\n")
                                if (!log.diagnosticAdvice.isNullOrBlank()) {
                                    sb.append("Remedy: ${log.diagnosticAdvice}\n")
                                }
                                if (!log.deviceState.isNullOrBlank()) {
                                    sb.append("Device State: ${log.deviceState}\n")
                                }
                                if (!log.stackTrace.isNullOrBlank()) {
                                    sb.append("Stack Trace:\n${log.stackTrace}\n")
                                }
                                sb.append("---\n")
                            }
                            clipboardManager.setText(AnnotatedString(sb.toString()))
                            Toast.makeText(context, "Copied ${crashLogs.size} log item(s) to clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No crash logs to copy", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        viewModel.clearCrashLogs()
                        Toast.makeText(context, "Crash logs cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (recurringCrashGroups.isNotEmpty()) {
                Text(
                    text = "RECURRING CRASH SIGNATURES (CRITICAL/ERROR)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recurringCrashGroups.forEach { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFF5252).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${group.count}x Occurrences",
                                                color = Color(0xFFFF5252),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = group.exceptionType,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Top frame: ${group.topStackFrame}",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = group.sampleMessage,
                                        color = Color(0xFFFF9800),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (group != recurringCrashGroups.last()) {
                                HorizontalDivider(color = DarkSurfaceBorder)
                            }
                        }
                    }
                }
            }

            // Logs List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECORDED EXCEPTION & PLAYBACK STACK TRACES (${crashLogs.size})",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            if (crashLogs.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = FlacLosslessCyan,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No System Crashes or Playback Exceptions Captured",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The Media3 ExoPlayer engine & MediaSessionService are running with 100% stability. Playback errors and uncaught thread exceptions will automatically log here with full stack traces.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    crashLogs.forEach { logItem ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val dateStr = remember(logItem.timestamp) {
                            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(logItem.timestamp))
                        }
                        val levelColor = when (logItem.level) {
                            "PLAYBACK_ERROR" -> Color(0xFFFF5252)
                            "ERROR" -> Color(0xFFFF9800)
                            else -> activePalette.primary
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, levelColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = levelColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "[${logItem.level}] ${logItem.tag}",
                                            color = levelColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Text(
                                        text = dateStr,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    text = logItem.message,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (!logItem.diagnosticAdvice.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = FlacLosslessCyan.copy(alpha = 0.08f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, FlacLosslessCyan.copy(alpha = 0.25f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = FlacLosslessCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = logItem.diagnosticAdvice,
                                                color = Color(0xFF80DEEA),
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                if (!logItem.deviceState.isNullOrBlank()) {
                                    Text(
                                        text = "Telemetry: ${logItem.deviceState}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (!logItem.stackTrace.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isExpanded) "Tap to hide stack trace" else "Tap to view stack trace",
                                            color = activePalette.primary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(logItem.stackTrace))
                                                Toast.makeText(context, "Stack trace copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Stack Trace",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PitchBlack,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = logItem.stackTrace,
                                                color = Color(0xFFFFCC80),
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringCrashGroupsSection(
    recurringCrashGroups: List<RecurringCrashGroup>,
    activePalette: com.anuj.obsidian.ui.theme.AppThemePalette
) {
    if (recurringCrashGroups.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Recurring Crash Groups", color = Color.Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                recurringCrashGroups.forEach { group ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            "${group.exceptionType}: ${group.topStackFrame}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Occurrences: ${group.count}", color = activePalette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                            Text("Last seen: ${sdf.format(Date(group.lastTimestamp))}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
