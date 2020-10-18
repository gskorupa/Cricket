/*
 * Copyright 2015 Grzegorz Skorupa <g.skorupa at gmail.com>.
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

import org.cricketmsf.event.Event;
import com.cedarsoftware.util.io.JsonWriter;
import com.sun.net.httpserver.Filter;
import org.cricketmsf.config.AdapterConfiguration;
import org.cricketmsf.config.ConfigSet;
import org.cricketmsf.config.Configuration;
import org.cricketmsf.in.InboundAdapter;
import org.cricketmsf.out.OutboundAdapter;
import static java.lang.Thread.MIN_PRIORITY;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.cricketmsf.config.HttpHeader;
import org.cricketmsf.event.EventMaster;
import org.cricketmsf.exception.DispatcherException;
import org.cricketmsf.exception.InitException;
import org.cricketmsf.exception.WebsocketException;
import org.cricketmsf.in.scheduler.SchedulerIface;
import org.cricketmsf.out.autostart.AutostartIface;
import org.cricketmsf.out.dispatcher.DispatcherIface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.cricketmsf.annotation.EventHook;

/**
 * Microkernel.
 *
 * @author Grzegorz Skorupa
 */
public abstract class Kernel {

    public final static int STARTING = 0;
    public final static int ONLINE = 1;
    public final static int MAINTENANCE = 2;
    public final static int SHUTDOWN = 3;

    // emergency LOGGER
    private static final Logger LOGGER = LoggerFactory.getLogger(Kernel.class);

    // event dispatcher
    protected DispatcherIface eventDispatcher = null;

    // singleton
    private static Kernel instance = null;

    private UUID uuid; //autogenerated when service starts - unpredictable
    private HashMap<String, String> portEventHookMethods = new HashMap<>();
    private String id; //identifying service 
    private String name; // name identifying service deployment (various names will have the same id)
    private String description;
    public boolean liftMode = false;

    // adapters
    public HashMap<String, Object> adaptersMap = new HashMap<>();
    private AutostartIface autostartAdapter = null;
    private SchedulerIface schedulerAdapter = null;

    // user defined properties
    public HashMap<String, Object> properties = new HashMap<>();
    public SimpleDateFormat dateFormat = null;

    // http server
    private String host = null;
    private int port = 0;
    private int websocketPort = 0;
    private String sslAlgorithm = "";
    private HttpdIface httpd;
    private boolean httpHandlerLoaded = false;
    private boolean inboundAdaptersLoaded = false;
    private WebsocketServer websocketServer;
    private int shutdownDelay = 5;

    private static AtomicLong eventSeed = new AtomicLong(System.currentTimeMillis());

    public String configurationBaseName = null;
    protected ConfigSet configSet = null;

    //private Filter securityFilter = new SecurityFilter();
    private Object securityFilter = null;
    private String securityFilterName = "";
    private ArrayList corsHeaders;

    private long startedAt = 0;
    private boolean started = false;
    private boolean initialized = false;
    private boolean fineLevel = true;
    private int status = STARTING;

    private CricketThreadFactory threadFactory;

    public Kernel() {
    }

    public CricketThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public boolean isStarted() {
        return started;
    }

    void setStartedAt(long time) {
        startedAt = time;
    }

    private void addHookMethodNameForPort(String key, String hookMethodName) {
        portEventHookMethods.put(key, hookMethodName);
    }

    private void getEventHooks() {
        String eventClass;
        String procedureName;
        LOGGER.info("REGISTERING EVENT HOOKS");
        // for every method of a Kernel instance (our service class extending Kernel)
        for (Method m : this.getClass().getMethods()) {
            EventHook[] portArray = m.getAnnotationsByType(EventHook.class);
            for (EventHook hook : portArray) {
                eventClass = hook.className();
                procedureName = hook.procedureName();
                addHookMethodNameForPort(procedureName + "@" + eventClass, m.getName());
                LOGGER.info("{}::{} => {}", eventClass, procedureName, m.getName());
            }
        }
        LOGGER.info("END REGISTERING EVENT HOOKS");
        LOGGER.info("");
    }

    private String getHookMethodNameForPort(String className, String procedureName) {
        return portEventHookMethods.get((null == procedureName ? "*" : procedureName) + "@" + className);
    }

    public Object getEventProcessingResult(Event event) {
        return getEventProcessingResult(event, event.getProcedureName());
    }

    public Object getEventProcessingResult(Event event, String procedureName) {
        String methodName = "unknown";
        String procedure = (null == procedureName ? "*" : procedureName);
        try {
            Method m;
            methodName = getHookMethodNameForPort(event.getClass().getName(), procedure);
            if (null != methodName && !methodName.isBlank()) {
                m = getClass().getMethod(methodName, event.getClass());
                return m.invoke(this, event);
            } else {
                LOGGER.warn("Don't know how to handle {} procedure {} fired by {}", event.getClass().getName(), procedureName, event.getOrigin().getName());
            }
        } catch (IllegalAccessException | NoSuchMethodException e) {
            LOGGER.warn("Handler method {} not compatible with event class {}", methodName, event.getClass().getName());
        } catch (InvocationTargetException e) {
            LOGGER.warn("Event class {} handler method {} exception {} {}", event.getClass().getName(), methodName, e.getCause().getClass(), e.getCause().getMessage());
            e.printStackTrace();
        } catch (NullPointerException e) {
            LOGGER.warn("Unable to find method {} for event class {}", methodName, event.getClass().getName());
        }
        return null;
    }

    /**
     * Invokes the service method annotated as dedicated to this event category
     *
     * @param event event object that should be processed
     * @return result of event processing
     */
    public static Object handle(Event event) {
        return Kernel.getInstance().getEventProcessingResult(event);
    }

    public Object dispatchEvent(Event event) {
        try {
            if (null != event.getTimePoint() && !event.getTimePoint().isEmpty() && null != schedulerAdapter) {
                schedulerAdapter.handleEvent(event);
            } else {
                eventDispatcher.dispatch(event);
            }
            return null;
        } catch (NullPointerException | DispatcherException ex) {
            return getEventProcessingResult(event);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public HashMap<String, Object> getAdaptersMap() {
        return adaptersMap;
    }

    protected Object getRegistered(String adapterName) {
        return adaptersMap.get(adapterName);
    }

    /**
     * Returns next unique identifier for Event.
     *
     * @return next unique identifier
     */
    public static long getEventId() {
        return eventSeed.getAndIncrement();
    }

    /**
     * Must be used to set adapter variables after instantiating them according
     * to the configuration in the configuration file (settings.json). Look at
     * EchoService example.
     */
    public abstract void getAdapters();

    public static Kernel getInstance() {
        return (Kernel) instance;
    }

    public static Kernel getInstanceWithProperties(Class c, Configuration config, ConfigSet defaultConfigSet) {
        if (instance != null) {
            return instance;
        }
        try {
            Configuration cfg = config;
            instance = (Kernel) c.newInstance();
            Configuration defaultCfg = null;
            if (null != instance.configurationBaseName) {
                defaultCfg = defaultConfigSet.getConfigurationById(instance.configurationBaseName);
                if (null != defaultCfg) {
                    cfg = cfg.join(defaultCfg);
                } else {
                    defaultConfigSet.getServices().forEach(cf -> {
                        System.out.println(cf.getId() + " " + cf.getService());
                    });
                }
            }
            System.out.println("DEFAULT CONFIG: " + instance.configurationBaseName + " with "
                    + (defaultCfg != null ? defaultCfg.getAdapters().size() : "unknown") + " adapters");
            ((Kernel) instance).setUuid(UUID.randomUUID());
            ((Kernel) instance).setId(config.getId());
            ((Kernel) instance).setDescription(config.getDescription());
            ((Kernel) instance).setProperties(cfg.getProperties());
            ((Kernel) instance).setName((String) cfg.getProperties().get("servicename"));
            ((Kernel) instance).setSsl((String) cfg.getProperties().getOrDefault("ssl", "false"));
            ((Kernel) instance).configureTimeFormat(cfg);
            ((Kernel) instance).loadAdapters(cfg);
        } catch (Exception e) {
            instance = null;
            LOGGER.info("{0}:{1}", new Object[]{e.getStackTrace()[0].toString(), e.getStackTrace()[1].toString()});
            e.printStackTrace();
            System.exit(1);
        }
        return instance;
    }

    private void configureTimeFormat(Configuration config) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        dateFormat.setTimeZone(TimeZone.getTimeZone(config.getProperty("time-zone", "GMT")));
    }

    private void printHeader(String version) {
        LOGGER.info("");
        LOGGER.info("  __|  \\  | __|  Cricket");
        LOGGER.info(" (    |\\/ | _|   Microservices Framework");
        LOGGER.info("\\___|_|  _|_|    version " + version);
        LOGGER.info("");
        // big text generated using http://patorjk.com/software/taag
    }

    private void printEventRegister() {
        LOGGER.info("");
        LOGGER.info("Event register:");
        //TODO: use annotations inside the Service class
        for (Map.Entry<String, String> entry : EventMaster.register.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) {
                LOGGER.info(entry.getKey());
            } else {
                LOGGER.info(entry.getKey() + " " + entry.getValue());
            }
        }
        LOGGER.info("");
    }

    /**
     * Instantiates adapters following configuration file (settings.json)
     *
     * @param config Configuration object loaded from the configuration file
     * @throws Exception
     */
    private synchronized void loadAdapters(Configuration config) throws Exception {
        threadFactory = new CricketThreadFactory("Kernel");
        setHttpHandlerLoaded(false);
        setInboundAdaptersLoaded(false);
        LOGGER.info("LOADING SERVICE PROPERTIES FOR " + config.getService());
        LOGGER.info("\tUUID=" + getUuid().toString());
        LOGGER.info("\tname=" + getName());
        setHost(config.getProperty("host", "0.0.0.0"));
        try {
            setPort(Integer.parseInt(config.getProperty("port", "8080")));
        } catch (Exception e) {
            LOGGER.debug(e.getMessage());
        }
        try {
            setWebsocketPort(Integer.parseInt(config.getProperty("wsport", "0")));
        } catch (Exception e) {
            LOGGER.debug(e.getMessage());
        }
        try {
            setShutdownDelay(Integer.parseInt(config.getProperty("shutdown-delay", "2")));
        } catch (Exception e) {
            LOGGER.debug(e.getMessage());
        }
        LOGGER.info("\tshutdown-delay=" + getShutdownDelay());
        setSecurityFilter(config.getProperty("filter"));
        setCorsHeaders(config.getProperty("cors"));;
        LOGGER.info("\tProperties:\n" + printExtendedProperties(getProperties()));
        LOGGER.info("LOADING ADAPTERS");
        String adapterName = null;
        AdapterConfiguration ac = null;
        try {
            HashMap<String, AdapterConfiguration> adcm = config.getAdapters();
            for (Map.Entry<String, AdapterConfiguration> adapterEntry : adcm.entrySet()) {
                adapterName = adapterEntry.getKey();
                ac = adapterEntry.getValue();
                LOGGER.info("ADAPTER: " + adapterName);
                try {
                    Class c = Class.forName(ac.getAdapterClassName());
                    if (Modifier.isAbstract(c.getModifiers())) {
                        LOGGER.error(adapterName + " adapter class is abstract: " + ac.getAdapterClassName());
                        continue;
                    }
                    adaptersMap.put(adapterName, c.getDeclaredConstructor().newInstance());
                    if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.http.HttpPortedAdapter) {
                        setHttpHandlerLoaded(true);
                    } else if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.websocket.WebsocketAdapter) {

                    } else if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.InboundAdapter) {
                        setInboundAdaptersLoaded(true);
                    }
                    // loading properties
                    java.lang.reflect.Method loadPropsMethod = c.getMethod("loadProperties", HashMap.class, String.class);
                    loadPropsMethod.invoke(adaptersMap.get(adapterName), ac.getProperties(), adapterName);
                    // defining API description
                    try {
                        java.lang.reflect.Method defApiMethod = c.getMethod("defineApi");
                        defApiMethod.invoke(adaptersMap.get(adapterName));
                    } catch (NoSuchMethodException e) {
                    }
                    try {
                        setEventDispatcher(((Adapter) adaptersMap.get(adapterName)).getDispatcher());
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        //e.printStackTrace();
                    }
                    if (adaptersMap.get(adapterName) instanceof org.cricketmsf.out.autostart.AutostartIface) {
                        setAutostartAdapter(adaptersMap.get(adapterName));
                    }
                    if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.scheduler.SchedulerIface) {
                        setSchedulerAdapter(adaptersMap.get(adapterName));
                    }
                } catch (NullPointerException | ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException ex) {
                    LOGGER.error(adapterName + " configuration: " + ex.getClass().getSimpleName());
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            LOGGER.info(String.format("Adapters initialization error. Configuration for {}: {}", adapterName, e.getMessage()));
        }
        LOGGER.info("event dispatcher: " + (eventDispatcher != null ? eventDispatcher.getName() + "(" + eventDispatcher.getClass().getName() + ")" : " not used"));
        LOGGER.info("END LOADING ADAPTERS");
        LOGGER.info("");

    }

    /**
     * Called for each adapter being loaded.
     *
     * @param adapter
     */
    private void setEventDispatcher(Object adapter) {
        if (null == adapter) {
            return;
        }
        // Scheduler can be used only if there is no other dispatcher configured
        if (null == eventDispatcher || eventDispatcher.getName().equals("Scheduler")) {
            eventDispatcher = (DispatcherIface) adapter;
        }
    }

    private void setSecurityFilter(String filterName) {
        if (null == filterName || filterName.isEmpty()) {
            securityFilterName = "org.cricketmsf.SecurityFilter";
        } else {
            securityFilterName = filterName;
        }
        try {
            Class c = Class.forName(securityFilterName);
            securityFilter = c.newInstance();
        } catch (ClassCastException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            System.out.println(e.getMessage());
            securityFilter = new SecurityFilter();
        }
    }

    public Filter getSecurityFilter() {
        if (null == securityFilter) {
            try {
                Class c = Class.forName(securityFilterName);
                securityFilter = (Filter) c.newInstance();
                securityFilter = c.newInstance();
            } catch (ClassCastException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                System.out.println(e.getMessage());
                //e.printStackTrace();
                securityFilter = new SecurityFilter();
            }
        }
        return (Filter) securityFilter;
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the httpd
     */
    private HttpdIface getHttpd() {
        return httpd;
    }

    /**
     * @param httpd the httpd to set
     */
    private void setHttpd(HttpdIface httpd) {
        this.httpd = httpd;
    }

    /**
     * @return the httpHandlerLoaded
     */
    private boolean isHttpHandlerLoaded() {
        return httpHandlerLoaded;
    }

    /**
     * @param httpHandlerLoaded the httpHandlerLoaded to set
     */
    private void setHttpHandlerLoaded(boolean httpHandlerLoaded) {
        this.httpHandlerLoaded = httpHandlerLoaded;
    }

    /**
     * This method will be invoked when Kernel is executed without --run option
     */
    public void runOnce() {
        getEventHooks();
        getAdapters();
        setKeystores();
        LOGGER.info("Running initialization tasks");
        try {
            runInitTasks();
        } catch (InitException ex) {
            LOGGER.info("Initialization exception: " + ex.getMessage());
        }
        printEventRegister();

        long startedIn = System.currentTimeMillis() - startedAt;
        printHeader(Kernel.getInstance().configSet.getKernelVersion());
        if (liftMode) {
            LOGGER.info("# Service: " + getClass().getName());
        } else {
            LOGGER.info("# Service: " + getId());
        }
        LOGGER.info("# UUID: " + getUuid());
        LOGGER.info("# NAME: " + getName());
        LOGGER.info("# JAVA: " + System.getProperty("java.version"));
        LOGGER.info("#");
        LOGGER.info("# Started in " + startedIn + "ms. Press Ctrl-C to stop");
        shutdown();

    }

    private void setKeystores() {
        String keystore;
        String keystorePass;
        String truststore;
        String truststorePass;

        keystore = (String) getProperties().getOrDefault("keystore", "");
        keystorePass = (String) getProperties().get("keystore-password");
        truststore = (String) getProperties().getOrDefault("keystore", "");
        truststorePass = (String) getProperties().get("keystore-password");

        if (!keystore.isEmpty() && !keystorePass.isEmpty()) {
            System.setProperty("javax.net.ssl.keyStore", keystore);
            System.setProperty("javax.net.ssl.keyStorePassword", keystorePass);
        }
        if (!truststore.isEmpty() && !truststorePass.isEmpty()) {
            System.setProperty("javax.net.ssl.trustStore", truststore);
            System.setProperty("javax.net.ssl.trustStorePassword", truststorePass);
        }
    }

    /**
     * Starts the service instance
     *
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {
        getAdapters();
        getEventHooks();
        setKeystores();
        if (isHttpHandlerLoaded() || isInboundAdaptersLoaded()) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread("shutdown hook") {
                public void run() {
                    try {
                        shutdown();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                        //e.printStackTrace();
                    }
                }
            });

            LOGGER.info("Running initialization tasks");
            try {
                runInitTasks();
            } catch (InitException ex) {
                LOGGER.info("Initialization exception: " + ex.getMessage());
            }
            printEventRegister();

            LOGGER.info("Starting listeners ...");
            // run listeners for inbound adapters
            runListeners();

            LOGGER.info("Starting http listener ...");
            if (!"jetty".equalsIgnoreCase((String) getProperties().getOrDefault("httpd", ""))) {
                setHttpd(new CricketHttpd(this));
                getHttpd().run();
            }
            if (getWebsocketPort() > 0) {
                websocketServer = new WebsocketServer(this);
                try {
                    websocketServer.start();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    //e.printStackTrace();
                }
            }

            long startedIn = System.currentTimeMillis() - startedAt;
            printHeader(Kernel.getInstance().configSet.getKernelVersion());
            if (liftMode) {
                LOGGER.info("# Service: " + getClass().getName());
            } else {
                LOGGER.info("# Service: " + getId());
            }
            LOGGER.info("# UUID   : " + getUuid());
            LOGGER.info("# VERSION: " + Kernel.getInstance().configSet.getServiceVersion());
            LOGGER.info("# NAME   : " + getName());
            LOGGER.info("# JAVA   : " + System.getProperty("java.version"));
            LOGGER.info("#");
            if (!"jetty".equalsIgnoreCase((String) getProperties().getOrDefault("httpd", ""))) {
                if (getHttpd().isSsl()) {
                    LOGGER.info("# HTTPS server listening on port " + getPort());
                } else {
                    LOGGER.info("# HTTP server listening on port " + getPort());
                }
            }
            if (getWebsocketPort() > 0) {
                LOGGER.info("# Websocket server listening on port " + getWebsocketPort());
            } else {
                LOGGER.info("# Websocket server not listening (port not configured)");
            }

            LOGGER.info("#");
            LOGGER.info("# Started in " + startedIn + "ms. Press Ctrl-C to stop");
            LOGGER.info("");
            runFinalTasks();
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            started = true;
            setStatus(ONLINE);
        } else {
            LOGGER.error("Inbound ports not configured. Exiting ...");
            System.exit(MIN_PRIORITY);
        }
    }

    /**
     * Could be overriden in a service implementation to run required code at
     * the service start. As the last step of the service starting procedure
     * before HTTP service.
     */
    protected void runInitTasks() throws InitException {
        if (null != getAutostartAdapter()) {
            getAutostartAdapter().execute();
        }
        setInitialized(true);
    }

    /**
     * Could be overriden in a service implementation to run required code at
     * the service start. As the last step of the service starting procedure
     * after HTTP service.
     */
    protected void runFinalTasks() {

    }

    protected void runListeners() {
        for (Map.Entry<String, Object> adapterEntry : getAdaptersMap().entrySet()) {
            if (adapterEntry.getValue() instanceof org.cricketmsf.in.InboundAdapter) {
                if (adapterEntry.getValue() instanceof org.cricketmsf.in.http.HttpPortedAdapter) {
                } else if (adapterEntry.getValue() instanceof org.cricketmsf.in.websocket.WebsocketAdapter) {
                } else {
                    getThreadFactory().newThread((InboundAdapter) adapterEntry.getValue(), adapterEntry.getKey()).start();
                    LOGGER.info(adapterEntry.getKey() + " (" + adapterEntry.getValue().getClass().getSimpleName() + ")");
                }
            }
        }
    }

    public void shutdown() {
        setStatus(SHUTDOWN);
        LOGGER.info("Shutting down ...");
        for (Map.Entry<String, Object> adapterEntry : getAdaptersMap().entrySet()) {
            if (adapterEntry.getValue() instanceof org.cricketmsf.in.InboundAdapter) {
                ((InboundAdapter) adapterEntry.getValue()).destroy();
            } else if (adapterEntry.getValue() instanceof org.cricketmsf.out.OutboundAdapter) {
                ((OutboundAdapter) adapterEntry.getValue()).destroy();
            }
        }
        try {
            getHttpd().stop();
        } catch (NullPointerException e) {
        }
        try {
            if (null != websocketServer) {
                websocketServer.stop();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        System.out.println("Kernel stopped\r\n");
    }

    /**
     * @return the configSet
     */
    public ConfigSet getConfigSet() {
        return configSet;
    }

    /**
     * @param configSet the configSet to set
     */
    public void setConfigSet(ConfigSet configSet) {
        this.configSet = configSet;
    }

    /**
     * Return service instance unique identifier
     *
     * @return the uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * @param uuid the uuid to set
     */
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    private void setId(String id) {
        this.id = id;
    }

    /**
     * @return the corsHeaders
     */
    public ArrayList getCorsHeaders() {
        return corsHeaders;
    }

    /**
     * @param corsHeaders the corsHeaders to set
     */
    public void setCorsHeaders(String corsHeaders) {
        String tmpCors = corsHeaders;
        if (null == tmpCors || tmpCors.isBlank()) {
            tmpCors = "Access-Control-Allow-Origin:*|Access-Control-Allow-Credentials:true|Access-Control-Allow-Methods: POST, GET, OPTIONS, DELETE, PUT|Access-Control-Allow-Headers: Authentication, Authorization, Origin, X-Requested-With, Content-Type, Accept, Accept-Language, Content-Language|Access-Control-Max-Age: 1728000";
        }
        this.corsHeaders = new ArrayList<>();
        String[] headers = tmpCors.split("\\|");
        for (String header : headers) {
            try {
                this.corsHeaders.add(
                        new HttpHeader(
                                header.substring(0, header.indexOf(":")).trim(),
                                header.substring(header.indexOf(":") + 1).trim()
                        )
                );
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * @return the properties
     */
    public HashMap<String, Object> getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(HashMap<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * @return the inboundAdaptersLoaded
     */
    public boolean isInboundAdaptersLoaded() {
        return inboundAdaptersLoaded;
    }

    /**
     * @param inboundAdaptersLoaded the inboundAdaptersLoaded to set
     */
    public void setInboundAdaptersLoaded(boolean inboundAdaptersLoaded) {
        this.inboundAdaptersLoaded = inboundAdaptersLoaded;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    public String getServiceVersion() {
        return getConfigSet().getServiceVersion();
    }

    /**
     * @param variableName system environment variable name which holds the name
     * of this service
     */
    public void setName(String variableName) {
        String tmp = null;
        try {
            tmp = System.getenv(variableName);
        } catch (Exception e) {
        }
        if (null == tmp || tmp.isBlank()) {
            tmp = (String) getProperties().getOrDefault("servicename", "");
        }
        if (null == tmp || tmp.isBlank()) {
            tmp = getId();
        }
        if (tmp.isBlank()) {
            this.name = "CricketService";
        } else {
            this.name = tmp;
        }
    }

    /**
     * Returns map of the current service properties along with list of statuses
     * reported by all registered (running) adapters
     *
     * @return status map
     */
    public Map reportStatus() {
        threadFactory.list();
        HashMap status = new HashMap();
        status.put("status", getStatusName());
        status.put("name", getName());
        status.put("id", getId());
        status.put("uuid", getUuid().toString());
        status.put("class", getClass().getName());
        status.put("totalMemory", Runtime.getRuntime().totalMemory());
        status.put("maxMemory", Runtime.getRuntime().maxMemory());
        status.put("freeMemory", Runtime.getRuntime().freeMemory());
        status.put("threads", Thread.activeCount());
        ArrayList adapters = new ArrayList();

        adaptersMap.keySet().forEach(key -> {
            try {
                adapters.add(
                        ((Adapter) adaptersMap.get(key)).getStatus(key));
            } catch (Exception e) {
                LOGGER.warn(key + " adapter is not registered");
            }
        });
        status.put("adapters", adapters);
        return status;
    }

    /**
     * Returns status map formated as JSON
     *
     * @return JSON representation of the statuses map
     */
    public String printStatus() {
        HashMap args = new HashMap();
        args.put(JsonWriter.PRETTY_PRINT, true);
        args.put(JsonWriter.DATE_FORMAT, "dd/MMM/yyyy:kk:mm:ss Z");
        args.put(JsonWriter.TYPE, false);
        return JsonWriter.objectToJson(reportStatus(), args);
    }

    public String printExtendedProperties(HashMap props) {
        HashMap args = new HashMap();
        args.put(JsonWriter.PRETTY_PRINT, true);
        args.put(JsonWriter.DATE_FORMAT, "dd/MMM/yyyy:kk:mm:ss Z");
        args.put(JsonWriter.TYPE, false);
        return JsonWriter.objectToJson(props, args);
    }

    private void getThreadsInfo() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;
        while ((parentGroup = rootGroup.getParent()) != null) {
            rootGroup = parentGroup;
        }
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * @param initialized the initialized to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * @return the shutdownDelay
     */
    public int getShutdownDelay() {
        return shutdownDelay;
    }

    /**
     * @param shutdownDelay the shutdownDelay to set
     */
    public void setShutdownDelay(int shutdownDelay) {
        this.shutdownDelay = shutdownDelay;
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    public String getStatusName() {
        switch (getStatus()) {
            case ONLINE:
                return "online";
            case STARTING:
                return "starting";
            case MAINTENANCE:
                return "maintenance";
            case SHUTDOWN:
                return "shutdown";
        }
        return "";
    }

    /**
     * @param status the status to set
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Algorithm value == "" or "false" mean SSL is not supported
     *
     * @return the sslAlgorithm
     */
    public String getSslAlgorithm() {
        return sslAlgorithm;
    }

    /**
     * Sets SSL algorithm based on environment variable name.
     *
     * @param variableName environment variable to use
     */
    public void setSsl(String variableName) {
        String tmp = null;
        try {
            tmp = System.getenv(variableName);
        } catch (Exception e) {
        }
        if (null != tmp && !tmp.isEmpty()) {
            this.sslAlgorithm = tmp;
        } else {
            this.sslAlgorithm = (String) getProperties().getOrDefault("ssl", "false");
        }
    }

    /**
     * @return the fineLevel
     */
    public boolean isFineLevel() {
        return fineLevel;
    }

    /**
     * @param fineLevel the fineLevel to set
     */
    public void setFineLevel(boolean fineLevel) {
        this.fineLevel = fineLevel;
    }

    /**
     * @return the websocketPort
     */
    public int getWebsocketPort() {
        return websocketPort;
    }

    /**
     * @param websocketPort the websocketPort to set
     */
    public void setWebsocketPort(int websocketPort) {
        this.websocketPort = websocketPort;
    }

    public void sendWebsocketMessage(String context, String message) throws WebsocketException {
        websocketServer.sendMessage(context, message);
    }

    public Locale getServiceLocale() {
        return Locale.forLanguageTag((String) getProperties().getOrDefault("locale", "en-US"));
    }

    /**
     * @return the autostartAdapter
     */
    public AutostartIface getAutostartAdapter() {
        return autostartAdapter;
    }

    /**
     * @param autostartAdapter the autostartAdapter to set
     */
    public void setAutostartAdapter(Object autostartAdapter) {
        this.autostartAdapter = (AutostartIface) autostartAdapter;
    }

    /**
     *
     * @param schedulerAdapter
     */
    public void setSchedulerAdapter(Object schedulerAdapter) {
        this.schedulerAdapter = (SchedulerIface) schedulerAdapter;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

}
