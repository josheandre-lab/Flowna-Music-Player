package com.flowna.musicplayer.util

object TextNormalizer {

    private val mojibakeMarkers = listOf("Ã", "Ä", "Å", "Ð", "Þ", "�")

    fun normalizeHumanText(value: String?): String? {
        val trimmed = value
            ?.replace('\uFEFF', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val repaired = repairUtf8Mojibake(trimmed)
        return repaired.replace(Regex("\\s+"), " ").trim()
    }

    private fun repairUtf8Mojibake(text: String): String {
        if (mojibakeScore(text) == 0) return text

        val repaired = runCatching {
            String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(text)

        return if (mojibakeScore(repaired) < mojibakeScore(text)) {
            repaired
        } else {
            text
        }
    }

    private fun mojibakeScore(text: String): Int {
        return mojibakeMarkers.sumOf { marker -> text.windowed(marker.length, 1).count { it == marker } }
    }
}
