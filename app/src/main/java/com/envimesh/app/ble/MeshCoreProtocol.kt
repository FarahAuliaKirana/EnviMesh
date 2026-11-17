package com.envimesh.app.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * DRAFT helpers for the MeshCore companion protocol over Bluetooth LE.
 * Not wired into the UI yet. Verify every byte against
 * https://docs.meshcore.io/companion_protocol/ and an nRF Connect test
 * before relying on it.
 *
 * Transport: Nordic UART Service. The app writes frames to RX and
 * receives frames as notifications on TX (subscribe to TX first).
 * Each BLE write / notification is exactly one frame.
 */
object MeshCoreProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // app -> radio (write)
    val TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // radio -> app (notify)

    // Commands (app -> radio)
    const val CMD_APP_START: Byte = 0x01
    const val CMD_SEND_CHANNEL_TXT_MSG: Byte = 0x03
    const val CMD_SYNC_NEXT_MESSAGE: Byte = 0x0A
    const val CMD_DEVICE_QUERY: Byte = 0x16

    // Responses / pushes (radio -> app)
    const val RESP_OK = 0x00
    const val RESP_ERROR = 0x01
    const val RESP_SELF_INFO = 0x05
    const val RESP_SENT = 0x06
    const val RESP_CONTACT_MSG = 0x07
    const val RESP_CHANNEL_MSG = 0x08
    const val RESP_NO_MORE_MESSAGES = 0x0A
    const val RESP_DEVICE_INFO = 0x0D
    const val RESP_CONTACT_MSG_V3 = 0x10
    const val RESP_CHANNEL_MSG_V3 = 0x11
    const val PUSH_MESSAGES_WAITING = 0x83

    /** MeshCore text messages are limited to 133 characters. */
    const val MAX_TEXT_CHARS = 133

    /** First command after connecting. TODO: check whether extra bytes (app version/name) are required. */
    fun appStart(): ByteArray = byteArrayOf(CMD_APP_START)

    fun deviceQuery(): ByteArray = byteArrayOf(CMD_DEVICE_QUERY, 0x03)

    fun syncNextMessage(): ByteArray = byteArrayOf(CMD_SYNC_NEXT_MESSAGE)

    /** 0x03 0x00 <channel_idx> <timestamp: 4 bytes LE> <UTF-8 text> */
    fun sendChannelText(
        channelIndex: Int,
        text: String,
        epochSeconds: Long = System.currentTimeMillis() / 1000,
    ): ByteArray {
        require(text.length <= MAX_TEXT_CHARS) { "Text longer than $MAX_TEXT_CHARS characters" }
        val body = text.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(3 + 4 + body.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(CMD_SEND_CHANNEL_TXT_MSG)
            .put(0.toByte())
            .put(channelIndex.toByte())
            .putInt(epochSeconds.toInt())
            .put(body)
            .array()
    }

    /** First byte of a frame received on TX, as an unsigned code. */
    fun responseCode(frame: ByteArray): Int? = frame.firstOrNull()?.toInt()?.and(0xFF)
}

/**
 * EnviMesh's own text format inside MeshCore channel messages.
 * Example: "ALERT|N01|-2.2456|114.1234|WL=42"
 */
data class EnviMeshAlertMessage(
    val nodeId: String,
    val latitude: Double,
    val longitude: Double,
    val waterLevelCm: Int?,
) {
    fun encode(): String =
        "ALERT|$nodeId|$latitude|$longitude" + (waterLevelCm?.let { "|WL=$it" } ?: "")

    companion object {
        fun parse(text: String): EnviMeshAlertMessage? {
            val parts = text.trim().split("|")
            if (parts.size < 4 || parts[0] != "ALERT") return null
            val lat = parts[2].toDoubleOrNull() ?: return null
            val lon = parts[3].toDoubleOrNull() ?: return null
            val wl = parts.drop(4).firstOrNull { it.startsWith("WL=") }?.removePrefix("WL=")?.toIntOrNull()
            return EnviMeshAlertMessage(parts[1], lat, lon, wl)
        }
    }
}
