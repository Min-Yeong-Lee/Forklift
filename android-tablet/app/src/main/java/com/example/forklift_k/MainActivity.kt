package com.example.forklift_k

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

/** ▷ 임시 래퍼: 네 프로젝트에 AppConfig 파일이 따로 있으면 이 블록은 삭제해도 됩니다. */
object AppConfig {
    /** AWS IoT Core ATS 엔드포인트(프로토콜/포트 없이 host만) */
    const val AWS_IOT_ENDPOINT: String = "a15xw0pdafxycc-ats.iot.us-east-1.amazonaws.com"
}

/**
 * 신규 규약 적용 MainActivity
 * - clientId: forklift_{wh}-{line}-{unit}-{role}-{instance}
 * - topicBase: fk/{wh}/{line}/{unit}
 * - publish: dev/cmd, app/{role}/{inst}/status(retain)
 * - subscribe: dev/ack, jet/{inst}/telemetry
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) 디바이스 네이밍(필요시 값만 수정)
        val naming = DeviceNaming(
            warehouse = "wh01",
            line = "A",
            unit = "fl01",
            role = "tb",               // ✅ 요청대로 tb 하드코딩
            instance = defaultInstance()
        )
        val topics = TopicPlan(naming)
        val clientId = naming.clientId

        // 2) MQTT 연결 (엔드포인트는 AppConfig에서)
        MqttManager.init(applicationContext, AppConfig.AWS_IOT_ENDPOINT, clientId)

        // 3) 연결 콜백: 구독 + status 게시(retain 권장)
        MqttManager.setOnConnected {
            // (a) 신규 규약 구독
            MqttManager.subscribe(topics.ack) { _, payload ->
                Log.d("ACK", "📥 ${topics.ack} :: $payload")
            }

            MqttManager.subscribe(topics.telemetry("01")) { _, payload ->
                Log.d("TEL", "📥 ${topics.telemetry("01")} :: $payload")
                // TODO: 좌표 파싱해서 화면 상태 갱신
            }

            // (b) 앱 상태 게시 (retain 옵션이 있으면 true 권장)
            val status = json(
                "clientId" to clientId,
                "online" to true,
                "ts" to System.currentTimeMillis()
            )
            // MqttManager.publish(topics.appStatus, status, qos = 1, retain = true)
            MqttManager.publish(topics.appStatus, status)
            Log.i("MQTT", "✅ Connected as $clientId, status -> ${topics.appStatus}")
        }

        // 4) UI
        setContent {
            AppLightTheme {
                HomeScreen(
                    onMove = { pallet, dest ->
                        val reqId = UUID.randomUUID().toString()
                        val payload = json(
                            "command" to "move_to",
                            "target_uid" to pallet,
                            "destination" to dest,
                            "from" to clientId,
                            "req_id" to reqId,
                            "ts" to System.currentTimeMillis()
                        )
                        MqttManager.publish(topics.cmd, payload) // qos=1/retain 지원 시 교체
                        Toast.makeText(this, "MOVE 전송 (${topics.cmd})", Toast.LENGTH_SHORT).show()
                        Log.d("CMD", "🚀 ${topics.cmd} :: $payload")
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttManager.disconnect()
    }

    // ──────────────────────────────── 유틸/모델 ────────────────────────────────

    // 인스턴스 기본값: ANDROID_ID 끝 2자리(운영은 설정 화면에서 수동 지정 권장)
    private fun defaultInstance(): String {
        val id = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "01"
        return id.takeLast(2).uppercase()
    }

    // JSON 간단 생성기(의존성 없이)
    private fun json(vararg pairs: Pair<String, Any?>): String {
        fun esc(s: String) = s.replace("\"", "\\\"")
        return buildString {
            append('{')
            pairs.forEachIndexed { i, (k, v) ->
                if (i > 0) append(',')
                append('"').append(esc(k)).append('"').append(':')
                when (v) {
                    null -> append("null")
                    is Number, is Boolean -> append(v.toString())
                    else -> append('"').append(esc(v.toString())).append('"')
                }
            }
            append('}')
        }
    }
}

/** 신규 규약용 네이밍 & 토픽 빌더 */
data class DeviceNaming(
    val warehouse: String,
    val line: String,
    val unit: String,
    val role: String,       // "tb" / "ph" / "esp" / "jet"
    val instance: String    // "01".. "99"
) {
    val clientId: String = "forklift_${warehouse}-${line}-${unit}-${role}-${instance}"
    val topicBase: String = "fk/${warehouse}/${line}/${unit}"
}

class TopicPlan(private val n: DeviceNaming) {
    val cmd       = "${n.topicBase}/dev/cmd"
    val ack       = "${n.topicBase}/dev/ack"
    val progress  = "${n.topicBase}/dev/progress"
    fun telemetry(inst: String = "01") = "${n.topicBase}/jet/$inst/telemetry"
    val appStatus = "${n.topicBase}/app/${n.role}/${n.instance}/status"
}

// ──────────────────────────────── UI ────────────────────────────────

@Composable
fun AppLightTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF3B82F6),
        secondary = Color(0xFF6B7280),
        background = Color(0xFFF2F2F7),
        surface = Color(0xFFF7F7FA),
        onSurface = Color(0xFF111827)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pallets: List<String> = listOf("Pallet A", "Pallet B", "Pallet C"),
    destinations: List<String> = listOf("Sector A", "Sector B", "Sector C"),
    onMove: (String, String) -> Unit
) {
    val ctx = LocalContext.current
    var pallet by remember { mutableStateOf<String?>(null) }
    var dest by remember { mutableStateOf<String?>(null) }
    val canMove = pallet != null && dest != null

    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Select Pallet", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))

        var pExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = pExpanded, onExpandedChange = { pExpanded = !pExpanded }) {
            OutlinedTextField(
                value = pallet ?: "Choose a pallet",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedTextColor = Color(0xFF111827),
                    unfocusedTextColor = Color(0xFF111827),
                    disabledTextColor = Color(0xFF111827),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
                pallets.forEach {
                    DropdownMenuItem(text = { Text(it, color = Color(0xFF111827)) }, onClick = {
                        pallet = it; pExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Select Destination", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))

        var dExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = dExpanded, onExpandedChange = { dExpanded = !dExpanded }) {
            OutlinedTextField(
                value = dest ?: "Choose a destination",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedTextColor = Color(0xFF111827),
                    unfocusedTextColor = Color(0xFF111827),
                    disabledTextColor = Color(0xFF111827),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = dExpanded, onDismissRequest = { dExpanded = false }) {
                destinations.forEach {
                    DropdownMenuItem(text = { Text(it, color = Color(0xFF111827)) }, onClick = {
                        dest = it; dExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                onMove(pallet!!, dest!!)
                Toast.makeText(ctx, "명령 전송 완료 ✅", Toast.LENGTH_SHORT).show()
            },
            enabled = canMove,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD1D5DB),
                disabledContentColor = Color.White
            )
        ) {
            Text("MOVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
