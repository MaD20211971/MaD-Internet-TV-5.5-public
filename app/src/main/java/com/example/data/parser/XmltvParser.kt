package com.example.data.parser

import android.util.Log
import android.util.Xml
import com.example.data.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.GZIPInputStream

data class XmltvChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String? = null
)

object XmltvParser {

    private const val TAG = "XmltvParser"

    private val DATE_FORMAT_WITH_TZ = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val DATE_FORMAT_BERLIN = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Europe/Berlin")
    }
    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val ISO_FORMAT_TZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)

    fun parse(
        inputStream: InputStream,
        isGzip: Boolean = false,
        sourceUrl: String? = null,
        maxPrograms: Int = 25000
    ): Pair<List<XmltvChannel>, List<EpgProgram>> {
        val pushback = PushbackInputStream(BufferedInputStream(inputStream, 32768), 16)
        val header = ByteArray(2)
        val readCount = pushback.read(header)
        val isRealGzip = (readCount == 2 && (header[0].toInt() and 0xFF) == 0x1F && (header[1].toInt() and 0xFF) == 0x8B)
        if (readCount > 0) {
            pushback.unread(header, 0, readCount)
        }

        val uncompressedStream: InputStream = if (isRealGzip) {
            try {
                GZIPInputStream(pushback, 32768)
            } catch (e: Exception) {
                Log.w(TAG, "GZIP decompression error, falling back to raw stream: ${e.message}")
                pushback
            }
        } else {
            pushback
        }

        // Buffer and consume UTF-8 BOM (0xEF, 0xBB, 0xBF) if present to prevent XML parser errors
        val pushbackXml = PushbackInputStream(uncompressedStream, 16)
        val bom = ByteArray(3)
        val bomRead = pushbackXml.read(bom)
        if (bomRead >= 3 && (bom[0].toInt() and 0xFF) == 0xEF && (bom[1].toInt() and 0xFF) == 0xBB && (bom[2].toInt() and 0xFF) == 0xBF) {
            // BOM consumed, do not unread
        } else if (bomRead > 0) {
            pushbackXml.unread(bom, 0, bomRead)
        }

        val reader = InputStreamReader(pushbackXml, Charsets.UTF_8)
        val channels = ArrayList<XmltvChannel>(512)
        val channelNameMap = HashMap<String, String>(512)
        val programs = ArrayList<EpgProgram>(8192)
        val fallbackStaleSample = ArrayList<EpgProgram>(512)

        val now = System.currentTimeMillis()
        // Keep only active window (-4 hours to +30 hours) to prevent OutOfMemoryError on huge multi-day XMLTV feeds
        val windowStartMs = now - (4 * 3600 * 1000L)
        val windowEndMs = now + (30 * 3600 * 1000L)

        try {
            val parser = try {
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                factory.newPullParser()
            } catch (_: Throwable) {
                Xml.newPullParser()
            }
            parser.setInput(reader)

            var eventType = parser.eventType

            var currentChannelId: String? = null
            var currentDisplayName: String? = null
            var currentChannelIcon: String? = null

            var progChannelId: String? = null
            var progStartMs: Long = -1L
            var progStopMs: Long = -1L
            var isProgInActiveWindow = false
            var isProgInFallbackSample = false
            var progTitle: String? = null
            var progDesc: String? = null
            var progCategory: String? = null
            var progIcon: String? = null
            var progRating: String? = null

            val textBuilder = StringBuilder(256)
            var captureText = false

            fun getAttr(name: String): String? {
                val count = parser.attributeCount
                for (i in 0 until count) {
                    if (parser.getAttributeName(i).equals(name, ignoreCase = true)) {
                        return parser.getAttributeValue(i)
                    }
                }
                return null
            }

            while (eventType != XmlPullParser.END_DOCUMENT && programs.size < maxPrograms) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name?.lowercase(Locale.US) ?: ""
                        captureText = false
                        when (tagName) {
                            "channel" -> {
                                currentChannelId = getAttr("id")?.trim()
                                currentDisplayName = null
                                currentChannelIcon = null
                            }
                            "display-name" -> {
                                if (currentChannelId != null && currentDisplayName == null) {
                                    textBuilder.setLength(0)
                                    captureText = true
                                }
                            }
                            "icon" -> {
                                if (currentChannelId != null && currentChannelIcon == null) {
                                    currentChannelIcon = getAttr("src")
                                } else if ((isProgInActiveWindow || isProgInFallbackSample) && progIcon == null) {
                                    progIcon = getAttr("src")
                                }
                            }
                            "programme" -> {
                                val chId = getAttr("channel")?.trim()
                                val startStr = getAttr("start")
                                val stopStr = getAttr("stop")
                                val sMs = parseXmltvDate(startStr)
                                val eMs = parseXmltvDate(stopStr)

                                if (!chId.isNullOrEmpty() && sMs != null && eMs != null && eMs > sMs) {
                                    isProgInActiveWindow = (eMs >= windowStartMs && sMs <= windowEndMs)
                                    isProgInFallbackSample = (!isProgInActiveWindow && programs.isEmpty() && fallbackStaleSample.size < 1000)
                                    if (isProgInActiveWindow || isProgInFallbackSample) {
                                        progChannelId = chId
                                        progStartMs = sMs
                                        progStopMs = eMs
                                    } else {
                                        progChannelId = null
                                    }
                                } else {
                                    progChannelId = null
                                    isProgInActiveWindow = false
                                    isProgInFallbackSample = false
                                }
                                progTitle = null
                                progDesc = null
                                progCategory = null
                                progIcon = null
                                progRating = null
                            }
                            "title" -> {
                                if (progChannelId != null && progTitle == null) {
                                    textBuilder.setLength(0)
                                    captureText = true
                                }
                            }
                            "desc" -> {
                                if (progChannelId != null && progDesc == null) {
                                    textBuilder.setLength(0)
                                    captureText = true
                                }
                            }
                            "category" -> {
                                if (progChannelId != null && progCategory == null) {
                                    textBuilder.setLength(0)
                                    captureText = true
                                }
                            }
                            "value" -> {
                                if (progChannelId != null && progRating == null) {
                                    textBuilder.setLength(0)
                                    captureText = true
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (captureText && textBuilder.length < 420) {
                            val txt = parser.text
                            if (!txt.isNullOrEmpty()) {
                                val remaining = 420 - textBuilder.length
                                if (txt.length <= remaining) {
                                    textBuilder.append(txt)
                                } else {
                                    textBuilder.append(txt, 0, remaining)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name?.lowercase(Locale.US) ?: ""
                        when (tagName) {
                            "display-name" -> {
                                if (captureText && currentChannelId != null && currentDisplayName == null) {
                                    val str = textBuilder.toString().trim()
                                    if (str.isNotEmpty()) {
                                        currentDisplayName = str
                                    }
                                }
                                captureText = false
                            }
                            "channel" -> {
                                val cId = currentChannelId
                                if (!cId.isNullOrEmpty()) {
                                    val dName = currentDisplayName ?: cId
                                    channelNameMap[cId] = dName
                                    channels.add(
                                        XmltvChannel(
                                            id = cId,
                                            displayName = dName,
                                            iconUrl = currentChannelIcon
                                        )
                                    )
                                }
                                currentChannelId = null
                            }
                            "title" -> {
                                if (captureText && progChannelId != null && progTitle == null) {
                                    val str = textBuilder.toString().trim()
                                    if (str.isNotEmpty()) progTitle = str
                                }
                                captureText = false
                            }
                            "desc" -> {
                                if (captureText && progChannelId != null && progDesc == null) {
                                    val str = textBuilder.toString().trim()
                                    if (str.isNotEmpty()) progDesc = str
                                }
                                captureText = false
                            }
                            "category" -> {
                                if (captureText && progChannelId != null && progCategory == null) {
                                    val str = textBuilder.toString().trim()
                                    if (str.isNotEmpty()) progCategory = str
                                }
                                captureText = false
                            }
                            "value" -> {
                                if (captureText && progChannelId != null && progRating == null) {
                                    val str = textBuilder.toString().trim()
                                    if (str.isNotEmpty()) progRating = str
                                }
                                captureText = false
                            }
                            "programme" -> {
                                val pChId = progChannelId
                                if (pChId != null && progStopMs > progStartMs) {
                                    val title = progTitle ?: "Sendung"
                                    val desc = progDesc ?: ""
                                    val cat = progCategory ?: "TV"
                                    val resolvedName = channelNameMap[pChId] ?: pChId
                                    val prog = EpgProgram(
                                        id = UUID.nameUUIDFromBytes("${pChId}_${progStartMs}".toByteArray()).toString(),
                                        channelTvgId = pChId,
                                        channelName = resolvedName,
                                        title = title,
                                        description = desc,
                                        startTime = progStartMs,
                                        endTime = progStopMs,
                                        category = cat,
                                        genre = cat,
                                        iconUrl = progIcon,
                                        rating = progRating,
                                        sourceUrl = sourceUrl,
                                        lastUpdated = now
                                    )
                                    if (isProgInActiveWindow) {
                                        programs.add(prog)
                                    } else if (isProgInFallbackSample) {
                                        fallbackStaleSample.add(prog)
                                    }
                                }
                                progChannelId = null
                                isProgInActiveWindow = false
                                isProgInFallbackSample = false
                                captureText = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            try {
                Log.e(TAG, "Error parsing XMLTV: ${e.message}", e)
            } catch (_: Throwable) {}
        } finally {
            try {
                reader.close()
            } catch (_: Exception) {}
        }

        if (programs.isNotEmpty()) {
            return Pair(channels, programs)
        }

        // Only if 0 active programs were in the current window, check fallbackStaleSample
        val maxEndTime = fallbackStaleSample.maxOfOrNull { it.endTime } ?: 0L
        val adjustedPrograms = if (maxEndTime > 0 && maxEndTime < now - (36 * 3600 * 1000L)) {
            val dayMillis = 24 * 3600 * 1000L
            val dayShift = ((now - maxEndTime) / dayMillis + 1) * dayMillis
            fallbackStaleSample.map { p ->
                p.copy(
                    startTime = p.startTime + dayShift,
                    endTime = p.endTime + dayShift,
                    lastUpdated = now
                )
            }
        } else {
            fallbackStaleSample
        }

        return Pair(channels, adjustedPrograms)
    }

    fun parseXmltvDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        val len = trimmed.length

        // Fast-path numeric XMLTV parser ("20261028201500 +0100" or "20261028201500") without regex allocation
        if (len >= 14 && trimmed[0].isDigit() && trimmed[13].isDigit() && !trimmed.contains("T")) {
            return try {
                if (len > 14 && (trimmed.contains(" ") || trimmed.contains("+") || trimmed.contains("-"))) {
                    val normalized = if (len >= 20 && trimmed[len - 3] == ':') {
                        trimmed.substring(0, len - 3) + trimmed.substring(len - 2)
                    } else {
                        trimmed
                    }
                    synchronized(DATE_FORMAT_WITH_TZ) {
                        DATE_FORMAT_WITH_TZ.parse(normalized)?.time
                    }
                } else {
                    val base = trimmed.substring(0, 14)
                    synchronized(DATE_FORMAT_BERLIN) {
                        DATE_FORMAT_BERLIN.parse(base)?.time
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        return try {
            if (trimmed.contains("T")) {
                val afterT = trimmed.substringAfter("T")
                if (trimmed.endsWith("Z", ignoreCase = true)) {
                    synchronized(ISO_FORMAT) {
                        ISO_FORMAT.parse(trimmed.substringBefore("."))?.time
                    }
                } else if (afterT.contains("+") || afterT.contains("-")) {
                    synchronized(ISO_FORMAT_TZ) {
                        ISO_FORMAT_TZ.parse(trimmed)?.time
                    }
                } else {
                    synchronized(ISO_FORMAT) {
                        ISO_FORMAT.parse(trimmed)?.time
                    }
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
