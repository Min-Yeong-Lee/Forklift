package com.example.forklift_k

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.util.UUID

class ForkliftViewModel : ViewModel() {
    private val _selectedPallet = mutableStateOf("")
    private val _selectedDest = mutableStateOf("")
    private val _isSending = mutableStateOf(false)
    private val _lastError = mutableStateOf<String?>(null)

    val selectedPallet: State<String> = _selectedPallet
    val selectedDest: State<String> = _selectedDest
    val isSending: State<Boolean> = _isSending
    val lastError: State<String?> = _lastError

    private val _isOnline = mutableStateOf(false)
    val isOnline: State<Boolean> = _isOnline
    fun setOnline(v: Boolean) { _isOnline.value = v }

    fun onAckReceived(payload: String) {
        // TODO: request_id 매칭, 상태 업데이트, 토스트/스낵바 등
        println("🟢 ACK: $payload")
    }

    fun selectPallet(pallet: String) { _selectedPallet.value = pallet.trim() }
    fun selectDestination(dest: String) { _selectedDest.value = dest.trim() }

    fun sendForkliftCommand() {
        val pallet = _selectedPallet.value
        val dest = _selectedDest.value
        if (pallet.isBlank() || dest.isBlank()) {
            println("🚨 Pallet/Destination 비어있음")
            return
        }
        if (!_isOnline.value) {
            println("⏳ MQTT 연결 대기 중 – 전송 보류")
            return
        }

        val json = """
            {
              "schema_ver": "1.0",
              "request_id": "${java.util.UUID.randomUUID()}",
              "target_uid": "$pallet",
              "command": "move_to",
              "destination": "$dest",
              "timestamp": ${System.currentTimeMillis() / 1000}
            }
        """.trimIndent()

        MqttManager.publish("forklift/command", json)
        println("✅ 지게차 이동 명령 전송 완료: $json")
    }
}