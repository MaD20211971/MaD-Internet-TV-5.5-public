package com.example.cast

import android.content.Context
import com.example.cast.model.CastDevice
import com.example.cast.model.CastProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persistiert manuell eingetragene Cast-Geräte (z. B. Kodi mit Port & Login, Roku oder feste TV-IP)
 * lokal in SharedPreferences.
 */
object ManualCastDeviceStore {

    private const val PREFS_NAME = "mad_universal_cast_manual_devices"
    private const val KEY_DEVICES_JSON = "manual_devices_v1"

    fun loadDevices(context: Context): List<CastDevice> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DEVICES_JSON, null) ?: return emptyList()
        return parseJson(raw)
    }

    fun addDevice(
        context: Context,
        name: String,
        protocol: CastProtocol,
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ): CastDevice {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        val cleanPort = if (port > 0) port else protocol.defaultPort
        val cleanName = name.trim().ifBlank { "${protocol.shortBadge} ($cleanHost)" }

        val device = CastDevice(
            id = "manual_${protocol.name.lowercase()}_${UUID.randomUUID()}",
            name = cleanName,
            protocol = protocol,
            host = cleanHost,
            port = cleanPort,
            modelName = "Manuell ($cleanHost:$cleanPort)",
            username = username.trim(),
            password = password,
            isManual = true
        )

        val current = loadDevices(context).toMutableList()
        current.add(device)
        saveDevices(context, current)
        return device
    }

    fun deleteDevice(context: Context, deviceId: String): List<CastDevice> {
        val updated = loadDevices(context).filterNot { it.id == deviceId }
        saveDevices(context, updated)
        return updated
    }

    private fun saveDevices(context: Context, devices: List<CastDevice>) {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("protocol", d.protocol.name)
                    put("host", d.host)
                    put("port", d.port)
                    put("modelName", d.modelName)
                    put("username", d.username)
                    put("password", d.password)
                }
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICES_JSON, arr.toString())
            .apply()
    }

    internal fun parseJson(raw: String): List<CastDevice> {
        val result = mutableListOf<CastDevice>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val protocolName = obj.optString("protocol", CastProtocol.KODI.name)
                val protocol = runCatching { CastProtocol.valueOf(protocolName) }.getOrDefault(CastProtocol.KODI)
                val host = obj.optString("host", "").trim()
                if (host.isBlank()) continue
                val port = obj.optInt("port", protocol.defaultPort).let { if (it > 0) it else protocol.defaultPort }
                result.add(
                    CastDevice(
                        id = obj.optString("id", "manual_$i"),
                        name = obj.optString("name", "${protocol.shortBadge} ($host)"),
                        protocol = protocol,
                        host = host,
                        port = port,
                        modelName = obj.optString("modelName", "Manuell ($host:$port)"),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        isManual = true
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
