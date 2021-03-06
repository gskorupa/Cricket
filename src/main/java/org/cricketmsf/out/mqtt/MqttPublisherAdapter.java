/*
 * Copyright 2017 Grzegorz Skorupa .
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.cricketmsf.out.mqtt;

import java.util.HashMap;
import java.util.Map;
import org.cricketmsf.Adapter;
import org.cricketmsf.Kernel;
import org.cricketmsf.out.OutboundAdapter;
import org.cricketmsf.out.OutboundAdapterIface;
import org.cricketmsf.out.dispatcher.MessageDispatcher;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Grzegorz Skorupa 
 */
public class MqttPublisherAdapter extends OutboundAdapter implements MqttPublisherIface, OutboundAdapterIface, Adapter {
    private static final Logger logger = LoggerFactory.getLogger(MqttPublisherAdapter.class);
    private String brokerURL;
    private int qos = 0;
    private String clientID;
    private boolean debug = false;

    private MemoryPersistence persistence = new MemoryPersistence();

    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        super.loadProperties(properties, adapterName);
        clientID = properties.getOrDefault("client-id", "CricketService");
        this.properties.put("client-id", clientID);
        logger.info("\tclient-id: " + clientID);
        brokerURL = properties.get("url");
        this.properties.put("url", brokerURL);
        logger.info("\turl: " + brokerURL);
        try {
            this.properties.put("qos", properties.getOrDefault("qos", "0"));
            qos = Integer.parseInt(this.properties.getOrDefault("qos", "0"));
            if (qos > 2) {
                qos = 2;
            }
        } catch (NumberFormatException e) {
        }
        logger.info("\tqos: " + qos);
        try {
            this.properties.put("debug", properties.getOrDefault("debug", "false"));
            debug = Boolean.parseBoolean(this.properties.getOrDefault("debug", "false"));
        } catch (NumberFormatException e) {
        }
        logger.info("\tdebug: " + debug);
    }


    @Override
    public void publish(String clientID, int qos, String topic, String payload) throws MqttPublisherException {
        MqttPublisher.publish(brokerURL, clientID, qos, debug, topic, payload);
    }

    @Override
    public void publish(int qos, String topic, String message) throws MqttPublisherException {
        publish(clientID, qos, topic, message);
    }

    @Override
    public void publish(String topic, String message) throws MqttPublisherException {
        publish(clientID, qos, topic, message);
    }

}
