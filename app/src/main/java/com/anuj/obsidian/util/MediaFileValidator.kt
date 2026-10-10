package com.anuj.obsidian.util

import java.io.File
import java.io.RandomAccessFile

data class MediaValidationResult(
    val container: String,
    val mimeType: String,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitDepth: Int = 0,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val isStructurallyValid: Boolean = false,
    val failureReason: String? = null
)

object MediaFileValidator {

    fun validate(file: File?): MediaValidationResult {
        if (file == null || !file.exists()) {
            return MediaValidationResult(
                container = "UNKNOWN",
                mimeType = "application/octet-stream",
                fileSize = 0L,
                isStructurallyValid = false,
                failureReason = "File does not exist"
            )
        }
        val size = file.length()
        if (size < 12L) {
            return MediaValidationResult(
                container = "UNKNOWN",
                mimeType = "application/octet-stream",
                fileSize = size,
                isStructurallyValid = false,
                failureReason = "File size too small (< 12 bytes)"
            )
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(12)
                raf.readFully(magic)

                when {
                    // FLAC: 'fLaC'
                    magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() && magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte() -> {
                        validateFlac(file, raf, size)
                    }
                    // MP4 / M4A: 'ftyp' at offset 4
                    magic[4] == 0x66.toByte() && magic[5] == 0x74.toByte() && magic[6] == 0x79.toByte() && magic[7] == 0x70.toByte() -> {
                        validateM4a(file, raf, size)
                    }
                    // WAV: 'RIFF' ... 'WAVE'
                    magic[0] == 0x52.toByte() && magic[1] == 0x49.toByte() && magic[2] == 0x46.toByte() && magic[3] == 0x46.toByte() &&
                    magic[8] == 0x57.toByte() && magic[9] == 0x41.toByte() && magic[10] == 0x56.toByte() && magic[11] == 0x45.toByte() -> {
                        validateWav(file, raf, size)
                    }
                    // OGG: 'OggS'
                    magic[0] == 0x4F.toByte() && magic[1] == 0x67.toByte() && magic[2] == 0x67.toByte() && magic[3] == 0x53.toByte() -> {
                        validateOgg(file, raf, size)
                    }
                    // MP3: ID3 or 0xFF sync frame
                    (magic[0] == 0x49.toByte() && magic[1] == 0x44.toByte() && magic[2] == 0x33.toByte()) ||
                    (magic[0] == 0xFF.toByte() && (magic[1].toInt() and 0xE0) == 0xE0) -> {
                        validateMp3(file, raf, size)
                    }
                    else -> {
                        MediaValidationResult(
                            container = "UNKNOWN",
                            mimeType = "application/octet-stream",
                            fileSize = size,
                            isStructurallyValid = false,
                            failureReason = "Unrecognized media magic bytes"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            MediaValidationResult(
                container = "UNKNOWN",
                mimeType = "application/octet-stream",
                fileSize = size,
                isStructurallyValid = false,
                failureReason = "Error reading file: ${e.message}"
            )
        }
    }

    private fun validateWav(file: File, raf: RandomAccessFile, fileSize: Long): MediaValidationResult {
        try {
            var pos = 12L
            var audioFormat = 1
            var numChannels = 0
            var sampleRate = 0
            var bitsPerSample = 0
            var hasFmt = false
            var hasData = false
            var dataSize = 0L

            val buf = ByteArray(8)
            while (pos + 8L <= fileSize) {
                raf.seek(pos)
                if (raf.read(buf) < 8) break
                val chunkId = String(buf, 0, 4)
                val chunkSize = (
                    ((buf[7].toInt() and 0xFF) shl 24) or
                    ((buf[6].toInt() and 0xFF) shl 16) or
                    ((buf[5].toInt() and 0xFF) shl 8) or
                    (buf[4].toInt() and 0xFF)
                ).toLong() and 0xFFFFFFFFL

                if (chunkSize < 0 || pos + 8L + chunkSize > fileSize + 1L) {
                    return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "WAV chunk size out of bounds")
                }

                if (chunkId == "fmt " && chunkSize >= 16) {
                    hasFmt = true
                    val fmtBuf = ByteArray(16)
                    raf.readFully(fmtBuf)
                    audioFormat = ((fmtBuf[1].toInt() and 0xFF) shl 8) or (fmtBuf[0].toInt() and 0xFF)
                    numChannels = ((fmtBuf[3].toInt() and 0xFF) shl 8) or (fmtBuf[2].toInt() and 0xFF)
                    sampleRate = (
                        ((fmtBuf[7].toInt() and 0xFF) shl 24) or
                        ((fmtBuf[6].toInt() and 0xFF) shl 16) or
                        ((fmtBuf[5].toInt() and 0xFF) shl 8) or
                        (fmtBuf[4].toInt() and 0xFF)
                    )
                    bitsPerSample = ((fmtBuf[15].toInt() and 0xFF) shl 8) or (fmtBuf[14].toInt() and 0xFF)

                    if (audioFormat != 1 && audioFormat != 3 && audioFormat != 65534) {
                        return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Unsupported WAV audio format ($audioFormat)")
                    }
                    if (numChannels !in 1..8) {
                        return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid channel count ($numChannels)")
                    }
                    if (sampleRate !in 8000..384000) {
                        return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid sample rate ($sampleRate)")
                    }
                    if (bitsPerSample !in listOf(8, 16, 20, 24, 32)) {
                        return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid bit depth ($bitsPerSample)")
                    }
                } else if (chunkId == "data") {
                    hasData = true
                    dataSize = chunkSize
                    break
                }
                pos += 8L + chunkSize + (chunkSize and 1L)
            }

            if (!hasFmt || !hasData) {
                return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Missing fmt or data chunk")
            }
            if (pos + 8L + dataSize > fileSize) {
                return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = "Truncated WAV data chunk")
            }

            return MediaValidationResult(
                container = "WAV",
                mimeType = "audio/wav",
                sampleRate = sampleRate,
                channels = numChannels,
                bitDepth = bitsPerSample,
                fileSize = fileSize,
                isStructurallyValid = true
            )
        } catch (e: Exception) {
            return MediaValidationResult("WAV", "audio/wav", fileSize = fileSize, isStructurallyValid = false, failureReason = e.message)
        }
    }

    private fun validateFlac(file: File, raf: RandomAccessFile, fileSize: Long): MediaValidationResult {
        try {
            if (fileSize < 42L) {
                return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "FLAC file too small (<42 bytes)")
            }

            raf.seek(4) // Skip 'fLaC'
            var isLastBlock = false
            var sampleRate = 0
            var numChannels = 0
            var bitsPerSample = 0
            var totalSamples = 0L
            var foundStreamInfo = false

            while (!isLastBlock && raf.filePointer < fileSize) {
                val headerBuf = ByteArray(4)
                if (raf.read(headerBuf) < 4) break

                val headerInt = ((headerBuf[0].toInt() and 0xFF) shl 24) or
                        ((headerBuf[1].toInt() and 0xFF) shl 16) or
                        ((headerBuf[2].toInt() and 0xFF) shl 8) or
                        (headerBuf[3].toInt() and 0xFF)

                isLastBlock = (headerInt and 0x80000000.toInt()) != 0
                val blockType = (headerInt ushr 24) and 0x7F
                val blockLength = headerInt and 0x00FFFFFF

                if (blockLength < 0 || raf.filePointer + blockLength > fileSize) {
                    return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid FLAC metadata block length ($blockLength)")
                }

                if (blockType == 0) { // STREAMINFO
                    if (blockLength < 34) {
                        return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid STREAMINFO length ($blockLength)")
                    }
                    val streamInfo = ByteArray(34)
                    raf.readFully(streamInfo)
                    foundStreamInfo = true

                    // Extract sample rate (20 bits), channels (3 bits), bits per sample (5 bits), total samples (36 bits)
                    val b10 = streamInfo[10].toInt() and 0xFF
                    val b11 = streamInfo[11].toInt() and 0xFF
                    val b12 = streamInfo[12].toInt() and 0xFF
                    val b13 = streamInfo[13].toInt() and 0xFF
                    val b14 = streamInfo[14].toInt() and 0xFF
                    val b15 = streamInfo[15].toInt() and 0xFF
                    val b16 = streamInfo[16].toInt() and 0xFF
                    val b17 = streamInfo[17].toInt() and 0xFF

                    sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
                    numChannels = ((b12 ushr 1) and 0x07) + 1
                    bitsPerSample = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1
                    totalSamples = (((b13 and 0x0F).toLong() shl 32) or
                            (b14.toLong() shl 24) or
                            (b15.toLong() shl 16) or
                            (b16.toLong() shl 8) or
                            b17.toLong())

                    if (sampleRate !in 1..384000) {
                        return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid FLAC sample rate ($sampleRate)")
                    }
                    if (numChannels !in 1..8) {
                        return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid FLAC channel count ($numChannels)")
                    }
                    if (bitsPerSample !in 4..32) {
                        return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid FLAC bit depth ($bitsPerSample)")
                    }

                    // Seek past rest of STREAMINFO if > 34
                    if (blockLength > 34) {
                        raf.seek(raf.filePointer + (blockLength - 34))
                    }
                } else {
                    raf.seek(raf.filePointer + blockLength)
                }
            }

            if (!foundStreamInfo) {
                return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = "Missing STREAMINFO block in FLAC header")
            }

            return MediaValidationResult(
                container = "FLAC",
                mimeType = "audio/flac",
                sampleRate = sampleRate,
                channels = numChannels,
                bitDepth = bitsPerSample,
                fileSize = fileSize,
                isStructurallyValid = true
            )
        } catch (e: Exception) {
            return MediaValidationResult("FLAC", "audio/flac", fileSize = fileSize, isStructurallyValid = false, failureReason = e.message)
        }
    }

    private fun validateM4a(file: File, raf: RandomAccessFile, fileSize: Long): MediaValidationResult {
        try {
            if (fileSize < 32L) {
                return MediaValidationResult("M4A", "audio/mp4", fileSize = fileSize, isStructurallyValid = false, failureReason = "M4A file too small (<32 bytes)")
            }

            var pos = 0L
            var foundFtyp = false
            var foundMoovOrMdat = false

            while (pos + 8L <= fileSize) {
                raf.seek(pos)
                val boxBuf = ByteArray(8)
                if (raf.read(boxBuf) < 8) break

                var boxSize = (
                    ((boxBuf[0].toInt() and 0xFF).toLong() shl 24) or
                    ((boxBuf[1].toInt() and 0xFF).toLong() shl 16) or
                    ((boxBuf[2].toInt() and 0xFF).toLong() shl 8) or
                    (boxBuf[3].toInt() and 0xFF).toLong()
                )
                val boxType = String(boxBuf, 4, 4)

                var headerSize = 8L
                if (boxSize == 1L) {
                    val extBuf = ByteArray(8)
                    if (raf.read(extBuf) < 8) break
                    boxSize = 0L
                    for (b in extBuf) {
                        boxSize = (boxSize shl 8) or (b.toInt() and 0xFF).toLong()
                    }
                    headerSize = 16L
                } else if (boxSize == 0L) {
                    boxSize = fileSize - pos
                }

                if (boxSize < headerSize || pos + boxSize > fileSize) {
                    return MediaValidationResult("M4A", "audio/mp4", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid ISO-BMFF box size for box '$boxType'")
                }

                if (boxType == "ftyp") {
                    foundFtyp = true
                } else if (boxType == "moov" || boxType == "mdat") {
                    foundMoovOrMdat = true
                }

                pos += boxSize
            }

            if (!foundFtyp || !foundMoovOrMdat) {
                return MediaValidationResult("M4A", "audio/mp4", fileSize = fileSize, isStructurallyValid = false, failureReason = "Missing ftyp, moov, or mdat box in ISO-BMFF container")
            }

            return MediaValidationResult(
                container = "M4A",
                mimeType = "audio/mp4",
                fileSize = fileSize,
                isStructurallyValid = true
            )
        } catch (e: Exception) {
            return MediaValidationResult("M4A", "audio/mp4", fileSize = fileSize, isStructurallyValid = false, failureReason = e.message)
        }
    }

    private fun validateOgg(file: File, raf: RandomAccessFile, fileSize: Long): MediaValidationResult {
        try {
            if (fileSize < 27L) {
                return MediaValidationResult("OGG", "audio/ogg", fileSize = fileSize, isStructurallyValid = false, failureReason = "OGG file too small (<27 bytes)")
            }

            raf.seek(0)
            val header = ByteArray(27)
            raf.readFully(header)

            if (header[0] != 0x4F.toByte() || header[1] != 0x67.toByte() || header[2] != 0x67.toByte() || header[3] != 0x53.toByte()) {
                return MediaValidationResult("OGG", "audio/ogg", fileSize = fileSize, isStructurallyValid = false, failureReason = "Missing OggS magic header")
            }

            val version = header[4].toInt() and 0xFF
            if (version != 0) {
                return MediaValidationResult("OGG", "audio/ogg", fileSize = fileSize, isStructurallyValid = false, failureReason = "Unsupported Ogg version ($version)")
            }

            val pageSegments = header[26].toInt() and 0xFF
            val segmentTable = ByteArray(pageSegments)
            raf.readFully(segmentTable)

            var bodySize = 0L
            for (s in segmentTable) {
                bodySize += (s.toInt() and 0xFF)
            }

            val totalPageSize = 27L + pageSegments + bodySize
            if (totalPageSize > fileSize) {
                return MediaValidationResult("OGG", "audio/ogg", fileSize = fileSize, isStructurallyValid = false, failureReason = "First Ogg page exceeds file bounds")
            }

            return MediaValidationResult(
                container = "OGG",
                mimeType = "audio/ogg",
                fileSize = fileSize,
                isStructurallyValid = true
            )
        } catch (e: Exception) {
            return MediaValidationResult("OGG", "audio/ogg", fileSize = fileSize, isStructurallyValid = false, failureReason = e.message)
        }
    }

    private fun validateMp3(file: File, raf: RandomAccessFile, fileSize: Long): MediaValidationResult {
        try {
            if (fileSize < 32L) {
                return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = "MP3 file too small (<32 bytes)")
            }

            raf.seek(0)
            val magic = ByteArray(10)
            raf.readFully(magic)

            var audioStartOffset = 0L
            if (magic[0] == 0x49.toByte() && magic[1] == 0x44.toByte() && magic[2] == 0x33.toByte()) { // ID3
                val tagSize = ((magic[6].toInt() and 0x7F) shl 21) or
                        ((magic[7].toInt() and 0x7F) shl 14) or
                        ((magic[8].toInt() and 0x7F) shl 7) or
                        (magic[9].toInt() and 0x7F)
                audioStartOffset = 10L + tagSize
            }

            if (audioStartOffset >= fileSize - 4) {
                return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = "ID3 tag covers entire MP3 file")
            }

            raf.seek(audioStartOffset)
            val frameHeader = ByteArray(4)
            if (raf.read(frameHeader) < 4) {
                return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = "Truncated MP3 audio frame header")
            }

            // Sync word check: 11 bits set to 1 (0xFF and top 3 bits of byte 1)
            val b0 = frameHeader[0].toInt() and 0xFF
            val b1 = frameHeader[1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) {
                return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = "Invalid MP3 frame sync word")
            }

            val layer = (b1 ushr 1) and 0x03
            val bitrateIdx = (frameHeader[2].toInt() and 0xF0) ushr 4
            val sampleRateIdx = (frameHeader[2].toInt() and 0x0C) ushr 2

            if (layer == 0 || bitrateIdx == 15 || sampleRateIdx == 3) {
                return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = "Malformed MP3 frame header parameters")
            }

            return MediaValidationResult(
                container = "MP3",
                mimeType = "audio/mpeg",
                fileSize = fileSize,
                isStructurallyValid = true
            )
        } catch (e: Exception) {
            return MediaValidationResult("MP3", "audio/mpeg", fileSize = fileSize, isStructurallyValid = false, failureReason = e.message)
        }
    }
}
