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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anuj.obsidian.data.remote.nas.NasServerConfig
import com.anuj.obsidian.data.remote.nas.NetworkMusicServerManager
import com.anuj.obsidian.data.remote.nas.ServerType
import com.anuj.obsidian.data.remote.nas.SubsonicClient
import com.anuj.obsidian.data.remote.nas.WebDavClient
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.ObsidianTheme
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.UUID

fun LazyListScope.settingsNetworkNasSection() {
    item {
        NetworkNasContent()
    }
}

@Composable
fun NetworkNasContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers by NetworkMusicServerManager.servers.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ServerType.SUBSONIC) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "NAS Servers",
                        tint = ObsidianTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Network & NAS Audio Storage",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Stream lossless audio directly from home servers",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        showAddDialog = !showAddDialog
                        testResult = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAddDialog) DarkSurface else ObsidianTheme.colors.primary,
                        contentColor = if (showAddDialog) TextPrimary else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (showAddDialog) Icons.Default.CloudQueue else Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAddDialog) "Cancel" else "Add Server", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (showAddDialog) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add Remote Music Server",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Server Type Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ServerType.values().forEach { st ->
                                val isSel = selectedType == st
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedType = st },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) ObsidianTheme.colors.primary.copy(alpha = 0.2f) else DarkSurfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) ObsidianTheme.colors.primary else DarkSurfaceBorder
                                    )
                                ) {
                                    Text(
                                        text = when (st) {
                                            ServerType.SUBSONIC -> "Subsonic"
                                            ServerType.WEBDAV -> "WebDAV"
                                            ServerType.DIRECT_HTTP -> "HTTP"
                                        },
                                        color = if (isSel) ObsidianTheme.colors.primary else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text("Display Name (e.g. Home Navidrome)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ObsidianTheme.colors.primary,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL (http://192.168.1.100:4533)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ObsidianTheme.colors.primary,
                                unfocusedBorderColor = DarkSurfaceBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        if (selectedType != ServerType.DIRECT_HTTP) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ObsidianTheme.colors.primary,
                                        unfocusedBorderColor = DarkSurfaceBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ObsidianTheme.colors.primary,
                                        unfocusedBorderColor = DarkSurfaceBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }

                        if (testResult != null) {
                            Text(
                                text = testResult ?: "",
                                color = if (testResult?.contains("success", ignoreCase = true) == true || testResult?.contains("Connected", ignoreCase = true) == true) ObsidianTheme.colors.primary else Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (serverUrl.isBlank()) {
                                        testResult = "Please enter server URL"
                                        return@Button
                                    }
                                    isTesting = true
                                    testResult = "Testing connection..."
                                    scope.launch {
                                        try {
                                            when (selectedType) {
                                                ServerType.SUBSONIC -> {
                                                    val client = SubsonicClient(serverUrl, username, password)
                                                    val res = client.ping()
                                                    testResult = if (res.isSuccess) res.getOrNull() else "Failed: ${res.exceptionOrNull()?.message}"
                                                }
                                                ServerType.WEBDAV -> {
                                                    val client = WebDavClient(serverUrl, username, password)
                                                    val res = client.testConnection()
                                                    testResult = if (res.isSuccess) res.getOrNull() else "Failed: ${res.exceptionOrNull()?.message}"
                                                }
                                                ServerType.DIRECT_HTTP -> {
                                                    testResult = "HTTP endpoint ready"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            testResult = "Error: ${e.message}"
                                        } finally {
                                            isTesting = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceCard,
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isTesting) "Testing..." else "Test Connection", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (serverUrl.isBlank()) {
                                        Toast.makeText(context, "Server URL cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val name = serverName.ifBlank {
                                        "${selectedType.name} Server"
                                    }
                                    val config = NasServerConfig(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        serverType = selectedType,
                                        serverUrl = serverUrl,
                                        username = username,
                                        tokenOrPassword = password,
                                        isEnabled = true,
                                        lastConnectedTimeMs = System.currentTimeMillis()
                                    )
                                    NetworkMusicServerManager.saveServer(context, config)
                                    showAddDialog = false
                                    serverName = ""
                                    serverUrl = ""
                                    username = ""
                                    password = ""
                                    testResult = null
                                    Toast.makeText(context, "Added server: $name", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianTheme.colors.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Server", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (servers.isEmpty()) {
                Text(
                    text = "No remote NAS or streaming servers configured. Add a Subsonic / Navidrome or WebDAV server to stream your remote library.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    servers.forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = "Server",
                                        tint = ObsidianTheme.colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = s.name,
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${s.serverType.displayName} • ${s.serverUrl}",
                                            color = TextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        NetworkMusicServerManager.removeServer(context, s.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
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
