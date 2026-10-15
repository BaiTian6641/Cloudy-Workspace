package dev.cloudy.qualification.rdp

import org.json.JSONObject

/** Private operator-provisioned fixture identity; never included in snapshots or journals. */
internal class RdpConfig(val host: String, val port: Int, val username: String, val domain: String,
    val password: String, val sha256: String, val width: Int, val height: Int) {
    fun arguments() = arrayOf("cloudy-rdp-probe", "/v:$host", "/port:$port", "/sec:nla",
        "/gdi:sw", "/size:${width}x$height", "/bpp:32", "/gfx:avc420", "/network:lan",
        "/kbd:unicode:on", "/audio-mode:2", "-clipboard", "-auto-reconnect", "/log-level:ERROR")
    companion object {
        fun parse(text: String): RdpConfig {
            require(text.length <= 16384) { "configuration-too-large" }
            val json = JSONObject(text)
            require(json.getInt("schemaVersion") == 1) { "configuration-version" }
            val host = json.getString("host")
            require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9.-]+")) && !host.startsWith("-")) { "configuration-host" }
            val port = json.getInt("port"); require(port in 1..65535) { "configuration-port" }
            val user = json.getString("username"); val domain = json.optString("domain", "")
            val password = json.getString("password"); val pin = json.getString("certificateSha256")
            require(user.length in 1..256 && domain.length <= 256 && password.length in 1..4096 &&
                listOf(user, domain, password).none { '\u0000' in it }) { "configuration-credentials" }
            require(pin.matches(Regex("[0-9a-fA-F]{64}"))) { "configuration-certificate-pin" }
            val width = json.optInt("width", 1280); val height = json.optInt("height", 720)
            require(width in 640..2560 && height in 480..1600 && width.toLong() * height <= 4096000) { "configuration-frame-size" }
            return RdpConfig(host, port, user, domain, password, pin, width, height)
        }
    }
}
