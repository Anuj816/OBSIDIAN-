package com.anuj.obsidian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anuj.obsidian.audio.usb.UsbDacOutputManager
import com.anuj.obsidian.ui.theme.ObsidianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDacInspectorDialog(
    onDismiss: () -> Unit
) {
    val appColors = ObsidianTheme.colors
    val dacInfo by UsbDacOutputManager.dacInfo.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF090B0E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (dacInfo.isConnected) Color(0xFFF5A623) else appColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HARDWARE AUDIO SINK",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Hardware Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (dacInfo.isConnected) Color(0xFF261D12) else Color(0xFF141822),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (dacInfo.isConnected) Color(0xFFF5A623).copy(alpha = 0.6f) else appColors.surfaceBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dacInfo.isConnected) "USB DIRECT EXCLUSIVE" else "INTERNAL AUDIO ENGINE",
                            color = if (dacInfo.isConnected) Color(0xFFF5A623) else appColors.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.0.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (dacInfo.isConnected) Color(0xFFF5A623).copy(alpha = 0.25f) else Color(0xFF1DB954).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (dacInfo.isConnected) "BIT-PERFECT" else "SYSTEM MIX",
                                color = if (dacInfo.isConnected) Color(0xFFF5A623) else Color(0xFF1DB954),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = dacInfo.deviceName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dacInfo.hardwareRoutingStatus,
                        color = Color(0xFF9EABB8),
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware Specs Matrix
            Text(
                text = "STREAM & HARDWARE PROPERTIES",
                color = appColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SpecRow(label = "Active Stream Rate", value = dacInfo.currentStreamSampleRate, isHighlight = true)
                SpecRow(label = "Active Bit Depth", value = dacInfo.currentStreamBitDepth, isHighlight = true)
                SpecRow(label = "Max Hardware Rate", value = "${dacInfo.maxSampleRate / 1000} kHz / 32-bit")
                SpecRow(label = "Direct DSD / DoP", value = if (dacInfo.isDoPSupported) "Supported (DoP v1.1)" else "PCM Only")
                SpecRow(label = "Supported Rates", value = dacInfo.sampleRates.joinToString(", ") { "${it / 1000}k" })
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, isHighlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF12151D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color(0xFF9EABB8), fontSize = 12.5.sp)
            Text(
                text = value,
                color = if (isHighlight) Color(0xFF00E5FF) else Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
