// MonitorPage.kt
package com.example.forklift_phone.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.forklift_phone.R
import com.example.forklift_phone.ui.theme.SimMonitoringViewModel

@Composable
fun MonitorPage(padding: PaddingValues) {
    val vm: SimMonitoringViewModel = viewModel()
    val pose by vm.pose.collectAsState()

    // 맵 이미지 비율(가로/세로) 추출 → 캔버스와 동일 크기 보장
    val painter = painterResource(R.drawable.factory_map)
    val ratio = remember(painter.intrinsicSize) {
        val w = if (painter.intrinsicSize.width.isFinite()) painter.intrinsicSize.width else 800f
        val h = if (painter.intrinsicSize.height.isFinite()) painter.intrinsicSize.height else 500f
        (w / h).coerceIn(0.5f, 3f)
    }

    // ❑ 경로(0~1 비율). 스샷 흰선 느낌: 왼하단 → 우측 하단 선반 앞 → 위로 꺾어 팔레트 존으로 이동 → 우측 상단 살짝
    //    필요하면 숫자 미세조정만 하면 됨.
    val route = remember {
        listOf(
            0.08f to 0.82f,  // 시작: 좌하단
            0.35f to 0.82f,  // → 오른쪽(선반 앞 통로)
            0.35f to 0.58f,  // ↑ 위로 꺾음
            0.72f to 0.58f,  // → 팔레트 존 왼쪽
            0.72f to 0.40f   // ↑ 팔레트 존 상단 쪽
        )
    }

    // 화면 진입 시 루트 시작
    LaunchedEffect(Unit) {
        vm.startRoute(
            route = route,
            speed = 0.14f,   // 속도 튜닝
            dwellMs = 350L,  // 코너에서 잠깐 멈춤
            loop = false    // 루트 이동 반복
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        color = Color(0xFFF7F7F7)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Monitoring", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                // 맵-캔버스 동일 영역 확보 (letterbox 제거)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio) // ← 이미지 비율
                            .align(Alignment.TopCenter)
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = "Factory map",
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.FillBounds // ← 캔버스와 1:1
                        )
                        Canvas(Modifier.matchParentSize()) {
                            // 현재 위치
                            val px = pose.x * size.width
                            val py = pose.y * size.height
                            drawCircle(
                                color = Color.Red,
                                radius = 12f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }
    }
}
/*
@Composable
fun MonitorPage(padding: PaddingValues, pose: ForkliftPose?) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        color = Color(0xFFF7F7F7)    // 라이트 그레이 배경
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Monitoring", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.factory_map),
                        contentDescription = "Factory map",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .align(Alignment.TopCenter)
                            .background(Color(0xFFF5F5F5)),
                        contentScale = ContentScale.Fit
                    )
                    // TODO: Canvas로 포즈 점 찍기 연결 예정
                }
            }
        }
    }
}
*/