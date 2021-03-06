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
package org.cricketmsf;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import org.cricketmsf.config.ConfigSet;
import org.cricketmsf.config.Configuration;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runner class is used when running JAR distribution. The class parses the
 * command line arguments, reads config, then creates and runs the service
 * instance according to the configuration.
 *
 * @author greg
 */
public class Runner {

    private static final Logger logger = LoggerFactory.getLogger(Runner.class);

    public static Kernel getService(String[] args) {

        long runAt = System.currentTimeMillis();
        final Kernel service;
        final ConfigSet defaultConfigSet;
        final ConfigSet configSet;
        Runner runner = new Runner();

        ArgumentParser arguments = new ArgumentParser(args);
        if (arguments.isProblem()) {
            if (arguments.containsKey("error")) {
                System.out.println(arguments.get("error"));
            }
            System.out.println(runner.getHelp());
            System.exit(-1);
        }

        defaultConfigSet = runner.readDefaultConfig();
        configSet = runner.readConfig(arguments);

        Class serviceClass = null;
        String serviceId;
        String serviceName = null;
        Configuration configuration = null;
        if (arguments.containsKey("service")) {
            // if service name provided as command line option
            serviceId = (String) arguments.get("service");
        } else {
            // otherwise get first configured service
            serviceId = configSet.getDefault().getId();
        }

        configuration = configSet.getConfigurationById(serviceId);

        // if serviceName isn't configured print error and exit
        if (configuration == null) {
            logger.info("Configuration not found for id=" + serviceId);
            System.exit(-1);
        } else if (arguments.containsKey("lift")) {
            serviceName = (String) arguments.get("lift");
            logger.info("LIFT service " + serviceName);
        } else {
            serviceName = configuration.getService();
        }

        if (arguments.containsKey("help")) {
            System.out.println(runner.getHelp(serviceName));
            System.exit(0);
        }

        if (arguments.containsKey("print")) {
            System.out.println(runner.getConfigAsString(configSet));
            System.exit(0);
        }
        if (arguments.containsKey("export")) {
            runner.exportConfigs(configSet);
            System.exit(0);
        }

        try {
            serviceClass = Class.forName(serviceName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        logger.info("CRICKET RUNNER");
        try {
            service = Kernel.getInstanceWithProperties(serviceClass, configuration, defaultConfigSet);
            service.configSet = configSet;
            service.liftMode = arguments.containsKey("lift");
            if (arguments.containsKey("run")) {
                service.setStartedAt(runAt);
                service.start();
                return service;
            } else {
                //service.setScheduler(new Scheduler());
                //System.out.println("Executing runOnce method");
                service.setStartedAt(runAt);
                service.runOnce();
                service.shutdown();
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            getService(args);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Returns content of the default help file ("help.txt")
     *
     * @return help content
     */
    public String getHelp() {
        return getHelp(null);
    }

    /**
     * Returns content of the custom service help file (serviceName+"-help.txt")
     * Example: if your service class name is
     * org.cricketmsf.services.EchoService then help file is
     * EchoService-help.txt
     *
     * @param serviceName the service class simple name
     * @return help text
     */
    public String getHelp(String serviceName) {
        String content = "Help file not found";
        String helpFileName = "/help.txt";
        if (null != serviceName) {
            helpFileName = "/" + serviceName.substring(serviceName.lastIndexOf(".") + 1) + "-help.txt";
        }
        try {
            content = readHelpFile(helpFileName);
        } catch (Exception e) {
            try {
                content = readHelpFile("/help.txt");
            } catch (Exception x) {
                System.out.println(e.getMessage());
                //e.printStackTrace();
            }
        }
        return content;
    }

    private String readHelpFile(String fileName) throws Exception {
        String content = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(fileName)))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            content = out.toString();
        }
        return content;
    }

    public ConfigSet readDefaultConfig() {
        ConfigSet configSet = null;
        Map args = new HashMap();
        args.put(JsonReader.USE_MAPS, false);
        Map types = new HashMap();
        types.put("java.utils.HashMap", "adapters");
        types.put("java.utils.HashMap", "properties");
        args.put(JsonReader.TYPE_NAME_MAP, types);
        InputStream propertyFile = null;
        String propsName = "cricket.json";
        propertyFile = getClass().getClassLoader().getResourceAsStream(propsName);
        if (null == propertyFile) {
            return null;
        }
        String inputStreamString = new Scanner(propertyFile, "UTF-8").useDelimiter("\\A").next();
        configSet = (ConfigSet) JsonReader.jsonToJava(inputStreamString, args);
        configSet.setKernelVersion(getVersion());
        configSet.setServiceVersion(getServiceVersion());
        return configSet;
    }

    private String getVersion() {
        try {
            Enumeration<URL> resources = Runner.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    Manifest manifest = new Manifest(url.openStream());
                    Attributes attributes = manifest.getMainAttributes();
                    if ("org.cricketmsf.Runner".equals(attributes.getValue("Main-Class"))) {
                        return attributes.getValue("Cricket-Version");
                    }
                } catch (IOException ex) {
                    return "";
                }
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    private String getServiceVersion() {
        try {
            Enumeration<URL> resources = Runner.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    Manifest manifest = new Manifest(url.openStream());
                    Attributes attributes = manifest.getMainAttributes();
                    if ("org.cricketmsf.Runner".equals(attributes.getValue("Main-Class"))) {
                        return attributes.getValue("Implementation-Version");
                    }
                } catch (IOException ex) {
                    return "";
                }
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    public ConfigSet readConfig(ArgumentParser arguments) {
        ConfigSet configSet = null;
        Map args = new HashMap();
        args.put(JsonReader.USE_MAPS, false);
        Map types = new HashMap();
        types.put("java.utils.HashMap", "adapters");
        types.put("java.utils.HashMap", "properties");
        args.put(JsonReader.TYPE_NAME_MAP, types);
        InputStream propertyFile = null;
        boolean builtInConfig = true;
        if (null != arguments && arguments.containsKey("config")) {
            //Properties props;
            try {
                propertyFile = new FileInputStream(new File((String) arguments.get("config")));
                builtInConfig = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            String propsName = "settings.json";
            File file = getFile(propsName);
            if (null != file) {
                try {
                    propertyFile = new FileInputStream(file);
                    builtInConfig = false;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
            if (null == propertyFile) {
                propertyFile = getClass().getClassLoader().getResourceAsStream(propsName);
                if (null == propertyFile) {
                    propsName = "cricket.json";
                    propertyFile = getClass().getClassLoader().getResourceAsStream(propsName);
                }
            }
        }
        if (null == propertyFile) {
            return null;
        }
        String inputStreamString = new Scanner(propertyFile, "UTF-8").useDelimiter("\\A").next();
        configSet = (ConfigSet) JsonReader.jsonToJava(inputStreamString, args);
        configSet.setBuiltIn(builtInConfig);
        // read Kernel version
        configSet.setKernelVersion(getVersion());
        configSet.setServiceVersion(getServiceVersion());
        // force property changes based on command line --force param
        if (null != arguments && arguments.containsKey("force")) {
            ArrayList<String> forcedProps = (ArrayList) arguments.get("force");
            for (int i = 0; i < forcedProps.size(); i++) {
                configSet.forceProperty(forcedProps.get(i));
            }
        }
        configSet.joinProps();
        return configSet;
    }

    private File getFile(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            return null;
        } else {
            return new File(resource.getFile());
        }
    }

    public String getConfigAsString(ConfigSet c) {
        Map nameBlackList = new HashMap();
        ArrayList blackList = new ArrayList();
        blackList.add("host");
        blackList.add("port");
        blackList.add("threads");
        blackList.add("filter");
        blackList.add("cors");
        nameBlackList.put(Configuration.class, blackList);
        Map args = new HashMap();
        args.put(JsonWriter.PRETTY_PRINT, true);
        //args.put(JsonWriter., args)
        return JsonWriter.objectToJson(c, args);
    }

    public void exportConfigs(ConfigSet configSet) {
        String settings = getConfigAsString(configSet);
        InputStream loggingConfig;
        loggingConfig = getClass().getClassLoader().getResourceAsStream("logback.xml");

        Scanner scanner = new Scanner(loggingConfig, "UTF-8");
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNext()) {
            sb.append(scanner.nextLine()).append("\r\n");
        }
        String result = writeToFile("settings", ".json", settings);
        if (result.isEmpty()) {
            System.out.println("Unable to save service settings.");
        } else {
            System.out.println("Service settings saved to " + result);
        }
        result = writeToFile("logback", ".xml", sb.toString());
        if (result.isEmpty()) {
            System.out.println("Unable to save logging config.");
        } else {
            System.out.println("Logging config saved to " + result);
        }
    }

    private String writeToFile(String name, String ext, String content) {
        String fileName = name + ext;
        int pass = 0;
        boolean created = false;
        while (!created && pass <= 10) {
            if (pass > 0) {
                fileName = name + "_" + pass + ext;
            }
            try {
                File myObj = new File(fileName);
                if (myObj.createNewFile()) {
                    created = true;
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            pass++;
        }
        if (!created) {
            return "";
        }
        try {
            FileWriter myWriter = new FileWriter(fileName);
            myWriter.write(content);
            myWriter.close();
            return fileName;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return "";
        }
    }
}
