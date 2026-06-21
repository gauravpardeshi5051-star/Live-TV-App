package com.example.parser

import android.util.Log
import com.example.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object M3uParser {
    private const val TAG = "M3uParser"

    /**
     * Parses M3U playlist content into a list of Channels.
     */
    fun parse(m3uContent: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val lines = m3uContent.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("#EXTINF:")) {
                    // Try to parse channel metadata
                    var logoUrl = ""
                    var groupTitle = "General"
                    var name = ""

                    // Extract logo URL : tvg-logo="..." or tvg-logo='...'
                    logoUrl = extractAttribute(line, "tvg-logo") 
                        ?: extractAttribute(line, "logo")
                        ?: ""

                    // Extract group / category
                    groupTitle = extractAttribute(line, "group-title") 
                        ?: extractAttribute(line, "group") 
                        ?: "General"

                    // Channel Name sits after the final comma on the #EXTINF line
                    val commaIndex = line.lastIndexOf(',')
                    name = if (commaIndex != -1 && commaIndex + 1 < line.length) {
                        line.substring(commaIndex + 1).trim()
                    } else {
                        // Try fallback to tvg-name or default
                        extractAttribute(line, "tvg-name") ?: "Unnamed Channel"
                    }

                    // Advance the index to find the stream URL
                    i++
                    while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith("#"))) {
                        // Skip any empty lines or nested comment details
                        i++
                    }

                    if (i < lines.size) {
                        val streamUrl = lines[i]
                        // Simple validation of URL structure
                        if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://") || streamUrl.contains("rtmp://") || streamUrl.contains("rtsp://")) {
                            channels.add(
                                Channel(
                                    id = streamUrl.hashCode().toString(),
                                    name = name,
                                    streamUrl = streamUrl,
                                    logoUrl = logoUrl,
                                    groupTitle = groupTitle
                                )
                            )
                        }
                    }
                }
                i++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U list: ${e.message}", e)
        }
        return channels
    }

    /**
     * Helper to extract key-value attributes from the #EXTINF line.
     */
    private fun extractAttribute(line: String, attribute: String): String? {
        val patterns = listOf(
            "$attribute=\"([^\"]*)\"",
            "$attribute='([^']*)'",
            "$attribute=([^\\s,]*)"
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(line)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Fetch the M3U text over OkHttp and parse it into channels.
     */
    suspend fun fetchFromUrl(client: OkHttpClient, url: String): List<Channel> = withContext(Dispatchers.IO) {
        if (url.contains("famelack.com/tv/") || url.contains("famelack.com/radio/")) {
            val resolved = resolveFamelackUrl(client, url)
            if (resolved.isNotEmpty()) {
                return@withContext resolved
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Network request failed: HTTP ${response.code} ${response.message}")
                }
                val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                return@withContext parse(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and parse M3U from: $url", e)
            throw e
        }
    }

    private fun resolveFamelackUrl(client: OkHttpClient, url: String): List<Channel> {
        try {
            // Parses Famelack URL like: https://famelack.com/tv/in/Z3ikslJzUV8EMT
            val cleanedUrl = url.trim()
            val uriParts = cleanedUrl.split("/").filter { it.isNotEmpty() }
            val modeIndex = uriParts.indexOfFirst { it == "tv" || it == "radio" }
            if (modeIndex == -1 || modeIndex + 2 >= uriParts.size) {
                return emptyList()
            }
            val mode = uriParts[modeIndex] // "tv" or "radio"
            val country = uriParts[modeIndex + 1].lowercase() // e.g., "in"
            val targetId = uriParts[modeIndex + 2] // e.g., "Z3ikslJzUV8EMT"

            val jsonUrl = "https://raw.githubusercontent.com/famelack/famelack-data/main/$mode/compressed/countries/$country.json"
            Log.d(TAG, "Fetching Famelack stream database from $jsonUrl")

            val request = Request.Builder()
                .url(jsonUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download Famelack stream database: HTTP ${response.code}")
                    return emptyList()
                }

                val bytes = response.body?.bytes() ?: return emptyList()
                val jsonText = if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                    // decompress GZIP stream
                    java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
                } else {
                    String(bytes)
                }

                // Locate the target item containing targetId
                val idIndex = jsonText.indexOf("\"nanoid\":\"$targetId\"")
                if (idIndex != -1) {
                    val startIdx = jsonText.lastIndexOf('{', idIndex)
                    val endIdx = jsonText.indexOf('}', idIndex)
                    if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                        val objectStr = jsonText.substring(startIdx, endIdx + 1)
                        val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(objectStr)
                        val name = nameMatch?.groupValues?.get(1) ?: "Famelack Channel"

                        val streamUrlsMatch = Regex("\"stream_urls\"\\s*:\\s*\\[([^\\]]+)\\]").find(objectStr)
                        if (streamUrlsMatch != null) {
                            val urlsRaw = streamUrlsMatch.groupValues[1]
                            val streamUrls = urlsRaw.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotEmpty() }
                            if (streamUrls.isNotEmpty()) {
                                val logoUrl = if (name.contains("Goldmines Movies", ignoreCase = true)) {
                                    "https://upload.wikimedia.org/wikipedia/commons/d/df/Star_Sports_logo.svg"
                                } else {
                                    "https://upload.wikimedia.org/wikipedia/commons/4/4b/Zee_Cinema_logo.svg"
                                }
                                Log.i(TAG, "Successfully resolved Famelack ID $targetId to stream URL ${streamUrls[0]}")
                                return listOf(
                                    Channel(
                                        id = "famelack_$targetId",
                                        name = name,
                                        streamUrl = streamUrls[0],
                                        logoUrl = logoUrl,
                                        groupTitle = if (mode == "tv") "Entertainment" else "Radio"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dynamically resolving Famelack stream URL", e)
        }
        return emptyList()
    }
}
