#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// WiFi
const char* WIFI_SSID = "iPhone 13 pro";
const char* WIFI_PW   = "j7005425";

// ===== AWS IoT =====
const char* AWS_ENDPOINT = "a15xw0pdafxycc-ats.iot.us-east-1.amazonaws.com";
const uint16_t AWS_PORT  = 8883;

// ===== Naming / Topics =====
const char* CLIENT_ID      = "forklift_wh01-A-fl01-esp-01";

const char* TOPIC_CMD       = "fk/wh01/A/fl01/dev/cmd";         // 앱 → (AWS) → ESP → 젯슨
const char* TOPIC_ACK       = "fk/wh01/A/fl01/dev/ack";         // 젯슨 → ESP → AWS
const char* TOPIC_PROGRESS  = "fk/wh01/A/fl01/dev/progress";    // 젯슨 → ESP → AWS
const char* TOPIC_TELEMETRY = "fk/wh01/A/fl01/jet/01/telemetry";// 젯슨 → ESP → AWS

// 인증서
const char* certificate_pem_crt = \
"-----BEGIN CERTIFICATE-----\n" \
"MIIDWTCCAkGgAwIBAgIUZQLHrfVCQ0pAwk0da9A1lbbcP+kwDQYJKoZIhvcNAQEL\n" \
"BQAwTTFLMEkGA1UECwxCQW1hem9uIFdlYiBTZXJ2aWNlcyBPPUFtYXpvbi5jb20g\n" \
"SW5jLiBMPVNlYXR0bGUgU1Q9V2FzaGluZ3RvbiBDPVVTMB4XDTI1MDYzMDAzMTQw\n" \
"NVoXDTQ5MTIzMTIzNTk1OVowHjEcMBoGA1UEAwwTQVdTIElvVCBDZXJ0aWZpY2F0\n" \
"ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOLW/xRq9xw/pZDHBFgd\n" \
"ffjOOHjDCI7paN/D5e0i29VjeP8D5diMvDcnndl8i27tu+/lLfu3powzdec6r874\n" \
"JqN+k/USmO1R4eqXSWU4A3AXrCWtcTKX4raI+WD9kXMOBUY1iCcWbrx3P/G2xiny\n" \
"4dudF3vCjT9Z8zKrq0V/vTBaqut4uczaW3v1RCkWnQTiaj1BvBmnmkqJ0bP/HwRG\n" \
"gAw3rIrAqymNki31+0ICTF2RjryAobUpsRO+EVFLXh2iG9ig8HcKmSleMj69gI/K\n" \
"fq/An9A4XxPUYUyGzrvK1FLwMrPQte3mE116RqhAUx0hKz3Ma+mvqWtdcWWKhkgi\n" \
"AoUCAwEAAaNgMF4wHwYDVR0jBBgwFoAUPwpHH+2rv8I6sjJJq+UG7+Or4iwwHQYD\n" \
"VR0OBBYEFGNocQMqlQHGb+UCKhcY688xa/V8MAwGA1UdEwEB/wQCMAAwDgYDVR0P\n" \
"AQH/BAQDAgeAMA0GCSqGSIb3DQEBCwUAA4IBAQCqL7K75kvHw5fBl7nPOh26kU+9\n" \
"3EokPY5PKXxUsRnuhpKfx282Nr+gKiJxTLBEFAF6YbTFEv71B0lHyT6UuQkXqcKc\n" \
"cT2r6uhkX/g9GPmbKJ0aAtR6LlP7UOewdOtk4jvsUECyOAicVbmoLIfhVudEmgom\n" \
"qecYqek2jcZAutRCh55fjy9bIZT6EGshnaQMxRNnDo5M38uY9e1KSuN30nW8ea5r\n" \
"4N2Jmb2XIaPAbkmf6qOUeVu4yGtM1Dx+T3Sibx4OpnHQuGGGGF/MngGznrNjm0h5\n" \
"TxsvcEcSsSroRWfbAYuRdlxnTN/DE5lcisRigx5YD9gKeu7jDsxNdt5Wt6BN\n" \
"-----END CERTIFICATE-----\n" ;

const char* private_pem_key = \
"-----BEGIN RSA PRIVATE KEY-----\n" \
"MIIEpAIBAAKCAQEA4tb/FGr3HD+lkMcEWB19+M44eMMIjulo38Pl7SLb1WN4/wPl\n" \
"2Iy8Nyed2XyLbu277+Ut+7emjDN15zqvzvgmo36T9RKY7VHh6pdJZTgDcBesJa1x\n" \
"Mpfitoj5YP2Rcw4FRjWIJxZuvHc/8bbGKfLh250Xe8KNP1nzMqurRX+9MFqq63i5\n" \
"zNpbe/VEKRadBOJqPUG8GaeaSonRs/8fBEaADDesisCrKY2SLfX7QgJMXZGOvICh\n" \
"tSmxE74RUUteHaIb2KDwdwqZKV4yPr2Aj8p+r8Cf0DhfE9RhTIbOu8rUUvAys9C1\n" \
"7eYTXXpGqEBTHSErPcxr6a+pa11xZYqGSCIChQIDAQABAoIBABR70aDoX9QfMzY1\n" \
"ooDKePuks5jEE9vD3AKI0BRv12YXi2+LzI+Xdc+NNkXTZ0vnD9eD3HkkhleWtgjh\n" \
"VcrcwbB116qDuWeEw1/JtYj8W+MdYCAu30/wQzq0d8B4ZU/8biF+6AKPlgRSyGVt\n" \
"Wcuj8R1BvEMiuFqGJt3mfprSBCV/fvEdg1kFtbmHUgCfl0ATecVwrH28hMzhFhkp\n" \
"UyZpVQob6pE2RPvHFLs2uiFzX/k4ShfKH+u3ZO5YY0s18Kt8XPaYU94iNNMnTYn5\n" \
"9JfrCy7zz359XExdvhZL3pWFDc5AYR3ztKKNIp0ThBtyd0uqGoN1rItEhjpaXjMu\n" \
"3Ctp7+kCgYEA+FgnrgH1EXNC9KVeLZqS/KSqAMV4wyvEYr3QKLJqiwS1KAlflT0x\n" \
"SH0LN/rLzdw5y7NcPODGw3HivAma4O+4qT47bHKlePm4IW3e/1UQ+hpa6Ho2rGXf\n" \
"spSq2WZmHmCnq+E7vp+nCQzBxcYkHL8qOieEwIMFK3gOdTg/sZ38uosCgYEA6dUi\n" \
"p1MctqTmETnEYT25KCu8lyXEUPXarIzVDxMdTN3w4eS/5f3VeW9cjzAicopUPpOk\n" \
"9//u+Bym65NAMl46wMWZO2kYmrhWhx0yqPIRMnr3j/VU5Fnyf/9/AJtmeGCVNPbi\n" \
"BrG/z9JQznI4eIvB5mDDCnDqRqEBnbifjr+CqS8CgYEAn9X9E91moWiRu2uDhc5q\n" \
"s/g3AnAWHWdZ6kNIaikKsZbCEZaW0jKkOYdhZRIhgckv2Y8C2pvA0aHG0EdOS19+\n" \
"dk77Z/0Ryx7OB5XzZFqXpqC3ydB+x855fzJ4E5v4Naz3vYQlM7cFhqmTPXsdWvMe\n" \
"dndg1ZZ+CVAn3gHp3KNWOakCgYEA2LloovkPVeFEM5b9Aru4jxjqdJTWfctA4Eiv\n" \
"eIdY0tOp3VSs37gCUcj7WuNcKhk3t8AUWJ9nl6LGuvUBvS1E+0KpoTzpRw2vdQ+E\n" \
"0r15XvZF2bGi+vq7HhNqXN+re8rYv/xKxbWdeDsQFigbWu79yk7ItzasjMXRv6NM\n" \
"j3vE2JUCgYBLtC38Q68jOBkU0Gshu+XxH6Js9LkC40JOPTouPMDCPGb5Pv9qPLiH\n" \
"HgQ/SgKz4x+c3B6r84wTF+GmYSXzTOXxGJGyhrwSsJH27G5XRJScu7niB/BhWohH\n" \
"dDQAsQ3T4UBtUdTE3dGZBKKTP536ZqMfTKPmWF2yOMV9sR3iLT+YLQ==\n" \
"-----END RSA PRIVATE KEY-----\n" ;


const char* amazon_root_ca = \
"-----BEGIN CERTIFICATE-----\n" \
"MIIDQTCCAimgAwIBAgITBmyfz5m/jAo54vB4ikPmljZbyjANBgkqhkiG9w0BAQsF\n" \
"ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" \
"b24gUm9vdCBDQSAxMB4XDTE1MDUyNjAwMDAwMFoXDTM4MDExNzAwMDAwMFowOTEL\n" \
"MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv\n" \
"b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj\n" \
"ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM\n" \
"9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw\n" \
"IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6\n" \
"VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L\n" \
"93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm\n" \
"jgSubJrIqg0CAwEAAaNCMEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC\n" \
"AYYwHQYDVR0OBBYEFIQYzIU07LwMlJQuCFmcx7IQTgoIMA0GCSqGSIb3DQEBCwUA\n" \
"A4IBAQCY8jdaQZChGsV2USggNiMOruYou6r4lK5IpDB/G/wkjUu0yKGX9rbxenDI\n" \
"U5PMCCjjmCXPI6T53iHTfIUJrU6adTrCC2qJeHZERxhlbI1Bjjt/msv0tadQ1wUs\n" \
"N+gDS63pYaACbvXy8MWy7Vu33PqUXHeeE6V/Uq2V8viTO96LXFvKWlJbYK8U90vv\n" \
"o/ufQJVtMVT8QtPHRh8jrdkPSHCa2XV4cdFyQzR1bldZwgJcJmApzyMZFo6IQ6XU\n" \
"5MsI+yMRQ+hDKXJioaldXgjUkK642M4UwtBV8ob2xJNDd2ZhwLnoQdeXeGADbkpy\n" \
"rqXRfboQnoZsG4q5WTP468SQvvG5\n" \
"-----END CERTIFICATE-----\n" ;

// ===== MQTT / TLS =====
WiFiClientSecure net;
PubSubClient mqtt(net);

/* ===== Pose State (Jetson → Serial in) =====
float latestX = NAN, latestY = NAN;
unsigned long lastSent = 0;
const unsigned long INTERVAL_MS = 1000; // 1Hz
*/

// ====== UART ======
// USB 하나(Serial)만 사용
#define JETSON Serial
const uint32_t UART_BAUD = 115200;

// ====== Buffers ======
String lineBuf;                  // UART 라인 버퍼
static const size_t MAX_LINE = 900;

// ====== 상태 캐시(선택) ======
float latestX = NAN, latestY = NAN, latestHeading = NAN;

// ===== Utils =====
void ensureWifi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.print("📶 WiFi connecting");
  WiFi.mode(WIFI_STA);
  WiFi.persistent(false);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PW);
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries++ < 60) {
    Serial.print(".");
    delay(500);
  }
  Serial.println(WiFi.status() == WL_CONNECTED ? " ✅" : " ❌");
}

//----- MQTT Callback (다운링크) -----
//다운링크: 앱이 cmd 발행 → ESP 구독 → 그대로 Serial.println(payload) 로 젯슨에 전달
void onMqtt(char* topic, byte* payload, unsigned int len) {
  // ▼ 다운링크: CMD만 구독 → UART로 “그대로” 1줄 전송
  if (strcmp(topic, TOPIC_CMD)==0) {
    JETSON.write(payload, len);
    JETSON.write('\n'); // 개행으로 라인 구분
  }
}


void ensureMqtt() {
  if (mqtt.connected()) return;
  Serial.print("🔗 AWS connecting");

  net.setCACert(amazon_root_ca);
  net.setCertificate(certificate_pem_crt);
  net.setPrivateKey(private_pem_key);


  mqtt.setServer(AWS_ENDPOINT, AWS_PORT);
  mqtt.setKeepAlive(30);
  mqtt.setBufferSize(1024); // ★512B -> 1024B (payload 256B + 여유)
  mqtt.setCallback(onMqtt);

  int tries = 0;
  while (!mqtt.connected() && tries < 10) {
    mqtt.connect(CLIENT_ID);
    if (!mqtt.connected()) {
      delay(1200);
      tries++;
    }
  }
  if (mqtt.connected()) {
    mqtt.subscribe(TOPIC_CMD, 1); // QoS1 요청은 라이브러리 한계로 0로 처리될 수 있음
  }
}


// ---------- JSON 라우팅 & 발행 ----------
void publishTo(const char* topic, const String& s) {
  // PubSubClient는 QoS0만 (ACK 보장 필요시 다른 라이브러리 고려)
  mqtt.publish(topic, s.c_str(), false); // retain=false
}

void routeAndPublish(const String& jsonLine) {
  StaticJsonDocument<512> doc;
  if (deserializeJson(doc, jsonLine)) return;

  // 1) t 필드 우선
  const char* t = doc["t"] | nullptr;
  if (t) {
    if (!strcmp(t, "telemetry")) { publishTo(TOPIC_TELEMETRY, jsonLine); return; }
    if (!strcmp(t, "ack"))       { publishTo(TOPIC_ACK,       jsonLine); return; }
    if (!strcmp(t, "progress"))  { publishTo(TOPIC_PROGRESS,  jsonLine); return; }
    // t가 있으나 미지정 타입 → 드롭(또는 Telemetry로 폴백해도 됨)
    return;
  }

  // 2) t가 없으면 히유리스틱
  bool hasX = doc.containsKey("x");
  bool hasY = doc.containsKey("y");
  bool hasH = doc.containsKey("heading");

  if (hasX && hasY && hasH) {
    publishTo(TOPIC_TELEMETRY, jsonLine);
    return;
  }

  // 3) 최소 좌표만 온 경우(하위 호환) → heading 없더라도 telemetry로 보냄
  if (hasX && hasY) {
    publishTo(TOPIC_TELEMETRY, jsonLine);
    return;
  }

  // 그 외의 라인은 무시(명확한 타입 아님)
}

// ---------- 유틸: 텔레메트리 직접 발행(백업/하트비트용) ----------
void publishTelemetry(float x, float y, float heading, unsigned long tsMillis) {
  char payload[256];
  snprintf(payload, sizeof(payload),
           "{\"t\":\"telemetry\",\"x\":%.3f,\"y\":%.3f,\"heading\":%.2f,\"ts\":%lu}",
           x, y, heading, tsMillis);
  publishTo(TOPIC_TELEMETRY, String(payload));
  // Serial.println(String("📤 TX: ") + payload); // 필요 시 주석 해제
}

// ===== Setup / Loop =====
void setup() {
  Serial.begin(115200);     // ★ Jetson과도, 로그도 모두 이 포트 하나 사용
  delay(80);
  ensureWifi();
  ensureMqtt();
  Serial.print("🔔 Topic telemetry: ");
  Serial.println(TOPIC_TELEMETRY);
}

void loop() {
  // 0) 네트워킹 보장
  if (WiFi.status() != WL_CONNECTED) {
    if (mqtt.connected()) mqtt.disconnect();
    ensureWifi();
    delay(250);
    return;
  }
  if (!mqtt.connected()) {
    ensureMqtt();
    delay(250);
    return;
  }
  mqtt.loop();

  // 업링크: Jetson이 보내는 JSON 라인을 받아 라우팅
  while (JETSON.available()) {
    char c = (char)JETSON.read();
    if (c == '\n') {
      if (lineBuf.length() > 0) {
        String s = lineBuf;
        lineBuf = "";

        // 좌표 캐시(선택)
        StaticJsonDocument<256> doc;
        if (deserializeJson(doc, s) == DeserializationError::Ok) {
          if (doc.containsKey("x")) latestX = doc["x"].as<float>();
          if (doc.containsKey("y")) latestY = doc["y"].as<float>();
          if (doc.containsKey("heading")) latestHeading = doc["heading"].as<float>();
        }

        routeAndPublish(s);
      }
    } else if (c != '\r') {
      if (lineBuf.length() < MAX_LINE) lineBuf += c;
      else lineBuf = ""; // 오버플로 보호
    }
  }

  // (선택) 하트비트/백업 텔레메트리: 최신 좌표가 유효하면 N초마다 한 번
  // → 필요 시 활성화
  // static unsigned long lastBeat = 0;
  // unsigned long now = millis();
  // if (!isnan(latestX) && !isnan(latestY) && !isnan(latestHeading) && (now - lastBeat) > 3000) {
  //   lastBeat = now;
  //   publishTelemetry(latestX, latestY, latestHeading, now);
  // }

  delay(5);
}