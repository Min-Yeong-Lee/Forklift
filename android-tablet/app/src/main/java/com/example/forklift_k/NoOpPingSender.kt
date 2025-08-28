package com.example.forklift_k

import org.eclipse.paho.client.mqttv3.MqttPingSender
import org.eclipse.paho.client.mqttv3.internal.ClientComms

class NoOpPingSender : MqttPingSender {
    override fun init(comms: ClientComms?) {}
    override fun start() {}
    override fun stop() {}
    override fun schedule(delayInMilliseconds: Long) {}
}
