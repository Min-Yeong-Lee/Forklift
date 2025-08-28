package com.example.forklift_phone.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.forklift_phone.ui.theme.Pallet
import com.example.forklift_phone.ui.theme.InventoryItem


@Composable
fun DashboardPage(
    padding: PaddingValues,
    pallets: List<Pallet>,
    logs: List<String>,
    inventory: List<InventoryItem>,
    onMockScan: (String) -> Unit
) {
    Surface(
        Modifier.fillMaxSize().padding(padding),
        color = Color(0xFFF7F7F7)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {

            // 1. 실시간 적재 현황
            Text("실시간 적재 현황", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    pallets.forEach {
                        Text("▶ ${it.name} : ${it.state} ${if (it.dest.isNotEmpty()) "(${it.dest})" else ""}")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. 재고 관리 (스캔 버튼 + 리스트)
            Text("재고 관리", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // 스캔 버튼들
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Pallet A", "Pallet B", "Pallet C").forEach { p ->
                            OutlinedButton(
                                onClick = { onMockScan(p) },
                                modifier = Modifier
                                    .weight(1f)           // ✅ 세 버튼 동일 너비
                                    .height(44.dp),       // 살짝 낮춰 폭 확보
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp) // ✅ 내부 여백 축소
                            ) {
                                Text(
                                    text = "Scan $p",
                                    maxLines = 1,                         // ✅ 줄바꿈 방지
                                    overflow = TextOverflow.Ellipsis,     // ✅ 길면 … 처리
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (inventory.isEmpty()) {
                        Text("스캔된 재고가 없습니다.", color = Color.Gray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(inventory) { it ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        it.pallet,
                                        modifier = Modifier.width(88.dp),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(it.item, modifier = Modifier.weight(1f))
                                    Text("${it.qty} ${it.unit}")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. 기록 조회
            Text("기록 조회", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(logs) { log ->
                        Text("• $log", fontSize = 14.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}


/*
@Composable
fun DashboardPage(padding: PaddingValues) {
    Surface(Modifier.fillMaxSize().padding(padding), color = Color(0xFFF7F7F7)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("실시간 적재 현황", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF222222))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) { /* ... */ }

            Spacer(Modifier.height(16.dp))
            Text("재고 관리", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF222222))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) { /* ... */ }

            Spacer(Modifier.height(16.dp))
            Text("기록 조회", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF222222))
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) { /* ... */ }
        }
    }
}
*/
