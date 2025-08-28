// MqttManager.kt
package com.example.forklift_phone.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// ───────── 데이터 모델 ─────────
data class ForkliftPose(val x: Float, val y: Float)

// ───────── 네이밍 ─────────
data class DeviceNaming(
    val warehouse: String = "wh01",
    val line: String = "A",
    val unit: String = "fl01",
    val role: String = "ph",   // tb=tablet, ph=phone, esp, jet 등
    val instance: String = "01"
)

// ───────── 설정 상수 ─────────
private const val TAG = "MqttManager"

// AWS IoT 엔드포인트
private const val AWS_ENDPOINT =
    "ssl://a15xw0pdafxycc-ats.iot.us-east-1.amazonaws.com:8883"

// 자산 파일명 (mTLS)
private const val FILE_CA   = "AmazonRootCA1.pem"
private const val FILE_CERT = "device.pem.crt"
private const val FILE_KEY  = "private_pkcs8.key"

// QoS 권장
private const val qosCmdAck = 1

class MqttManager(
    private val ctx: Context,
    private val naming: DeviceNaming = DeviceNaming()
) {
    // ✅ 메인스레드 핸들러 (외부 콜백은 전부 여기로 올려서 실행)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ✅ keepalive 전용 핸들러(안드12 PingSender 이슈 회피)
    private val keepAliveHandler = Handler(Looper.getMainLooper())

    // clientId: forklift_${wh}-${line}-${unit}-${role}-${instance}
    private val clientId =
        "forklift_${naming.warehouse}-${naming.line}-${naming.unit}-${naming.role}-${naming.instance}"

    // topic base: fk/{wh}/{line}/{unit}
    private val topicBase = "fk/${naming.warehouse}/${naming.line}/${naming.unit}"

    // topic map
    private val topicCmd       = "$topicBase/dev/cmd"                                 // 앱 → 디바이스 명령
    private val topicAck       = "$topicBase/dev/ack"                                 // 디바이스 → 앱 응답/ACK
    private val topicProgress  = "$topicBase/dev/progress"                            // 진행 상황(옵션)
    private val topicTelemetry = "$topicBase/jet/${naming.instance}/telemetry"        // 젯슨/ESP → 앱 텔레메트리(좌표 등)
    private val topicStatus    = "$topicBase/app/${naming.role}/${naming.instance}/status" // ✅ 앱 상태(retain)

    private val client = MqttAndroidClient(ctx.applicationContext, AWS_ENDPOINT, clientId)

    // 외부 노출 Flow
    private val _connectionState =
        MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val connectionState: SharedFlow<Boolean> = _connectionState

    private val _poseFlow =
        MutableSharedFlow<ForkliftPose>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val poseFlow: SharedFlow<ForkliftPose> = _poseFlow

    private val _ackFlow =
        MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val ackFlow: SharedFlow<String> = _ackFlow

    // ───────── Public: 연결 ─────────
    fun connect(onConnected: () -> Unit = {}) {
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            keepAliveInterval = 0           // 수동 keepalive
            connectionTimeout = 10
            socketFactory = buildSocketFactory(
                caInput   = ctx.assets.open(FILE_CA),
                certInput = ctx.assets.open(FILE_CERT),
                keyInput  = ctx.assets.open(FILE_KEY),
            )
        }

        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "❌ connectionLost: ${cause?.message}")
                _connectionState.tryEmit(false)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString().orEmpty()
                when (topic) {
                    topicTelemetry -> parsePose(payload)?.let { _poseFlow.tryEmit(it) }
                    topicAck       -> _ackFlow.tryEmit(payload)
                    topicProgress  -> { /* 필요 시 별도 Flow 추가 */ }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.d(TAG, "✅ deliveryComplete")
            }
        })

        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "✅ MQTT connected as $clientId")
                _connectionState.tryEmit(true)
                subscribeInternal()
                publishStatus(online = true) { /* ignore */ }
                startManualKeepAlive()
                // ✅ 외부 콜백도 메인 스레드에서
                mainHandler.post { onConnected() }
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "❌ MQTT connect fail: ${exception?.message}")
                _connectionState.tryEmit(false)
                // 연결 실패 콜백 필요하면 여기에 mainHandler.post로 추가 가능
            }
        })
    }

    // ───────── Public: 명령 퍼블리시 ─────────
    /**
     * MOVE 명령
     * JSON:
     * {
     *   "target_uid": "Pallet-123",
     *   "command": "move_to",
     *   "destination": "Sector-A",
     *   "from": "<clientId>",
     *   "req_id": 1712345678900,
     *   "ts":    1712345678900
     * }
     */
    fun publishMove(targetUid: String, destination: String, onDone: (Boolean) -> Unit = {}) {
        val now = System.currentTimeMillis()
        val json = """
            {
              "target_uid": "${targetUid.trim()}",
              "command": "move_to",
              "destination": "${destination.trim()}",
              "from": "$clientId",
              "req_id": $now,
              "ts": $now
            }
        """.trimIndent()
        publish(topicCmd, json, qos = qosCmdAck, retained = false, onDone = onDone)
    }

    // 진행상황/Progress (옵션)
    fun publishProgress(stage: String, detail: String? = null, onDone: (Boolean) -> Unit = {}) {
        val json = """
            {
              "from": "$clientId",
              "stage": "${stage.trim()}",
              "detail": ${if (detail == null) "null" else "\"${detail.trim()}\""},
              "ts": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        publish(topicProgress, json, qos = qosCmdAck, retained = false, onDone = onDone)
    }

    // 상태(Status) retain
    fun publishStatus(online: Boolean, extra: String? = null, onDone: (Boolean) -> Unit = {}) {
        val json = """
            {
              "clientId": "$clientId",
              "online": $online,
              "ts": ${System.currentTimeMillis()},
              "role": "${naming.role}",
              "inst": "${naming.instance}"${if (extra != null) ", \"extra\": \"${extra.trim()}\"" else ""}
            }
        """.trimIndent()
        publish(topicStatus, json, qos = 1, retained = true, onDone = onDone)
    }

    // 일반 퍼블리시 (✅ 외부 콜백을 무조건 메인에서 호출)
    fun publish(
        topic: String,
        payload: String,
        qos: Int = 1,
        retained: Boolean = false,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (!client.isConnected) {
            Log.e(TAG, "❌ publish failed: not connected")
            mainHandler.post { onDone(false) } // ✅ 메인에서
            return
        }
        try {
            val msg = MqttMessage(payload.toByteArray()).apply {
                this.qos = qos
                isRetained = retained
            }
            client.publish(topic, msg, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    mainHandler.post { onDone(true) } // ✅ 메인에서
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ publish error: ${exception?.message}")
                    mainHandler.post { onDone(false) } // ✅ 메인에서
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ publish exception: ${e.message}")
            mainHandler.post { onDone(false) } // ✅ 메인에서
        }
    }

    // ───────── Public: 연결 해제 ─────────
    fun disconnect() {
        try {
            stopManualKeepAlive()
            publishStatus(online = false) { /* best-effort */ }
            if (client.isConnected) client.disconnect()
            client.unregisterResources()
            client.close()
            _connectionState.tryEmit(false)
        } catch (_: Exception) { }
    }

    // ───────── 내부: 구독/파서/keepalive ─────────
    private fun subscribeInternal() {
        try {
            client.subscribe(topicTelemetry, qosCmdAck, null, null) // 좌표/센서 수신
            client.subscribe(topicAck,       qosCmdAck, null, null) // 명령 ACK 수신
            client.subscribe(topicProgress,  qosCmdAck, null, null) // (옵션) 진행상황
            Log.d(TAG, "✅ subscribed: $topicTelemetry, $topicAck, $topicProgress")
        } catch (e: Exception) {
            Log.e(TAG, "❌ subscribe error: ${e.message}")
        }
    }

    // Android 12 PingSender 이슈 회피용 수동 keepalive (15초)
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (client.isConnected) {
                try {
                    client.publish(
                        "$clientId/ping",
                        MqttMessage("alive".toByteArray()).apply { qos = 0; isRetained = false }
                    )
                    Log.d(TAG, "📡 keepalive ping")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ keepalive fail: ${e.message}")
                }
            }
            keepAliveHandler.postDelayed(this, 15_000L)
        }
    }
    private fun startManualKeepAlive() = keepAliveHandler.post(keepAliveRunnable)
    private fun stopManualKeepAlive() = keepAliveHandler.removeCallbacks(keepAliveRunnable)

    private fun parsePose(s: String): ForkliftPose? {
        fun pick(key: String): Float? {
            val idx = s.indexOf("\"$key\"")
            if (idx < 0) return null
            val sub = s.substring(idx)
            val num = sub.substring(sub.indexOf(':') + 1).trim()
                .takeWhile { it.isDigit() || it == '.' || it == '-' }
            return num.toFloatOrNull()
        }
        val x = pick("x") ?: return null
        val y = pick("y") ?: return null
        return ForkliftPose(x, y)
    }

    // ───────── mTLS 소켓팩토리 ─────────
    private fun buildSocketFactory(
        caInput: InputStream,
        certInput: InputStream,
        keyInput: InputStream
    ): SSLSocketFactory {
        val cf = CertificateFactory.getInstance("X.509")

        // CA → TrustManager
        val caCert = cf.generateCertificate(caInput)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("ca", caCert)
            }
            init(ks)
        }

        // 클라 인증서+개인키 → KeyManager
        val clientCert = cf.generateCertificate(certInput)
        val privateKey = loadPrivateKeyFromPem(keyInput)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry("key", privateKey, null, arrayOf(clientCert))
            }
            init(ks, null)
        }

        val ctx = SSLContext.getInstance("TLSv1.2").apply {
            init(kmf.keyManagers, tmf.trustManagers, SecureRandom())
        }
        return ctx.socketFactory
    }

    private fun loadPrivateKeyFromPem(input: InputStream): PrivateKey {
        val pem = input.bufferedReader().use { it.readText() }
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(pem)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
