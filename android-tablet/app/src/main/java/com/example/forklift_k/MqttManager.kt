package com.example.forklift_k

import info.mqtt.android.service.Ack
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.*
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.security.KeyStore
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory


/**
 * MQTT 공용 매니저 (AWS IoT / mTLS / 재연결 / 재구독)
 * - 서버 URI: "ssl://<ats-endpoint>:8883"  또는 순수 호스트만 주면 자동 조립
 * - mTLS: res/raw 의 PEM + PKCS#8 로 SSLSocketFactory 생성 (TabletConfig.sslSocketFactory)
 * - 중복 init / CLIENT_CLOSED(32111) 방지
 * - 재연결 시 자동 재구독
 * - 콜백은 항상 main thread에서 호출
 */
object MqttManager {
    private const val TAG = "MqttManager"
    private const val DEFAULT_PORT = 8883
    private const val DEFAULT_PING_TOPIC = "fk/health/ping"

    @Volatile private var initialized = false
    @Volatile private var connecting = false

    @SuppressLint("StaticFieldLeak")
    private lateinit var client: MqttAndroidClient
    private var connected = false

    // 메인 스레드 디스패처
    private val main = Handler(Looper.getMainLooper())

    // 구독 관리 (재연결 재구독용)
    private data class Sub(val filter: String, val qos: Int, val cb: (String, String) -> Unit)
    private val subscriptions = mutableListOf<Sub>()

    // 외부 연결 콜백
    private var onConnected: (() -> Unit)? = null

    // 핑 사용 여부
    var disablePing: Boolean = false

    // 내부 보관용 MQTT 콜백(재생성 시 재등록)
    private val internalCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            connected = true
            Log.i(TAG, if (reconnect) "🔁 재연결 성공: $serverURI" else "✅ 최초 연결 성공: $serverURI")

            // 오프라인 버퍼
            try {
                val opts = DisconnectedBufferOptions().apply {
                    isBufferEnabled = true
                    bufferSize = 200
                    isPersistBuffer = false
                    isDeleteOldestMessages = true
                }
                client.setBufferOpts(opts)   // ← 중요: client. 으로 호출
            } catch (e: Exception) {
                Log.w(TAG, "setBufferOpts 실패: ${e.message}")
            }

            // 자동 재구독
            resubscribeAll()

            // 외부 콜백
            onConnected?.let { cb -> main.post { cb() } }

            // 헬스 핑
            if (!disablePing) startPing()
        }

        override fun connectionLost(cause: Throwable?) {
            connected = false
            Log.w(TAG, "❌ 연결 끊김: ${cause?.message}", cause)
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            if (topic == null) return
            val payload = message?.toString().orEmpty()
            val sub = findFirstMatch(topic)
            if (sub != null) {
                main.post { sub.cb(topic, payload) }
            } else {
                Log.d(TAG, "📥 [unhandled $topic] $payload")
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) { /* optional */ }
    }

    fun isConnected(): Boolean = ::client.isInitialized && connected
    fun setOnConnected(cb: () -> Unit) { onConnected = cb }

    /**
     * @param brokerHostOrUri "a-ats.iot.us-east-1.amazonaws.com" 또는 "ssl://host:8883"
     * @param clientId        forklift_{wh}-{line}-{unit}-{role}-{instance}
     */
    fun init(context: Context, brokerHostOrUri: String, clientId: String) {
        if (initialized || connecting) {
            Log.d(TAG, "⏩ init 무시 (initialized=$initialized, connecting=$connecting)")
            return
        }
        connecting = true
        initialized = true

        val serverUri = if (brokerHostOrUri.startsWith("tcp://") || brokerHostOrUri.startsWith("ssl://")) {
            brokerHostOrUri
        } else {
            "ssl://$brokerHostOrUri:$DEFAULT_PORT"
        }
        Log.d(TAG, "🔥 init: $serverUri, clientId=$clientId")

        // Ack.AUTO_ACK 권장 (수신 콜백 안정성)
        client = MqttAndroidClient(context.applicationContext, serverUri, clientId, Ack.AUTO_ACK).apply {
            setCallback(internalCallback)
        }

        // mTLS 옵션
        val opts = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            keepAliveInterval = 30
            connectionTimeout = 10
            maxInflight = 20
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            socketFactory = TabletConfig.sslSocketFactory(context)
        }

        client.connect(opts, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                connecting = false
                Log.i(TAG, "✅ connect() onSuccess")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                connecting = false
                connected = false
                Log.e(TAG, "❌ connect() 실패: ${exception?.message}", exception)

                if (exception is MqttException &&
                    exception.reasonCode == MqttException.REASON_CODE_CLIENT_CLOSED.toInt()) {
                    Log.w(TAG, "🧯 CLIENT_CLOSED 감지 → 클라이언트 재생성 후 재시도")
                    safeRecreateAndReconnect(context, serverUri, clientId, opts)
                }
            }
        })
    }

    /** JSON 문자열/바이너리 모두 가능 */
    fun publish(topic: String, payload: String, qos: Int = 0, retain: Boolean = false) {
        if (!isConnected()) {
            Log.e(TAG, "❌ publish 불가(미연결): [$topic] $payload")
            return
        }
        try {
            val msg = MqttMessage(payload.toByteArray()).apply {
                this.qos = qos
                isRetained = retain
            }
            client.publish(topic, msg, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "📤 PUB 성공 [$topic] (qos=$qos, retain=$retain)")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ PUB 실패 [$topic]: ${exception?.message}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ publish 예외 [$topic]: ${e.message}", e)
        }
    }

    /** MQTT 필터 구독(+, # 지원). 재연결 시 자동 재구독. */
    fun subscribe(filter: String, qos: Int = 1, callback: (String, String) -> Unit) {
        synchronized(subscriptions) {
            val idx = subscriptions.indexOfFirst { it.filter == filter }
            if (idx >= 0) subscriptions[idx] = Sub(filter, qos, callback)
            else subscriptions += Sub(filter, qos, callback)
        }

        if (!isConnected()) {
            Log.w(TAG, "⏳ 미연결 상태. 연결 후 자동 재구독 예정 [$filter]")
            return
        }

        try {
            client.subscribe(filter, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "✅ 구독 성공 [$filter] qos=$qos")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ 구독 실패 [$filter]: ${exception?.message}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ subscribe 예외 [$filter]: ${e.message}", e)
        }
    }

    fun unsubscribe(filter: String) {
        synchronized(subscriptions) {
            subscriptions.removeAll { it.filter == filter }
        }
        if (!isConnected()) return
        try {
            client.unsubscribe(filter, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "🧹 구독 해제 성공 [$filter]")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ 구독 해제 실패 [$filter]: ${exception?.message}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ unsubscribe 예외 [$filter]: ${e.message}", e)
        }
    }

    /** 화면 전환/종료 시 disconnect 만 호출. 완전 종료 시 close 도 가능. */
    fun disconnect() {
        try {
            if (::client.isInitialized) {
                if (client.isConnected) client.disconnect()
                Log.i(TAG, "🔌 MQTT disconnect 요청 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ disconnect 실패: ${e.message}", e)
        } finally {
            connected = false
            initialized = false
            connecting = false
        }
    }

    fun close() {
        try {
            if (::client.isInitialized) {
                client.unregisterResources()
                client.close()
                Log.i(TAG, "🔒 MQTT client 자원 해제 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ close 실패: ${e.message}", e)
        } finally {
            connected = false
            initialized = false
            connecting = false
        }
    }

    // ─────────────────── 내부 유틸 ───────────────────

    private fun resubscribeAll() {
        val copy = synchronized(subscriptions) { subscriptions.toList() }
        copy.forEach { sub ->
            try {
                client.subscribe(sub.filter, sub.qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "🔁 재구독 성공 [${sub.filter}] qos=${sub.qos}")
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "❌ 재구독 실패 [${sub.filter}]: ${exception?.message}", exception)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "❌ 재구독 예외 [${sub.filter}]: ${e.message}", e)
            }
        }
    }

    /** MQTT 필터(+/#)와 토픽 매칭 */
    private fun matches(filter: String, topic: String): Boolean {
        if (filter == topic) return true
        val f = filter.split('/')
        val t = topic.split('/')
        var i = 0
        while (i < f.size) {
            val fs = f[i]
            if (fs == "#") return true
            if (fs == "+") {
                if (i >= t.size) return false
            } else {
                if (i >= t.size || fs != t[i]) return false
            }
            i++
        }
        return i == t.size
    }

    private fun findFirstMatch(topic: String): Sub? {
        val copy = synchronized(subscriptions) { subscriptions.toList() }
        return copy.firstOrNull { matches(it.filter, topic) }
    }

    private fun startPing() {
        if (disablePing) return
        main.postDelayed(object : Runnable {
            override fun run() {
                if (isConnected()) {
                    try {
                        client.publish(
                            DEFAULT_PING_TOPIC,
                            MqttMessage("alive".toByteArray()).apply { qos = 0; isRetained = false }
                        )
                    } catch (_: Exception) { /* ignore */ }
                    main.postDelayed(this, 15_000)
                }
            }
        }, 15_000)
    }

    private fun safeRecreateAndReconnect(context: Context, serverUri: String, clientId: String, opts: MqttConnectOptions) {
        try {
            if (::client.isInitialized) {
                try { client.unregisterResources() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
            client = MqttAndroidClient(context.applicationContext, serverUri, clientId, Ack.AUTO_ACK).apply {
                setCallback(internalCallback)
            }
            client.connect(opts, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "✅ 재생성 후 connect() 성공")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "❌ 재생성 후 connect() 실패: ${exception?.message}", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ safeRecreateAndReconnect 예외: ${e.message}", e)
        }
    }
}

/** mTLS SSLSocketFactory 생성기 (raw/ 의 PEM + PKCS#8 키 사용) */
object TabletConfig {
    private const val TAG = "TabletConfig"
    private var cached: SSLSocketFactory? = null

    fun sslSocketFactory(ctx: Context): SSLSocketFactory {
        cached?.let { return it }
        try {
            Log.d(TAG, "🔐 load CA/device cert/private key from raw")

            // 1) TrustStore (AmazonRootCA1)
            val caCert = ctx.resources.openRawResource(R.raw.amazon_root_ca1).use { it.readX509() }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            trustStore.setCertificateEntry("AmazonRootCA1", caCert)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }

            // 2) Device Cert
            val devCert = ctx.resources.openRawResource(R.raw.device_pem_crt).use { it.readX509() }

            // 3) Private Key (PKCS#8, PEM)
            val keyText = ctx.resources.openRawResource(R.raw.private_pkcs8).bufferedReader().use { it.readText() }
            Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            val parser = org.bouncycastle.openssl.PEMParser(keyText.reader())
            val obj = parser.use { it.readObject() }
            val conv = org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().setProvider("BC")
            val privateKey = when (obj) {
                is org.bouncycastle.openssl.PEMKeyPair -> conv.getKeyPair(obj).private
                is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> conv.getPrivateKey(obj)
                else -> throw IllegalArgumentException("Unsupported key format: ${obj?.javaClass}")
            }

            // 4) KeyStore (device key + cert)
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            keyStore.setKeyEntry("device", privateKey, CharArray(0), arrayOf(devCert))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, CharArray(0)) }

            // 5) TLS 1.2 컨텍스트
            val ssl = SSLContext.getInstance("TLSv1.2").apply { init(kmf.keyManagers, tmf.trustManagers, null) }
            Log.i(TAG, "✅ SSL factory ready (TLSv1.2)")
            return ssl.socketFactory.also { cached = it }
        } catch (e: Exception) {
            Log.e(TAG, "❌ SSL factory 실패: ${e.message}", e)
            throw e
        }
    }

    private fun InputStream.readX509(): X509Certificate {
        return CertificateFactory.getInstance("X.509").generateCertificate(this) as X509Certificate
    }

    // ── (옵션) 네이밍 규약 헬퍼 ──
    data class DeviceNaming(
        val warehouse: String = "wh01",
        val line: String = "A",
        val unit: String = "fl01",
        val role: String = "tb",
        val instance: String = "01"
    ) {
        val clientId = "forklift_${warehouse}-${line}-${unit}-${role}-${instance}"
        val base     = "fk/${warehouse}/${line}/${unit}"
        val cmd      = "$base/dev/cmd"
        val ack      = "$base/dev/ack"
        val progress = "$base/dev/progress"
        fun telemetry(inst: String = "01") = "$base/jet/$inst/telemetry"
        val appStatus = "$base/app/$role/$instance/status"
    }
}
