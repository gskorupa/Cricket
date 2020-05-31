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

import com.cedarsoftware.util.io.JsonWriter;
import org.cricketmsf.annotation.EventHook;
import com.sun.net.httpserver.Filter;
import org.cricketmsf.config.AdapterConfiguration;
import org.cricketmsf.config.ConfigSet;
import org.cricketmsf.config.Configuration;
import org.cricketmsf.in.InboundAdapter;
import org.cricketmsf.out.OutboundAdapter;
import java.util.logging.Logger;
import static java.lang.Thread.MIN_PRIORITY;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.cricketmsf.annotation.EventClassHook;
import org.cricketmsf.annotation.HttpAdapterHook;
import org.cricketmsf.config.HttpHeader;
import org.cricketmsf.event.EventDecorator;
import org.cricketmsf.event.EventMaster;
import org.cricketmsf.exception.DispatcherException;
import org.cricketmsf.exception.InitException;
import org.cricketmsf.exception.WebsocketException;
import org.cricketmsf.in.scheduler.SchedulerIface;
import org.cricketmsf.out.dispatcher.DispatcherIface;
import org.cricketmsf.out.log.LoggerAdapterIface;
import org.cricketmsf.out.log.StandardLogger;

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
    private static final Logger LOGGER = Logger.getLogger(org.cricketmsf.Kernel.class.getName());
    // standard logger
    protected static LoggerAdapterIface logger = new StandardLogger().getDefault();
    // event dispatcher
    protected DispatcherIface eventDispatcher = null;

    // singleton
    private static Kernel instance = null;

    private UUID uuid; //autogenerated when service starts - unpredictable
    private HashMap<String, String> eventHookMethods = new HashMap<>();
    private HashMap<String, String> primaryEventHookMethods = new HashMap<>();
    private String id; //identifying service 
    private String name; // name identifying service deployment (various names will have the same id)
    public boolean liftMode = false;

    // adapters
    public HashMap<String, Object> adaptersMap = new HashMap<>();

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

    //private static long eventSeed = System.currentTimeMillis();
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

    private void addHookMethodNameForEvent(String eventCategory, String hookMethodName) {
        eventHookMethods.put(eventCategory, hookMethodName);
    }

    private void addHookMethodNameForEvent(Class cls, String hookMethodName) {
        primaryEventHookMethods.put(cls.getName(), hookMethodName);
    }

    private void getEventHooks() {
        EventHook ah;
        HttpAdapterHook hah;
        String eventCategory;
        String eventClass;
        getLogger().print("REGISTERING EVENT HOOKS");
        // for every method of a Kernel instance (our service class extending Kernel)
        for (Method m : this.getClass().getMethods()) {
            EventClassHook[] classNameArray = m.getAnnotationsByType(EventClassHook.class);
            for (EventClassHook hook : classNameArray) {
                eventClass = hook.className();
                try {
                    addHookMethodNameForEvent(Class.forName(eventClass), m.getName());
                    getLogger().print(eventClass + " : " + m.getName());
                } catch (ClassNotFoundException ex) {
                    getLogger().print("ERROR: " + eventClass + " not found");
                }
            }
            EventHook[] categoryArray = m.getAnnotationsByType(EventHook.class);
            for (EventHook hook : categoryArray) {
                eventCategory = hook.eventCategory();
                addHookMethodNameForEvent(eventCategory, m.getName());
                getLogger().print("event category " + eventCategory + " : " + m.getName());
            }
            HttpAdapterHook[] adapterArray = m.getAnnotationsByType(HttpAdapterHook.class);
            for (HttpAdapterHook hook : adapterArray) {
                getLogger().print("http adapter " + hook.adapterName() + " " + hook.requestMethod() + " : " + m.getName());
            }
        }
        getLogger().print("END REGISTERING EVENT HOOKS");
    }

    private String getHookMethodNameForEvent(String eventCategory) {
        String result;
        result = eventHookMethods.get(eventCategory);
        if (null == result) {
            result = eventHookMethods.get("*");
        }
        return result;
    }

    private String getHookMethodNameForEvent(Class cls) {
        return primaryEventHookMethods.get(cls.getName());
    }

    /**
     * Invokes the service method annotated as dedicated to this event category
     *
     * @param event event object that should be processed
     */
    public Object getEventProcessingResult(Event event) {
        Object o = null;
        String methodName = "unknown";
        try {
            Method m;
            methodName = getHookMethodNameForEvent(event.getClass());
            //Method m = getClass().getMethod(methodName, Event.class);
            if (null == methodName) {
                methodName = getHookMethodNameForEvent(event.getCategory());
                m = getClass().getMethod(methodName, Event.class);
            } else {
                m = getClass().getMethod(methodName, event.getClass());
            }
            o = m.invoke(this, event);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            dispatchEvent(Event.logWarning(
                    "Handler method " + methodName + " not compatible with event class",
                    " " + event.getClass().getName()
            ));
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            dispatchEvent(Event.logWarning(
                    "Handler method " + methodName + " throwed exception",
                    " " + event.getClass().getName()
            ));
        } catch(NullPointerException e){
            e.printStackTrace();
            dispatchEvent(Event.logWarning(
                    "Unable to find handler method " + methodName,
                    " " + event.getClass().getName()
            ));
        }
        return o;
    }

    public Object getEventProcessingResult(EventDecorator event) {
        return Kernel.this.getEventProcessingResult((Event) event);
    }

    @Deprecated
    public Object handleEvent(Event event) {
        return Kernel.this.getEventProcessingResult(event);
    }

    @Deprecated
    public Object handleEvent(EventDecorator event) {
        return handle((Event) event);
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

    /**
     * Sends event object to the event queue using registered dispatcher
     * adapter. In case the dispatcher adapter is not registered or throws
     * exception, the Kernel.handle(event) method will be called.
     *
     * @param event the event object that should be send to the event queue
     * @return null if dispatcher adapter is registered, otherwise returns
     * result of the Kernel.handle(event) method
     */
    public Object dispatchEvent(Event event) {
        if (null != event.getTimePoint()) {
            try {
                ((SchedulerIface) getAdaptersMap().get("Scheduler")).handleEvent(event);
                return null;
            } catch (NullPointerException | ClassCastException e) {
                return getEventProcessingResult(event);
            }
        }
        try {
            eventDispatcher.dispatch(event);
            return null;
        } catch (NullPointerException | DispatcherException ex) {
            return getEventProcessingResult(event);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public Object dispatchEvent(EventDecorator event) {
        if (null != event.getOriginalEvent().getTimePoint()) {
            try {
                ((SchedulerIface) getAdaptersMap().get("Scheduler")).handleEvent(event);
                return null;
            } catch (NullPointerException | ClassCastException e) {
                return getEventProcessingResult(event);
            }
        }
        try {
            eventDispatcher.dispatch(event);
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
            ((Kernel) instance).setName((String) cfg.getProperties().getOrDefault("SRVC_NAME_ENV_VARIABLE", "CRICKET_NAME"));
            ((Kernel) instance).setProperties(cfg.getProperties());
            ((Kernel) instance).setSsl((String) cfg.getProperties().getOrDefault("SSL_ENV_VARIABLE", "CRICKET_SSL"));
            ((Kernel) instance).configureTimeFormat(cfg);
            ((Kernel) instance).loadAdapters(cfg);
        } catch (Exception e) {
            instance = null;
            LOGGER.log(Level.SEVERE, "{0}:{1}", new Object[]{e.getStackTrace()[0].toString(), e.getStackTrace()[1].toString()});
            e.printStackTrace();
        }
        return instance;
    }

    private void configureTimeFormat(Configuration config) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        dateFormat.setTimeZone(TimeZone.getTimeZone(config.getProperty("time-zone", "GMT")));
    }

    private void printHeader(String version) {
        getLogger().print("");
        getLogger().print("  __|  \\  | __|  Cricket");
        getLogger().print(" (    |\\/ | _|   Microservices Framework");
        getLogger().print("\\___|_|  _|_|    version " + version);
        getLogger().print("");
        // big text generated using http://patorjk.com/software/taag
    }

    private void printEventRegister() {
        getLogger().print("");
        getLogger().print("Event register:");
        //TODO: use annotations inside the Service class
        for (Map.Entry<String, String> entry : EventMaster.register.entrySet()) {
            if (entry.getKey().equals(entry.getValue())) {
                getLogger().print(entry.getKey());
            } else {
                getLogger().print(entry.getKey() + " " + entry.getValue());
            }
        }
        getLogger().print("");
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
        getLogger().print("LOADING SERVICE PROPERTIES FOR " + config.getService());
        getLogger().print("\tUUID=" + getUuid().toString());
        getLogger().print("\tenv name=" + getName());
        //setHost(config.getHost());
        getLogger().print("\thttpd=" + config.getProperty("httpd", ""));
        setHost(config.getProperty("host", "0.0.0.0"));
        getLogger().print("\thost=" + getHost());
        try {
            //setPort(Integer.parseInt(config.getPort()));
            setPort(Integer.parseInt(config.getProperty("port", "8080")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            setWebsocketPort(Integer.parseInt(config.getProperty("wsport", "0")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        getLogger().print("\tport=" + getPort());
        try {
            setShutdownDelay(Integer.parseInt(config.getProperty("shutdown-delay", "2")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        getLogger().print("\tshutdown-delay=" + getShutdownDelay());
        setSecurityFilter(config.getProperty("filter"));
        //if ("jetty".equalsIgnoreCase(config.getProperty("httpd", ""))) {
        //    getLogger().print("\tfilter=" + getJettySecurityFilter().getClass().getName());
        //} else {
            getLogger().print("\tfilter=" + getSecurityFilter().getClass().getName());
        //}
        setCorsHeaders(config.getProperty("cors"));
        getLogger().print("\tCORS=" + getCorsHeaders());
        getLogger().print("\tExtended properties: " + getProperties().toString());
        getLogger().print("LOADING ADAPTERS");
        String adapterName = null;
        AdapterConfiguration ac = null;
        try {
            HashMap<String, AdapterConfiguration> adcm = config.getAdapters();
            for (Map.Entry<String, AdapterConfiguration> adapterEntry : adcm.entrySet()) {
                adapterName = adapterEntry.getKey();
                ac = adapterEntry.getValue();
                getLogger().print("ADAPTER: " + adapterName);
                try {
                    //Class c = Class.forName(ac.getClassFullName());
                    Class c = Class.forName(ac.getAdapterClassName());
                    adaptersMap.put(adapterName, c.newInstance());
                    if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.http.HttpAdapter) {
                        setHttpHandlerLoaded(true);
                    } else if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.websocket.WebsocketAdapter) {
                        
                    } else if (adaptersMap.get(adapterName) instanceof org.cricketmsf.in.InboundAdapter) {
                        setInboundAdaptersLoaded(true);
                    }
                    // loading properties
                    java.lang.reflect.Method loadPropsMethod = c.getMethod("loadProperties", HashMap.class, String.class);
                    loadPropsMethod.invoke(adaptersMap.get(adapterName), ac.getProperties(), adapterName);
                    try{
                        setEventDispatcher(((Adapter) adaptersMap.get(adapterName)).getDispatcher());
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    if (adaptersMap.get(adapterName) instanceof org.cricketmsf.out.log.LoggerAdapterIface) {
                        setFineLevel(((LoggerAdapterIface) adaptersMap.get(adapterName)).isFineLevel());
                    }
                } catch (NullPointerException | ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException ex) {
                    adaptersMap.put(adapterName, null);
                    getLogger().print("ERROR: " + adapterName + " configuration: " + ex.getClass().getSimpleName());
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Adapters initialization error. Configuration for: {0}", adapterName);
            throw new Exception(e);
        }
        getLogger().print("event dispatcher: " + (eventDispatcher != null ? eventDispatcher.getName() + "(" + eventDispatcher.getClass().getName() + ")" : " not used"));
        getLogger().print("END LOADING ADAPTERS");
        getLogger().print("");

    }

    /**
     * Called for each adapter being loaded.
     *
     * @param adapter
     */
    private void setEventDispatcher(Object adapter) {
        if (adapter != null) {
            // Scheduler can be used only if there is no other dispatcher configured
            //if (null == eventDispatcher && !"org.cricketmsf.in.scheduler.Scheduler".equals(adapter.getClass().getName())) {
            if (null == eventDispatcher || eventDispatcher.getName().equals("Scheduler")) {
                eventDispatcher = (DispatcherIface) adapter;
            }
        }
    }

    private void setSecurityFilter(String filterName) {
        securityFilterName = filterName;
        try {
            Class c = Class.forName(filterName);
            //securityFilter = (Filter) c.newInstance();
            securityFilter = c.newInstance();
        } catch (ClassCastException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
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
                e.printStackTrace();
                securityFilter = new SecurityFilter();
            }
        }
        return (Filter) securityFilter;
    }

    /*
    public javax.servlet.Filter getJettySecurityFilter() {
        if (null == securityFilter) {
            try {
                Class c = Class.forName(securityFilterName);
                securityFilter = (javax.servlet.Filter) c.newInstance();
                securityFilter = c.newInstance();
            } catch (ClassCastException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
                securityFilter = new SecurityFilter();
            }
        }
        return (javax.servlet.Filter) securityFilter;
    }
*/
    
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
        printHeader(Kernel.getInstance().configSet.getKernelVersion());
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
                        e.printStackTrace();
                    }
                }
            });

            getLogger().print("Running initialization tasks");
            try {
                runInitTasks();
            } catch (InitException ex) {
                getLogger().print("Initialization exception: "+ex.getMessage());
            }
            printEventRegister();

            getLogger().print("Starting listeners ...");
            // run listeners for inbound adapters
            runListeners();

            getLogger().print("Starting http listener ...");
            if (!"jetty".equalsIgnoreCase((String) getProperties().getOrDefault("httpd", ""))) {
                setHttpd(new CricketHttpd(this));
                getHttpd().run();
            }
            if (getWebsocketPort() > 0) {
                websocketServer = new WebsocketServer(this);
                try{
                    websocketServer.start();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            
            long startedIn = System.currentTimeMillis() - startedAt;
            printHeader(Kernel.getInstance().configSet.getKernelVersion());
            if (liftMode) {
                getLogger().print("# Service: " + getClass().getName());
            } else {
                getLogger().print("# Service: " + getId());
            }
            getLogger().print("# UUID: " + getUuid());
            getLogger().print("# NAME: " + getName());
            getLogger().print("# JAVA: " + System.getProperty("java.version"));
            getLogger().print("#");
            if (!"jetty".equalsIgnoreCase((String) getProperties().getOrDefault("httpd", ""))) {
                if (getHttpd().isSsl()) {
                    getLogger().print("# HTTPS server listening on port " + getPort());
                } else {
                    getLogger().print("# HTTP server listening on port " + getPort());
                }
            }
            if (getWebsocketPort() > 0) {
                getLogger().print("# Websocket server listening on port " + getWebsocketPort());
            } else {
                getLogger().print("# Websocket server not listening (port not configured)");
            }
            
            getLogger().print("#");
            getLogger().print("# Started in " + startedIn + "ms. Press Ctrl-C to stop");
            getLogger().print("");
            runFinalTasks();
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            started = true;
            setStatus(ONLINE);
            /*
            if ("jetty".equalsIgnoreCase((String) getProperties().getOrDefault("httpd", ""))) {
                setHttpd(new JettyHttpd(this));
                getHttpd().run();
            }
            */
        } else {
            getLogger().print("Couldn't find any http request hook method. Exiting ...");
            System.exit(MIN_PRIORITY);
        }
    }

    /**
     * Could be overriden in a service implementation to run required code at
     * the service start. As the last step of the service starting procedure
     * before HTTP service.
     */
    protected void runInitTasks() throws InitException {
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
                if (adapterEntry.getValue() instanceof org.cricketmsf.in.http.HttpAdapter) {
                }else if(adapterEntry.getValue() instanceof org.cricketmsf.in.websocket.WebsocketAdapter){
                }else {
                    //(new Thread((InboundAdapter) adapterEntry.getValue(), adapterEntry.getKey())).start();
                    getThreadFactory().newThread((InboundAdapter) adapterEntry.getValue(), adapterEntry.getKey()).start();
                    getLogger().print(adapterEntry.getKey() + " (" + adapterEntry.getValue().getClass().getSimpleName() + ")");
                }
            }
        }
    }

    public void shutdown() {
        setStatus(SHUTDOWN);
        getLogger().print("Shutting down ...");
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
            e.printStackTrace();
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
        this.corsHeaders = new ArrayList<>();
        if (corsHeaders != null) {
            String[] headers = corsHeaders.split("\\|");
            for (String header : headers) {
                try {
                    this.corsHeaders.add(
                            new HttpHeader(
                                    header.substring(0, header.indexOf(":")).trim(),
                                    header.substring(header.indexOf(":") + 1).trim()
                            )
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
        this.name = tmp != null ? tmp : "" + getProperties().getOrDefault("servicename", "");
        if (this.name.isEmpty()) {
            this.name = "CricketService";
        }
    }

    /**
     * @return the logger
     */
    public static LoggerAdapterIface getLogger() {
        return logger;
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
                getInstance().dispatchEvent(Event.logFine(this, key + " adapter is not registered"));
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
    
    public void sendWebsocketMessage(String context, String message) throws WebsocketException{
        websocketServer.sendMessage(context, message);
    }

}
