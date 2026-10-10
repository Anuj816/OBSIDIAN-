package com.anuj.obsidian.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anuj.obsidian.ObsidianCrashLogger
import com.anuj.obsidian.ui.ObsidianViewModel
import com.anuj.obsidian.ui.components.ColorTarget
import com.anuj.obsidian.ui.components.ColorWheelPickerSheet
import com.anuj.obsidian.ui.components.ObsidianDspBottomSheet
import com.anuj.obsidian.ui.screens.settings.settingsDacEngineSection
import com.anuj.obsidian.ui.screens.settings.settingsDiagnosticsSection
import com.anuj.obsidian.ui.screens.settings.settingsDownloadsStorageSection
import com.anuj.obsidian.ui.screens.settings.settingsNetworkNasSection
import com.anuj.obsidian.ui.screens.settings.settingsPresetsEqSection
import com.anuj.obsidian.ui.screens.settings.settingsThemeVisualizerSection
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.ObsidianTheme

/**
 * =========================================================================================
 * SettingsScreen - Obsidian Master Settings Architecture
 * =========================================================================================
 * Modular controller orchestrating sub-tab sections:
 * - Tab 0: Presets & EQ (SettingsPresetsEqSection)
 * - Tab 1: DAC & Engine (SettingsDacEngineSection)
 * - Tab 2: Downloads & SAF (SettingsDownloadsStorageSection)
 * - Tab 3: Theme & Visualizer (SettingsThemeVisualizerSection)
 * - Tab 4: System Diagnostics & Crash Telemetry (SettingsDiagnosticsSection)
 * =========================================================================================
 */
@Composable
fun SettingsScreen(
    viewModel: ObsidianViewModel,
    modifier: Modifier = Modifier,
    onManageFolders: () -> Unit = {}
) {
    val context = LocalContext.current
    val dspState by viewModel.dspState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var showDonateDialog by remember { mutableStateOf(false) }
    var showDspSheet by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf(0) } // 0: Presets & EQ, 1: DAC & Engine, 2: Downloads & SAF, 3: Theme & Colors, 4: Storage & Debug
    var showColorWheelSheet by remember { mutableStateOf(false) }
    var colorWheelInitialTarget by remember { mutableStateOf(ColorTarget.PRIMARY) }

    val downloadFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                ObsidianCrashLogger.logException(e, "SettingsScreen.downloadFolderPicker")
            }
            val doc = try { DocumentFile.fromTreeUri(context, treeUri) } catch (_: Throwable) { null }
            val name = doc?.name ?: "Custom Download Directory"
            viewModel.updateCustomDownloadFolder(treeUri, name)
            Toast.makeText(context, "Downloads will save to: $name", Toast.LENGTH_SHORT).show()
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportFullBackup(context, uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFullBackup(context, uri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUDIOPHILE ENGINE & AUDIO LAB",
                        color = ObsidianTheme.colors.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Obsidian Settings",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (dspState.isDspEnabled) ObsidianTheme.colors.primary.copy(alpha = 0.2f) else DarkSurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (dspState.isDspEnabled) ObsidianTheme.colors.primary else DarkSurfaceBorder
                    ),
                    modifier = Modifier.clickable { viewModel.setDspEnabled(!dspState.isDspEnabled) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (dspState.isDspEnabled) ObsidianTheme.colors.primary else TextMuted)
                        )
                        Text(
                            text = if (dspState.isDspEnabled) "DSP ACTIVE" else "DSP BYPASS",
                            color = if (dspState.isDspEnabled) ObsidianTheme.colors.primary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Studio DSP console quick launch card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDspSheet = true }
                    .testTag("open_dsp_studio_console_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (dspState.isDspEnabled) ObsidianTheme.colors.primary.copy(alpha = 0.12f) else DarkSurfaceCard
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (dspState.isDspEnabled) ObsidianTheme.colors.primary.copy(alpha = 0.5f) else DarkSurfaceBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (dspState.isDspEnabled) ObsidianTheme.colors.primary else DarkSurfaceBorder,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Studio DSP",
                                    tint = if (dspState.isDspEnabled) Color.Black else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Studio DSP Console",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (dspState.isDspEnabled) "10-Band EQ, Tube Harmonics & Spatial Active" else "Tap to open floating audiophile console",
                                color = if (dspState.isDspEnabled) ObsidianTheme.colors.primary else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianTheme.colors.primary,
                        contentColor = Color.Black
                    ) {
                        Text(
                            text = "Open Console",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Donate to Creator Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDonateDialog = true }
                    .testTag("donate_to_creator_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FlacHiResGold.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = FlacHiResGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Donate to Creator",
                                    tint = FlacHiResGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Donate to Creator",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Support development with UPI / GPay / PhonePe",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = FlacHiResGold,
                        contentColor = Color.Black
                    ) {
                        Text(
                            text = "Support \u2764",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Sub-Tab Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceCard)
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("Presets & EQ", "DAC & Engine", "NAS & Network", "Downloads & SAF", "Theme & Colors", "Storage & Debug")
                tabs.forEachIndexed { index, label ->
                    val isSelected = activeSubTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ObsidianTheme.colors.primary else Color.Transparent)
                            .clickable { activeSubTab = index }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        when (activeSubTab) {
            0 -> settingsPresetsEqSection(
                dspState = dspState,
                viewModel = viewModel
            )

            1 -> settingsDacEngineSection(
                dspState = dspState,
                settings = settings,
                sleepTimerMinutes = sleepTimerMinutes,
                viewModel = viewModel,
                onManageFolders = onManageFolders
            )

            2 -> settingsNetworkNasSection()

            3 -> settingsDownloadsStorageSection(
                settings = settings,
                viewModel = viewModel,
                context = context,
                onManageFolders = onManageFolders,
                downloadFolderPickerLauncher = downloadFolderPickerLauncher,
                exportBackupLauncher = exportBackupLauncher,
                importBackupLauncher = importBackupLauncher
            )

            4 -> settingsThemeVisualizerSection(
                settings = settings,
                viewModel = viewModel,
                onOpenColorWheel = { target ->
                    colorWheelInitialTarget = target
                    showColorWheelSheet = true
                }
            )

            5 -> settingsDiagnosticsSection(
                settings = settings,
                viewModel = viewModel,
                context = context,
                clipboardManager = clipboardManager
            )
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showDspSheet) {
        ObsidianDspBottomSheet(
            onDismiss = { showDspSheet = false }
        )
    }

    if (showColorWheelSheet) {
        ColorWheelPickerSheet(
            initialPrimary = settings.customPrimaryColor,
            initialSecondary = settings.customSecondaryColor,
            initialBackground = settings.customBackgroundColor,
            initialTarget = colorWheelInitialTarget,
            currentTrack = playerState.currentTrack,
            onDismiss = { showColorWheelSheet = false },
            onApplyColors = { p, s, bg ->
                viewModel.setCustomColors(p, s, bg)
            }
        )
    }

    if (showDonateDialog) {
        var selectedAmount by remember { mutableStateOf("50") }
        val presetAmounts = listOf("10", "50", "100", "500")

        val qrUrl = remember(selectedAmount) {
            val baseData = "upi://pay?pa=9466142854@fam&pn=Anuj Kumar&cu=INR"
            val fullData = if (selectedAmount.isNotEmpty()) "$baseData&am=$selectedAmount" else baseData
            "https://api.qrserver.com/v1/create-qr-code/?size=350x350&data=" + java.net.URLEncoder.encode(fullData, "UTF-8")
        }

        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            containerColor = Color(0xFF131520),
            icon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = FlacHiResGold,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Donate to Creator",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // UPI QR Code Image
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(2.dp, FlacHiResGold),
                        modifier = Modifier.size(140.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = qrUrl,
                                contentDescription = "UPI Payment QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }

                    Text(
                        text = "Scan QR with GPay / PhonePe / Paytm / FamApp",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Preset Amount Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetAmounts.forEach { amt ->
                            val isSelected = selectedAmount == amt
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) FlacHiResGold else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) FlacHiResGold else TextMuted.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedAmount = if (isSelected) "" else amt }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "₹$amt",
                                        color = if (isSelected) Color.Black else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // UPI ID Copy Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FlacHiResGold.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString("9466142854@fam"))
                                Toast.makeText(context, "UPI ID copied: 9466142854@fam", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "9466142854@fam",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Name: Anuj Kumar (FamApp)",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = FlacHiResGold.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FlacHiResGold)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = FlacHiResGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        color = FlacHiResGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Quick App Launcher Buttons Row
                    Text(
                        text = "Or tap to copy & open payment app:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("GPay", "com.google.android.apps.nps", Color(0xFF4285F4)),
                            Triple("PhonePe", "com.phonepe.app", Color(0xFF5F259F)),
                            Triple("Paytm", "net.one97.paytm", Color(0xFF00BAF2)),
                            Triple("FamApp", "com.fampay.in", Color(0xFFFFB800))
                        ).forEach { (appName, pkg, btnColor) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = btnColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, btnColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        openUpiPaymentApp(context, packageName = pkg, amount = selectedAmount)
                                        showDonateDialog = false
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = appName,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "💡 Note: Indian banks restrict direct app links for personal VPAs ('exceeded limit'). Scanning the QR code or pasting 9466142854@fam in GPay/PhonePe works 100%!",
                        color = FlacLosslessCyan,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString("9466142854@fam"))
                        Toast.makeText(context, "UPI ID copied: 9466142854@fam. Open GPay / PhonePe to paste!", Toast.LENGTH_LONG).show()
                        showDonateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlacHiResGold, contentColor = Color.Black)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy UPI ID", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDonateDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary)
                ) {
                    Text("Close", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }
}

private fun openUpiPaymentApp(context: Context, packageName: String? = null, amount: String? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard != null) {
        val clip = ClipData.newPlainText("UPI ID", "9466142854@fam")
        clipboard.setPrimaryClip(clip)
    }

    val appLabel = when (packageName) {
        "com.google.android.apps.nps" -> "Google Pay"
        "com.phonepe.app" -> "PhonePe"
        "net.one97.paytm" -> "Paytm"
        "com.fampay.in" -> "FamApp"
        else -> "Payment App"
    }

    Toast.makeText(context, "UPI ID copied: 9466142854@fam. Opening $appLabel...", Toast.LENGTH_LONG).show()

    if (!packageName.isNullOrBlank()) {
        try {
            val uriBuilder = Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", "9466142854@fam")
                .appendQueryParameter("pn", "Anuj Kumar")
                .appendQueryParameter("cu", "INR")

            if (!amount.isNullOrBlank() && amount.toFloatOrNull() != null) {
                val formattedAmt = String.format(java.util.Locale.US, "%.2f", amount.toFloat())
                uriBuilder.appendQueryParameter("am", formattedAmt)
            }

            val intent = Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "SettingsScreen.openUpiPaymentApp - attempt")
        }

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (e: Exception) {
            ObsidianCrashLogger.logException(e, "SettingsScreen.openUpiPaymentApp - launchIntent")
        }
    }

    try {
        val rawUri = Uri.parse("upi://pay?pa=9466142854@fam&pn=Anuj%20Kumar&cu=INR")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, rawUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    } catch (e: Exception) {
        ObsidianCrashLogger.logException(e, "SettingsScreen.openUpiPaymentApp - fallback")
        Toast.makeText(context, "Could not open $appLabel. UPI ID copied to clipboard!", Toast.LENGTH_LONG).show()
    }
}
