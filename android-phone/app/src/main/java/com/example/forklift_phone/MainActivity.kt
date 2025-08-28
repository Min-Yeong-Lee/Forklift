package com.example.forklift_phone

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.forklift_phone.ui.theme.*
import com.example.forklift_phone.ui.theme.Pallet

// ✅ MQTT 네이밍/매니저
import com.example.forklift_phone.mqtt.DeviceNaming
import com.example.forklift_phone.mqtt.ForkliftPose
import com.example.forklift_phone.mqtt.MqttManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            Forklift_phoneTheme {
                val nav = rememberNavController()
                val activity = this@MainActivity
                val appCtx = applicationContext

                // ✅ 1) 네이밍 규칙
                val naming = remember {
                    DeviceNaming(
                        warehouse = "wh01",
                        line = "A",
                        unit = "fl01",
                        role = "ph",   // phone
                        instance = "01"
                    )
                }

                // ✅ 2) MQTT 매니저 준비
                val mqtt = remember { MqttManager(appCtx, naming) }

                // ✅ 3) 연결/해제 라이프사이클
                LaunchedEffect(Unit) {
                    mqtt.connect()
                    Toast.makeText(activity, "MQTT 연결 시도…", Toast.LENGTH_SHORT).show()
                }
                DisposableEffect(Unit) {
                    onDispose { mqtt.disconnect() }
                }

                // ✅ 4) ACK/좌표 Flow 수집
                var latestPose by remember { mutableStateOf<ForkliftPose?>(null) }
                LaunchedEffect(Unit) {
                    mqtt.ackFlow.collectLatest { ack ->
                        Toast.makeText(activity, "ACK: $ack", Toast.LENGTH_SHORT).show()
                    }
                }
                LaunchedEffect(Unit) {
                    mqtt.poseFlow.collectLatest { pose ->
                        latestPose = pose
                    }
                }

                // ✅ 5) Pallet 상태/로그 공통 관리 (촬영용 시뮬 + Dashboard 연동)
                var pallets by remember {
                    mutableStateOf(
                        listOf(
                            Pallet("Pallet A", "대기", ""),
                            Pallet("Pallet B", "대기", ""),
                            Pallet("Pallet C", "대기", "")
                        )
                    )
                }
                var logs by remember { mutableStateOf(listOf<String>()) }

                // 상태 전환 시뮬 함수 (대기 → 이동 중 → 완료)
                fun simulateMove(pallet: String, dest: String) {
                    // 이동 중 표시
                    pallets = pallets.map {
                        if (it.name == pallet) it.copy(state = "이동 중", dest = dest) else it
                    }
                    logs = listOf("$pallet → $dest 이동 시작") + logs

                    // 3초 후 완료 처리
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(3000)
                        pallets = pallets.map {
                            if (it.name == pallet) it.copy(state = "적재 완료", dest = dest) else it
                        }
                        logs = listOf("$pallet → $dest 적재 완료") + logs
                    }
                }

                // ✅ 5-1) 재고 상태 + 더미 스캔 로직 추가
                // 재고 상태
                var inventory by remember { mutableStateOf(listOf<InventoryItem>()) }

                // 더미 DB (촬영용)
                val mockInventoryDb: Map<String, List<InventoryItem>> = mapOf(
                    "Pallet A" to listOf(
                        InventoryItem("Pallet A", "알루미늄 바", 40, "ea"),
                        InventoryItem("Pallet A", "너트 M6", 120, "ea")
                    ),
                    "Pallet B" to listOf(
                        InventoryItem("Pallet B", "시멘트", 30, "bag"),
                        InventoryItem("Pallet B", "철근 D10", 75, "ea")
                    ),
                    "Pallet C" to listOf(
                        InventoryItem("Pallet C", "목재 2x4", 48, "ea"),
                        InventoryItem("Pallet C", "볼트 M8", 90, "ea")
                    )
                )

                // 촬영용 '스캔' 시뮬 (버튼 누르면 해당 팔레트 재고를 읽어온 것처럼)
                fun mockScan(pallet: String) {
                    val items = mockInventoryDb[pallet].orEmpty()
                    inventory = items
                    logs = listOf("재고 스캔: $pallet (${items.size}종)") + logs
                }

                // ✅ 6) 네비게이션
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavBar(nav) }
                ) { inner ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable("home") {
                            HomePage(
                                padding = inner,
                                onMove = { pallet, dest ->
                                    // ① MQTT publish
                                    mqtt.publishMove(
                                        targetUid = pallet,
                                        destination = dest
                                    ) { ok: Boolean ->
                                        Toast.makeText(
                                            activity,
                                            if (ok) "명령 전송 완료" else "명령 전송 실패",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    // ② 촬영용 상태/로그 시뮬
                                    simulateMove(pallet, dest)
                                }
                            )
                        }
                        composable("monitor") {
                            //MonitorPage(padding = inner, pose = latestPose)
                            MonitorPage(padding = inner)
                        }
                        composable("dashboard") {
                            DashboardPage(
                                padding = inner,
                                pallets = pallets,
                                logs = logs,
                                inventory = inventory,
                                onMockScan = { pallet -> mockScan(pallet) }
                            )
                        }

                    }
                }
            }
        }
    }
}
