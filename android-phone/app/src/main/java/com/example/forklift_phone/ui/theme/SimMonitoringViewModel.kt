package com.example.forklift_phone.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

data class SimPose(val x: Float, val y: Float)

class SimMonitoringViewModel : ViewModel() {
    private val _pose = MutableStateFlow(SimPose(0.08f, 0.82f)) // 시작점 (비율좌표)
    val pose: StateFlow<SimPose> = _pose.asStateFlow()

    private var job: Job? = null

    /**
     * 폴리라인 경로를 따라 이동 (모든 좌표는 0~1 비율)
     * @param route 예: listOf(0.08f to 0.82f, 0.35f to 0.82f, 0.35f to 0.55f, ...)
     * @param speed 비율/초 (0.25f면 화면 너비의 25%/s 정도 속도)
     * @param dwellMs 코너에서 멈추는 시간(ms)
     * @param loop 끝까지 가면 처음으로 반복
     */
    fun startRoute(
        route: List<Pair<Float, Float>>,
        speed: Float = 0.25f,
        dwellMs: Long = 400L,
        loop: Boolean = true
    ) {
        if (route.size < 2) return
        stop()
        job = viewModelScope.launch(Dispatchers.Default) {
            var i = 0
            var cur = route[0]
            _pose.value = SimPose(cur.first, cur.second)
            delay(200) // 시작 전 살짝 대기

            while (isActive) {
                val nxt = route[i + 1]
                val dx = nxt.first - cur.first
                val dy = nxt.second - cur.second
                val segLen = hypot(dx, dy)
                if (segLen == 0f) {
                    // 같은 점이면 스킵
                    i++
                    if (i >= route.lastIndex) {
                        if (!loop) break
                        i = 0; cur = route[0]; continue
                    }
                    cur = nxt; continue
                }

                // 한 프레임마다 전진
                var t = 0f
                val frameMs = 16L
                while (t < 1f && isActive) {
                    val dt = frameMs / 1000f
                    val step = (speed * dt) / segLen   // 구간 길이에 따라 보정
                    t = (t + step).coerceAtMost(1f)

                    // Linear interpolation (ㄴ/ㄱ자 구간에서도 자연스럽게 직선 이동)
                    val x = cur.first + dx * t
                    val y = cur.second + dy * t
                    _pose.value = SimPose(x, y)
                    delay(frameMs)
                }

                // 코너에서 잠깐 정지(파레트 픽업/회전 느낌)
                delay(dwellMs)

                // 다음 구간
                i++
                if (i >= route.lastIndex) {
                    if (!loop) break
                    i = 0
                }
                cur = route[i]
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
