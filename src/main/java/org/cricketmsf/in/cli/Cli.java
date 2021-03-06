/*
 * Copyright 2016 Grzegorz Skorupa .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cricketmsf.in.cli;

import java.io.Console;
import java.util.HashMap;
import org.cricketmsf.Adapter;
import org.cricketmsf.Kernel;
import org.cricketmsf.in.InboundAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author greg
 */
public class Cli extends InboundAdapter implements Adapter, CliIface {
    private static final Logger logger = LoggerFactory.getLogger(Cli.class);

    private int samplingInterval = 1000;
    Console c = System.console();
    private boolean started = false;
    private String command;

    /**
     * This method is executed while adapter is instantiated during the service
     * start. It's used to configure the adapter according to the configuration.
     *
     * @param properties map of properties readed from the configuration file
     * @param adapterName name of the adapter set in the configuration file (can
     * be different from the interface and class name.
     */
    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        //super.getServiceHooks(adapterName);
        setSamplingInterval(properties.getOrDefault("sampling-interval", "200"));
        logger.info("\tsampling-interval: " + samplingInterval);
        //super.registerEventCategory(categoryName, Event.class.getName());
    }

    @Override
    public void start() {
        started = true;
    }

    public void readCommand() {
        command = c.readLine("Enter command: ");
        CliEvent ev = new CliEvent(command);
        Kernel.getInstance().dispatchEvent(ev);
    }

    @Override
    public void run() {
        try {
            while (!started) {
                Thread.sleep(samplingInterval);
            }
        } catch (InterruptedException e) {
            logger.info("CLI interrupted");
        }
        if (started) {
            try {
                while (true) {
                    readCommand();
                    Thread.sleep(samplingInterval);
                }
            } catch (InterruptedException e) {
                logger.info("CLI interrupted");
            }
        }
    }

    /**
     * @param samplingInterval the samplingInterval to set
     */
    public void setSamplingInterval(String samplingInterval) {
        try {
            this.samplingInterval = Integer.parseInt(samplingInterval);
        } catch (NumberFormatException e) {
            logger.info(e.getMessage());
        }
    }

}
