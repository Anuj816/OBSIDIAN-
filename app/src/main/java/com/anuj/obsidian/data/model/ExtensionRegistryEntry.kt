package com.anuj.obsidian.data.model

data class ExtensionRegistryEntry(
    val id: String,
    val name: String,
    val displayName: String,
    val version: String,
    val description: String,
    val downloadUrl: String,
    val sha256: String = "",
    val iconUrl: String? = null,
    val category: String = "integration",
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val updatedAt: String = "",
    val minAppVersion: String = ""
)
