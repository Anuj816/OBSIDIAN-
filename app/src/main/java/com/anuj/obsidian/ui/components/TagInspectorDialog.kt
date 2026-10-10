package com.anuj.obsidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anuj.obsidian.data.local.DetailedCodecInfo
import com.anuj.obsidian.data.local.JaudiotaggerHelper
import com.anuj.obsidian.data.local.ModernAudioTagger
import com.anuj.obsidian.data.model.DownloadedTrack
import com.anuj.obsidian.ui.theme.DarkSurface
import com.anuj.obsidian.ui.theme.DarkSurfaceBorder
import com.anuj.obsidian.ui.theme.DarkSurfaceCard
import com.anuj.obsidian.ui.theme.FlacHiResGold
import com.anuj.obsidian.ui.theme.FlacLosslessCyan
import com.anuj.obsidian.ui.theme.TextMuted
import com.anuj.obsidian.ui.theme.TextPrimary
import com.anuj.obsidian.ui.theme.TextSecondary
import com.anuj.obsidian.ui.theme.ObsidianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TagInspectorDialog(
    track: DownloadedTrack?,
    onDismiss: () -> Unit,
    onSaveTags: (Long, String, String, String, String, String, Int, String) -> Unit
) {
    if (track == null) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre) }
    var releaseDate by remember { mutableStateOf(track.releaseDate) }
    var trackNumberStr by remember { mutableStateOf(track.trackNumber.toString()) }
    var lyricsText by remember { mutableStateOf(track.lyrics) }

    var codecDetails by remember { mutableStateOf<DetailedCodecInfo?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var isEmbeddingArt by remember { mutableStateOf(false) }
    var artSuccessMessage by remember { mutableStateOf("") }
    var isFingerprinting by remember { mutableStateOf(false) }
    var fingerprintSuccessMsg by remember { mutableStateOf("") }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isEmbeddingArt = true
            coroutineScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                        input?.use { stream ->
                            val byteBuffer = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(16384)
                            var read: Int
                            var totalRead = 0
                            while (stream.read(buffer).also { read = it } != -1) {
                                byteBuffer.write(buffer, 0, read)
                                totalRead += read
                                if (totalRead > 12 * 1024 * 1024) {
                                    throw java.io.IOException("Selected file exceeds 12MB limit")
                                }
                            }
                            byteBuffer.toByteArray()
                        }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val success = withContext(Dispatchers.IO) {
                            ModernAudioTagger.embedArtworkBytes(
                                context = context,
                                filePathOrUri = track.localFilePath,
                                artworkBytes = bytes,
                                mimeType = mimeType
                            )
                        }
                        if (success) {
                            val refreshed = withContext(Dispatchers.IO) {
                                JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                            }
                            codecDetails = refreshed
                            artSuccessMessage = "Hi-Res Cover Embedded! (${bytes.size / 1024} KB)"
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    com.anuj.obsidian.ObsidianCrashLogger.logException(e, "TagInspectorDialog.photoPickerLauncher")
                    android.util.Log.e("TagInspector", "Failed to embed image: ${e.message}")
                } finally {
                    isEmbeddingArt = false
                }
            }
        }
    }

    var isSavingTags by remember { mutableStateOf(false) }
    var saveFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var saveIsError by remember { mutableStateOf(false) }
    var showCopyButton by remember { mutableStateOf(false) }

    LaunchedEffect(track) {
        withContext(Dispatchers.IO) {
            val details = JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
            withContext(Dispatchers.Main) {
                codecDetails = details
                if (details.title.isNotBlank()) title = details.title
                if (details.artist.isNotBlank()) artist = details.artist
                if (details.album.isNotBlank()) album = details.album
                if (details.genre.isNotBlank()) genre = details.genre
                if (details.year.isNotBlank()) releaseDate = details.year
                if (details.trackNumber.isNotBlank()) trackNumberStr = details.trackNumber
                isLoadingDetails = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "JAUDIOTAGGER INSPECTOR",
                            color = ObsidianTheme.colors.primary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Lossless FLAC & Vorbis Comments",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tag_inspector_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Acoustic Fingerprint Quick Auto-Identify Action Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FlacLosslessCyan.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FlacLosslessCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = FlacLosslessCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "ACOUSTIC FINGERPRINT",
                                        color = FlacLosslessCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "AcoustID & MusicBrainz Spectrum",
                                        color = TextSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    isFingerprinting = true
                                    fingerprintSuccessMsg = ""
                                    coroutineScope.launch {
                                        try {
                                            val identified = withContext(Dispatchers.IO) {
                                                com.anuj.obsidian.audio.fingerprint.AudioIdentificationService.identifyTrackFromFile(
                                                    context = context,
                                                    filePathOrUri = track.localFilePath,
                                                    hintTitle = title,
                                                    hintArtist = artist,
                                                    durationSecHint = track.durationSeconds
                                                )
                                            }
                                            if (identified != null) {
                                                if (identified.title.isNotBlank()) title = identified.title
                                                if (identified.artist.isNotBlank()) artist = identified.artist
                                                if (identified.album.isNotBlank()) album = identified.album
                                                if (identified.genre.isNotBlank() && identified.genre != "Audio") genre = identified.genre
                                                if (identified.releaseYear.isNotBlank()) releaseDate = identified.releaseYear
                                                if (identified.trackNumber > 0) trackNumberStr = identified.trackNumber.toString()
                                                if (identified.lyrics.isNotBlank()) lyricsText = identified.lyrics

                                                if (identified.albumArtUrl.isNotBlank()) {
                                                    // Auto embed high res artwork
                                                    withContext(Dispatchers.IO) {
                                                        try {
                                                            val req = okhttp3.Request.Builder().url(identified.albumArtUrl).build()
                                                            val resp = com.anuj.obsidian.data.remote.MusicApiService.client.newCall(req).execute()
                                                            val bytes = resp.body?.bytes()
                                                            if (bytes != null && bytes.isNotEmpty()) {
                                                                ModernAudioTagger.embedArtworkBytes(
                                                                    context = context,
                                                                    filePathOrUri = track.localFilePath,
                                                                    artworkBytes = bytes,
                                                                    mimeType = "image/jpeg"
                                                                )
                                                                val refreshed = JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                                                                codecDetails = refreshed
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }

                                                fingerprintSuccessMsg = "Matched: ${identified.title} by ${identified.artist} (${identified.serviceSource})"
                                            } else {
                                                fingerprintSuccessMsg = "No exact acoustic fingerprint found online"
                                            }
                                        } catch (e: Exception) {
                                            fingerprintSuccessMsg = "Identification error: ${e.message}"
                                        } finally {
                                            isFingerprinting = false
                                        }
                                    }
                                },
                                enabled = !isFingerprinting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FlacLosslessCyan,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp).testTag("fingerprint_identify_btn")
                            ) {
                                if (isFingerprinting) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scanning...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Auto-Identify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (fingerprintSuccessMsg.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = fingerprintSuccessMsg,
                                color = if (fingerprintSuccessMsg.startsWith("Matched")) FlacHiResGold else Color(0xFFEF9A9A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Embedded Album Art & Codec Specs Card
                Surface(
                    color = DarkSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoadingDetails) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ObsidianTheme.colors.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing FLAC Bitstream via JAudioTagger...", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        val details = codecDetails ?: DetailedCodecInfo()
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (artSuccessMessage.isNotBlank()) {
                                Surface(
                                    color = ObsidianTheme.colors.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianTheme.colors.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = artSuccessMessage,
                                        color = ObsidianTheme.colors.primary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Embedded Artwork Box with replace action
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurface)
                                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (details.artworkBitmap != null) {
                                            Image(
                                                bitmap = details.artworkBitmap.asImageBitmap(),
                                                contentDescription = "Embedded Artwork",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Album,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("No Cover", color = TextMuted, fontSize = 9.sp)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { photoPickerLauncher.launch("image/*") },
                                        enabled = !isEmbeddingArt,
                                        modifier = Modifier.testTag("tag_embed_art_btn")
                                    ) {
                                        Text(
                                            text = if (isEmbeddingArt) "Embedding..." else "Embed Art",
                                            color = ObsidianTheme.colors.primary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Detailed Audio Codec & Header Spec List
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = FlacLosslessCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = details.formatName.ifBlank { track.format + " Lossless" },
                                            color = FlacLosslessCyan,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val rawSampleRate = details.sampleRateFormatted.ifBlank { track.sampleRate }
                                    val sampleRateText = if (rawSampleRate.isBlank() || rawSampleRate.equals("UNKNOWN", ignoreCase = true)) "44.1 kHz" else rawSampleRate
                                    val rawBitDepth = details.bitDepthFormatted.ifBlank { track.bitDepth }
                                    val bitDepthText = if (rawBitDepth.isBlank() || rawBitDepth.equals("UNKNOWN", ignoreCase = true)) "16-bit" else rawBitDepth
                                    val bitrateText = if (details.bitrateKbps > 0) "${details.bitrateKbps} kbps" else if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "1411 kbps"

                                    Text(
                                        text = "$bitDepthText / $sampleRateText ($bitrateText)",
                                        color = FlacHiResGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = "Channels: ${details.channelMode.ifBlank { "2.0 Stereo" }}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )

                                    if (details.artworkDimensions.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val kb = (details.artworkSizeBytes / 1024L)
                                        Text(
                                            text = "Cover: ${details.artworkDimensions} (${kb} KB ${details.artworkMimeType.substringAfter("/")})",
                                            color = TextMuted,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }

                            // Extra Codec Signatures (MD5, Encoder)
                            if (details.flacMd5Signature.isNotBlank() || details.composer.isNotBlank() || details.encoder.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = DarkSurface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        if (details.flacMd5Signature.isNotBlank()) {
                                            Text(
                                                text = "FLAC MD5: ${details.flacMd5Signature}",
                                                color = TextMuted,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        if (details.composer.isNotBlank()) {
                                            Text(
                                                text = "Composer: ${details.composer}",
                                                color = TextSecondary,
                                                fontSize = 10.5.sp
                                            )
                                        }
                                        if (details.encoder.isNotBlank()) {
                                            Text(
                                                text = "Encoder: ${details.encoder}",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Vorbis Comment Fields
                TagTextField(label = "Title", value = title, onValueChange = { title = it }, testTag = "tag_input_title")
                Spacer(modifier = Modifier.height(8.dp))
                TagTextField(label = "Artist", value = artist, onValueChange = { artist = it }, testTag = "tag_input_artist")
                Spacer(modifier = Modifier.height(8.dp))
                TagTextField(label = "Album", value = album, onValueChange = { album = it }, testTag = "tag_input_album")
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TagTextField(
                        label = "Genre",
                        value = genre,
                        onValueChange = { genre = it },
                        modifier = Modifier.weight(1f),
                        testTag = "tag_input_genre"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TagTextField(
                        label = "Track #",
                        value = trackNumberStr,
                        onValueChange = { trackNumberStr = it },
                        modifier = Modifier.weight(0.6f),
                        testTag = "tag_input_track_num"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TagTextField(label = "Release Year / Date", value = releaseDate, onValueChange = { releaseDate = it }, testTag = "tag_input_year")

                Spacer(modifier = Modifier.height(8.dp))
                TagTextField(
                    label = "Lyrics / Embedded LRC",
                    value = lyricsText,
                    onValueChange = { lyricsText = it },
                    singleLine = false,
                    maxLines = 4,
                    testTag = "tag_input_lyrics"
                )

                // Feedback Banner
                AnimatedVisibility(visible = saveFeedbackMessage != null) {
                    saveFeedbackMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (saveIsError) Color(0xFF3E1E1E) else Color(0xFF1E3E26),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (saveIsError) Color(0xFFFF5252) else Color(0xFF4CAF50)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (saveIsError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (saveIsError) Color(0xFFFF5252) else Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tag_inspector_cancel")
                    ) {
                        Text("Close", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (showCopyButton) {
                        Button(
                            onClick = {
                                val trackNum = trackNumberStr.toIntOrNull() ?: 1
                                isSavingTags = true
                                saveFeedbackMessage = "Creating new tagged audio file..."
                                saveIsError = false
                                coroutineScope.launch {
                                    val newPath = withContext(Dispatchers.IO) {
                                        ModernAudioTagger.createTaggedCopy(
                                            context = context,
                                            sourceFilePath = track.localFilePath,
                                            title = title,
                                            artist = artist,
                                            album = album,
                                            genre = genre,
                                            year = releaseDate,
                                            lyrics = lyricsText,
                                            artworkUrl = track.albumArtUrl,
                                            trackNumber = trackNum
                                        )
                                    }
                                    isSavingTags = false
                                    if (newPath != null) {
                                        saveFeedbackMessage = "New tagged audio saved to Music folder!"
                                        saveIsError = false
                                        showCopyButton = false
                                        onSaveTags(track.id, title, artist, album, genre, releaseDate, trackNum, lyricsText)
                                    } else {
                                        saveFeedbackMessage = "Failed to create tagged audio copy."
                                        saveIsError = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A75D3),
                                contentColor = Color.White
                            ),
                            enabled = !isSavingTags,
                            modifier = Modifier.testTag("tag_inspector_save_copy")
                        ) {
                            Text("Save Tagged Copy", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            val trackNum = trackNumberStr.toIntOrNull() ?: 1
                            isSavingTags = true
                            saveFeedbackMessage = "Writing Vorbis / ID3 tags & verifying..."
                            saveIsError = false
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    ModernAudioTagger.embedAllTags(
                                        context = context,
                                        sourceFilePath = track.localFilePath,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        genre = genre,
                                        year = releaseDate,
                                        lyrics = lyricsText,
                                        artworkUrl = track.albumArtUrl,
                                        trackNumber = trackNum
                                    )
                                }
                                isSavingTags = false
                                if (success) {
                                    saveFeedbackMessage = "Tags verified & written directly into file!"
                                    saveIsError = false
                                    showCopyButton = false
                                    val refreshed = withContext(Dispatchers.IO) {
                                        JaudiotaggerHelper.readDetailedMetadata(context, track.localFilePath)
                                    }
                                    codecDetails = refreshed
                                    onSaveTags(track.id, title, artist, album, genre, releaseDate, trackNum, lyricsText)
                                } else {
                                    saveFeedbackMessage = "Direct overwrite blocked by Android storage permissions. Tap 'Save Tagged Copy' to export a tagged version."
                                    saveIsError = true
                                    showCopyButton = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianTheme.colors.primary,
                            contentColor = Color.Black
                        ),
                        enabled = !isSavingTags,
                        modifier = Modifier.testTag("tag_inspector_save")
                    ) {
                        if (isSavingTags) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saving...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Tags", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    testTag: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.5.sp) },
        singleLine = singleLine,
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ObsidianTheme.colors.primary,
            unfocusedBorderColor = DarkSurfaceBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = ObsidianTheme.colors.primary,
            unfocusedLabelColor = TextSecondary,
            focusedContainerColor = DarkSurfaceCard,
            unfocusedContainerColor = DarkSurfaceCard
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
