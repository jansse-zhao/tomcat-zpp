/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.startup;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.security.SecurityConfig;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

/**
 * Startup/Shutdown shell program for Catalina.
 * The following command line options are recognized:
 * <ul>
 * <li><b>-config {pathname}</b> - Set the pathname of the configuration file
 *     to be processed.  If a relative path is specified, it will be
 *     interpreted as relative to the directory pathname specified by the
 *     "catalina.base" system property.   [conf/server.xml]</li>
 * <li><b>-help</b>      - Display usage information.</li>
 * <li><b>-nonaming</b>  - Disable naming support.</li>
 * <li><b>configtest</b> - Try to test the config</li>
 * <li><b>start</b>      - Start an instance of Catalina.</li>
 * <li><b>stop</b>       - Stop the currently running instance of Catalina.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class Catalina {

    private static final Log log = LogFactory.getLog(Catalina.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    public static final String SERVER_XML = "conf/server.xml";

    // ----------------------------------------------------- Instance Variables

    /**
     * Use await.
     */
    protected boolean await = false;

    /**
     * Pathname to the server configuration file.
     */
    protected String configFile = SERVER_XML;

    // XXX Should be moved to embedded
    /**
     * The shared extensions class loader for this server.
     */
    protected ClassLoader parentClassLoader = Catalina.class.getClassLoader();

    /**
     * The server component we are starting or stopping.
     */
    protected Server server = null;

    /**
     * Use shutdown hook flag.
     */
    protected boolean useShutdownHook = true;

    /**
     * Shutdown hook.
     */
    protected Thread shutdownHook = null;

    /**
     * Is naming enabled ?
     * 是否启用了命名功能?
     */
    protected boolean useNaming = true;

    /**
     * Prevent duplicate loads.
     */
    protected boolean loaded = false;

    /**
     * Rethrow exceptions on init failure.
     */
    protected boolean throwOnInitFailure = Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE");

    /**
     * Generate Tomcat embedded code from configuration files.
     * 从配置文件生成Tomcat嵌入代码。
     * 生成手动拼接的Java代码
     */
    protected boolean generateCode = false;

    /**
     * Location of generated sources.
     */
    protected File generatedCodeLocation = null;

    /**
     * Value of the argument.
     */
    protected String generatedCodeLocationParameter = null;

    /**
     * Top package name for generated source.
     * 生成源代码的顶级包名。
     */
    protected String generatedCodePackage = "catalinaembedded";

    /**
     * Use generated code as a replacement for configuration files.
     * 生成的代码作为配置文件的替代品。
     */
    protected boolean useGeneratedCode = false;

    // ----------------------------------------------------------- Constructors

    public Catalina() {
        setSecurityProtection();
        ExceptionUtils.preload();
    }

    // ------------------------------------------------------------- Properties

    public void setConfigFile(String file) {
        configFile = file;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }

    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }

    public boolean getGenerateCode() {
        return this.generateCode;
    }

    public void setGenerateCode(boolean generateCode) {
        this.generateCode = generateCode;
    }

    public boolean getUseGeneratedCode() {
        return this.useGeneratedCode;
    }

    public void setUseGeneratedCode(boolean useGeneratedCode) {
        this.useGeneratedCode = useGeneratedCode;
    }

    public File getGeneratedCodeLocation() {
        return this.generatedCodeLocation;
    }

    public void setGeneratedCodeLocation(File generatedCodeLocation) {
        this.generatedCodeLocation = generatedCodeLocation;
    }

    public String getGeneratedCodePackage() {
        return this.generatedCodePackage;
    }

    public void setGeneratedCodePackage(String generatedCodePackage) {
        this.generatedCodePackage = generatedCodePackage;
    }

    /**
     * @return <code>true</code> if an exception should be thrown if an error
     * occurs during server init
     */
    public boolean getThrowOnInitFailure() {
        return throwOnInitFailure;
    }

    /**
     * Set the behavior regarding errors that could occur during server init.
     *
     * @param throwOnInitFailure the new flag value
     */
    public void setThrowOnInitFailure(boolean throwOnInitFailure) {
        this.throwOnInitFailure = throwOnInitFailure;
    }

    /**
     * Set the shared extensions class loader.
     *
     * @param parentClassLoader The shared extensions class loader.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }

    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        return ClassLoader.getSystemClassLoader();
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Server getServer() {
        return server;
    }

    /**
     * @return <code>true</code> if naming is enabled.
     */
    public boolean isUseNaming() {
        return this.useNaming;
    }

    /**
     * Enables or disables naming support.
     *
     * @param useNaming The new use naming value
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }

    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * Process the specified command line arguments.
     *
     * @param args Command line arguments to process
     * @return <code>true</code> if we should continue processing
     */
    protected boolean arguments(String args[]) {
        boolean isConfig = false;
        boolean isGenerateCode = false;

        if (args.length < 1) {
            // 打印此应用程序的使用信息。
            usage();
            return false;
        }

        for (String arg : args) {
            if (isConfig) {
                configFile = arg;
                isConfig = false;
            }
            // -config命令
            else if (arg.equals("-config")) {
                isConfig = true;
            }
            // -generateCode命令
            else if (arg.equals("-generateCode")) {
                setGenerateCode(true);
                isGenerateCode = true;
            }
            // -useGeneratedCode命令
            else if (arg.equals("-useGeneratedCode")) {
                setUseGeneratedCode(true);
                isGenerateCode = false;
            }
            // -nonaming命令
            else if (arg.equals("-nonaming")) {
                setUseNaming(false);
                isGenerateCode = false;
            }
            // -help命令
            else if (arg.equals("-help")) {
                usage();
                return false;
            }
            // start命令
            else if (arg.equals("start")) {
                isGenerateCode = false;
                // NOOP
            }
            // configtest命令
            else if (arg.equals("configtest")) {
                isGenerateCode = false;
                // NOOP
            }
            // stop命令
            else if (arg.equals("stop")) {
                isGenerateCode = false;
                // NOOP
            } else if (isGenerateCode) {
                generatedCodeLocationParameter = arg;
                isGenerateCode = false;
            } else {
                usage();
                return false;
            }
        }

        return true;
    }

    /**
     * Return a File object representing our configuration file.
     *
     * @return the main configuration file
     */
    protected File configFile() {
        File file = new File(configFile);
        if (!file.isAbsolute()) {
            file = new File(Bootstrap.getCatalinaBase(), configFile);
        }
        return file;

    }

    /**
     * Create and configure the Digester we will be using for startup.
     * <p>
     * 创建并配置Digester，这个类用于解析xml文件，
     * 将里面的xml节点解析成对应的Server、Service、Engine、Listener、Connector等实例对象组件
     * 因为server.xml文件中配置的有Server、Listener、Service、Engine、Connector等节点
     *
     * @return the main digester to parse server.xml
     */
    protected Digester createStartDigester() {
        // Initialize the digester
        Digester digester = new Digester();
        // 设置验证解析器标志，不验证xml解析器
        digester.setValidating(false);
        // 设置规则验证标志
        digester.setRulesValidation(true);

        Map<Class<?>, List<String>> fakeAttributes = new HashMap<>();
        // Ignore className on all elements
        List<String> objectAttrs = new ArrayList<>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);

        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);

        // Ignore Connector attribute used internally but set on Server
        List<String> connectorAttrs = new ArrayList<>();
        connectorAttrs.add("portOffset");
        fakeAttributes.put(Connector.class, connectorAttrs);

        digester.setFakeAttributes(fakeAttributes);
        // 是否使用上下文类加载器
        digester.setUseContextClassLoader(true);

        /**
         * Configure the actions we will be using
         *
         * Digester对象的全局属性rules = new RulesBase();
         * 该对象中存在：
         * 1. HashMap<String, List<Rule>> cache = new HashMap<>();
         * 2. ArrayList<Rule> rules = new ArrayList<>();
         *
         * 在cache中存在【“Server”, [ObjectCreateRule, SetPropertiesRule, SetNextRule]】
         * rules中存在[ObjectCreateRule, SetPropertiesRule, SetNextRule]
         *
         * 在RulesBase中保存格式：
         *      cache.put("Server", list.add(new ObjectCreateRule("org.apache.catalina.core.StandardServer", "className")))
         *      rules.add(new ObjectCreateRule("org.apache.catalina.core.StandardServer", "className"))
         */
        digester.addObjectCreate("Server", "org.apache.catalina.core.StandardServer", "className");

        /**
         * 在RulesBase中保存格式：
         *      cache.put("Server", list.add(new SetPropertiesRule()))
         *      rules.add(new SetPropertiesRule())
         */
        digester.addSetProperties("Server");

        /**
         * 在RulesBase中保存格式：
         *      cache.put("Server", list.add(new SetNextRule("setServer", "org.apache.catalina.Server")))
         *      rules.add(new SetNextRule("setServer", "org.apache.catalina.Server"))
         */
        digester.addSetNext("Server", "setServer", "org.apache.catalina.Server");

        digester.addObjectCreate("Server/GlobalNamingResources", "org.apache.catalina.deploy.NamingResourcesImpl");
        digester.addSetProperties("Server/GlobalNamingResources");
        digester.addSetNext("Server/GlobalNamingResources", "setGlobalNamingResources", "org.apache.catalina.deploy.NamingResourcesImpl");

        digester.addRule("Server/Listener", new ListenerCreateRule(null, "className"));
        digester.addSetProperties("Server/Listener");
        digester.addSetNext("Server/Listener", "addLifecycleListener", "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service", "org.apache.catalina.core.StandardService", "className");
        digester.addSetProperties("Server/Service");
        digester.addSetNext("Server/Service", "addService", "org.apache.catalina.Service");

        digester.addObjectCreate("Server/Service/Listener", null, "className");
        digester.addSetProperties("Server/Service/Listener");
        digester.addSetNext("Server/Service/Listener", "addLifecycleListener", "org.apache.catalina.LifecycleListener");

        //Executor
        digester.addObjectCreate("Server/Service/Executor", "org.apache.catalina.core.StandardThreadExecutor", "className");
        digester.addSetProperties("Server/Service/Executor");
        digester.addSetNext("Server/Service/Executor", "addExecutor", "org.apache.catalina.Executor");

        digester.addRule("Server/Service/Connector", new ConnectorCreateRule());
        digester.addSetProperties("Server/Service/Connector", new String[]{"executor", "sslImplementationName", "protocol"});
        digester.addSetNext("Server/Service/Connector", "addConnector", "org.apache.catalina.connector.Connector");

        digester.addRule("Server/Service/Connector", new AddPortOffsetRule());

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig", "org.apache.tomcat.util.net.SSLHostConfig");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig", "addSslHostConfig", "org.apache.tomcat.util.net.SSLHostConfig");

        digester.addRule("Server/Service/Connector/SSLHostConfig/Certificate", new CertificateCreateRule());
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/Certificate", new String[]{"type"});
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/Certificate", "addCertificate", "org.apache.tomcat.util.net.SSLHostConfigCertificate");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf", "org.apache.tomcat.util.net.openssl.OpenSSLConf");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf", "setOpenSslConf", "org.apache.tomcat.util.net.openssl.OpenSSLConf");

        digester.addObjectCreate("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd", "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");
        digester.addSetProperties("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd");
        digester.addSetNext("Server/Service/Connector/SSLHostConfig/OpenSSLConf/OpenSSLConfCmd", "addCmd", "org.apache.tomcat.util.net.openssl.OpenSSLConfCmd");

        digester.addObjectCreate("Server/Service/Connector/Listener", null, "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener", "addLifecycleListener", "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service/Connector/UpgradeProtocol", null, "className");
        digester.addSetProperties("Server/Service/Connector/UpgradeProtocol");
        digester.addSetNext("Server/Service/Connector/UpgradeProtocol", "addUpgradeProtocol", "org.apache.coyote.UpgradeProtocol");

        // Add RuleSets for nested elements
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        addClusterRuleSet(digester, "Server/Service/Engine/Host/Cluster/");
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine", new SetParentClassLoaderRule(parentClassLoader));
        addClusterRuleSet(digester, "Server/Service/Engine/Cluster/");

        return digester;
    }

    /**
     * Cluster support is optional. The JARs may have been removed.
     */
    private void addClusterRuleSet(Digester digester, String prefix) {
        Class<?> clazz = null;
        Constructor<?> constructor = null;
        try {
            clazz = Class.forName("org.apache.catalina.ha.ClusterRuleSet");
            constructor = clazz.getConstructor(String.class);
            RuleSet ruleSet = (RuleSet) constructor.newInstance(prefix);
            digester.addRuleSet(ruleSet);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.noCluster", e.getClass().getName() + ": " + e.getMessage()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("catalina.noCluster", e.getClass().getName() + ": " + e.getMessage()));
            }
        }
    }

    /**
     * Create and configure the Digester we will be using for shutdown.
     *
     * @return the digester to process the stop operation
     */
    protected Digester createStopDigester() {
        // Initialize the digester
        Digester digester = new Digester();
        digester.setUseContextClassLoader(true);

        // Configure the rules we need for shutting down
        digester.addObjectCreate("Server", "org.apache.catalina.core.StandardServer", "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server", "setServer", "org.apache.catalina.Server");

        return digester;

    }

    /**
     * 解析server.xml
     */
    protected void parseServerXml(boolean start) {
        // Set configuration source
        // <Bootstrap.getCatalinaBaseFile(), getConfigFile()> = System.getProperty("user.dir") + "conf/server.xml"
        // 设置ConfigFileLoader类的source属性
        ConfigFileLoader.setSource(new CatalinaBaseConfigurationSource(Bootstrap.getCatalinaBaseFile(), getConfigFile()));
        File file = configFile();

        if (useGeneratedCode && !Digester.isGeneratedCodeLoaderSet()) {
            // Load loader
            String loaderClassName = generatedCodePackage + ".DigesterGeneratedCodeLoader";
            try {
                Digester.GeneratedCodeLoader loader = (Digester.GeneratedCodeLoader)
                    Catalina.class.getClassLoader().loadClass(loaderClassName).getDeclaredConstructor().newInstance();
                Digester.setGeneratedCodeLoader(loader);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.info(sm.getString("catalina.noLoader", loaderClassName), e);
                } else {
                    log.info(sm.getString("catalina.noLoader", loaderClassName));
                }
                // No loader so don't use generated code
                useGeneratedCode = false;
            }
        }

        // Init source location
        File serverXmlLocation = null;
        String xmlClassName = null;
        if (generateCode || useGeneratedCode) {
            xmlClassName = start ? generatedCodePackage + ".ServerXml" : generatedCodePackage + ".ServerXmlStop";
        }
        if (generateCode) {
            if (generatedCodeLocationParameter != null) {
                generatedCodeLocation = new File(generatedCodeLocationParameter);
                if (!generatedCodeLocation.isAbsolute()) {
                    generatedCodeLocation = new File(Bootstrap.getCatalinaHomeFile(), generatedCodeLocationParameter);
                }
            } else {
                generatedCodeLocation = new File(Bootstrap.getCatalinaHomeFile(), "work");
            }
            serverXmlLocation = new File(generatedCodeLocation, generatedCodePackage);
            if (!serverXmlLocation.isDirectory() && !serverXmlLocation.mkdirs()) {
                log.warn(sm.getString("catalina.generatedCodeLocationError", generatedCodeLocation.getAbsolutePath()));
                // Disable code generation
                generateCode = false;
            }
        }

        ServerXml serverXml = null;
        if (useGeneratedCode) {
            serverXml = (ServerXml) Digester.loadGeneratedClass(xmlClassName);
        }

        if (serverXml != null) {
            serverXml.load(this);
        } else {
            try (ConfigurationSource.Resource resource = ConfigFileLoader.getSource().getServerXml()) {
                // Create and execute our Digester
                Digester digester = start ? createStartDigester() : createStopDigester();
                // server.xml文件流
                InputStream inputStream = resource.getInputStream();
                // url的文件流
                InputSource inputSource = new InputSource(resource.getURI().toURL().toString());
                inputSource.setByteStream(inputStream);

                digester.push(this);
                if (generateCode) {
                    digester.startGeneratingCode();
                    generateClassHeader(digester, start);
                }
                digester.parse(inputSource);

                if (generateCode) {
                    generateClassFooter(digester);
                    try (FileWriter writer = new FileWriter(new File(serverXmlLocation, start ? "ServerXml.java" : "ServerXmlStop.java"))) {
                        writer.write(digester.getGeneratedCode().toString());
                    }
                    digester.endGeneratingCode();
                    Digester.addGeneratedClass(xmlClassName);
                }
            } catch (Exception e) {
                log.warn(sm.getString("catalina.configFail", file.getAbsolutePath()), e);
                if (file.exists() && !file.canRead()) {
                    log.warn(sm.getString("catalina.incorrectPermissions"));
                }
            }
        }
    }

    public void stopServer() {
        stopServer(null);
    }

    public void stopServer(String[] arguments) {
        if (arguments != null) {
            arguments(arguments);
        }

        Server s = getServer();
        if (s == null) {
            parseServerXml(false);
            if (getServer() == null) {
                log.error(sm.getString("catalina.stopError"));
                System.exit(1);
            }
        } else {
            // Server object already present. Must be running as a service
            try {
                s.stop();
                s.destroy();
            } catch (LifecycleException e) {
                log.error(sm.getString("catalina.stopError"), e);
            }
            return;
        }

        // Stop the existing server
        s = getServer();
        if (s.getPortWithOffset() > 0) {
            try (Socket socket = new Socket(s.getAddress(), s.getPortWithOffset());
                 OutputStream stream = socket.getOutputStream()) {
                String shutdown = s.getShutdown();
                for (int i = 0; i < shutdown.length(); i++) {
                    stream.write(shutdown.charAt(i));
                }
                stream.flush();
            } catch (ConnectException ce) {
                log.error(sm.getString("catalina.stopServer.connectException", s.getAddress(),
                    String.valueOf(s.getPortWithOffset()), String.valueOf(s.getPort()),
                    String.valueOf(s.getPortOffset())));
                log.error(sm.getString("catalina.stopError"), ce);
                System.exit(1);
            } catch (IOException e) {
                log.error(sm.getString("catalina.stopError"), e);
                System.exit(1);
            }
        } else {
            log.error(sm.getString("catalina.stopServer"));
            System.exit(1);
        }
    }

    /**
     * Start a new server instance.
     * 启动一个新的服务器实例。
     */
    public void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        long t1 = System.nanoTime();

        // Before digester - it may be needed
        /**
         * 设置系统变量
         * 1. 设置"java.naming.factory.url.pkgs"=<org.apache.naming>
         * 2. 设置"java.naming.factory.initial"=<org.apache.naming.java.javaURLContextFactory>
         */
        initNaming();

        // Parse main server.xml
        // 解析 server.xml，这里通过Digester实例化一个Server对象实例
        parseServerXml(true);
        Server s = getServer();
        if (s == null) {
            return;
        }

        getServer().setCatalina(this);
        getServer().setCatalinaHome(Bootstrap.getCatalinaHomeFile());
        getServer().setCatalinaBase(Bootstrap.getCatalinaBaseFile());

        // Stream redirection
        initStreams();

        // Start the new server
        try {
            getServer().init();
        } catch (LifecycleException e) {
            if (throwOnInitFailure) {
                throw new java.lang.Error(e);
            } else {
                log.error(sm.getString("catalina.initError"), e);
            }
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("catalina.init", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1))));
        }
    }

    /*
     * Load using arguments
     */
    public void load(String args[]) {
        try {
            if (arguments(args)) {
                load();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /**
     * Start a new server instance.
     */
    public void start() {
        if (getServer() == null) {
            load();
        }

        if (getServer() == null) {
            log.fatal(sm.getString("catalina.noServer"));
            return;
        }

        long t1 = System.nanoTime();

        // Start the new server
        try {
            getServer().start();
        } catch (LifecycleException e) {
            log.fatal(sm.getString("catalina.serverStartFail"), e);
            try {
                getServer().destroy();
            } catch (LifecycleException e1) {
                log.debug("destroy() failed for failed Server ", e1);
            }
            return;
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("catalina.startup", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1))));
        }

        if (generateCode) {
            // Generate loader which will load all generated classes
            generateLoader();
        }

        // Register shutdown hook
        if (useShutdownHook) {
            if (shutdownHook == null) {
                shutdownHook = new CatalinaShutdownHook();
            }

            /**
             * ######JVM的关闭钩子：######
             *
             * 关闭钩子是指通过Runtime.addShutdownHook注册的但尚未开始的线程。
             * 这些钩子可以用于实现服务或者应用程序的清理工作，例如删除临时文件，或者清除无法由操作系统自动清除的资源。
             * JVM既可以正常关闭，也可以强行关闭。正常关闭的触发方式有多种，包括：当最后一个“正常（非守护）”线程结束时，
             * 或者当调用了System.exit时，或者通过其他特定于平台的方法关闭时（例如发送了SIGINT信号或者键入Ctrl-C）。
             *
             * 在正常关闭中，JVM首先调用所有已注册的关闭钩子。JVM并不能保证关闭钩子的调用顺序。在关闭应用程序线程时，
             * 如果有（守护或者非守护）线程仍然在执行，那么这些线程接下来将与关闭进程并发执行。当所有的关闭钩子都执行结束时，
             * 如果runFinalizersOnExit为true【通过Runtime.runFinalizersOnExit(true)设置】，
             * 那么JVM将运行这些Finalizer（对象重写的finalize方法），然后再停止。JVM不会停止或中断任何在关闭时仍然运行的应用程序线程。
             * 当JVM最终结束时，这些线程将被强行结束。如果关闭钩子或者Finalizer没有执行完成，那么正常关闭进程“挂起”并且JVM必须被强行关闭。
             * 当JVM被强行关闭时，只是关闭JVM，并不会运行关闭钩子（举个例子，类似于电源都直接拔了，还怎么做其它动作呢？）。
             */
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // If JULI is being used, disable JULI's shutdown hook since
            // shutdown hooks run in parallel and log messages may be lost
            // if JULI's hook completes before the CatalinaShutdownHook()
            LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof ClassLoaderLogManager) {
                ((ClassLoaderLogManager) logManager).setUseShutdownHook(false);
            }
        }

        // 调用server的await()方法和stop()方法
        if (await) {
            await();
            stop();
        }
    }

    /**
     * Stop an existing server instance.
     */
    public void stop() {
        try {
            // Remove the ShutdownHook first so that server.stop() doesn't get invoked twice
            if (useShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);

                // If JULI is being used, re-enable JULI's shutdown to ensure
                // log messages are not lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).setUseShutdownHook(true);
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }

        // Shut down the server
        try {
            Server s = getServer();
            LifecycleState state = s.getState();
            if (LifecycleState.STOPPING_PREP.compareTo(state) <= 0
                && LifecycleState.DESTROYED.compareTo(state) >= 0) {
                // Nothing to do. stop() was already called
            } else {
                s.stop();
                s.destroy();
            }
        } catch (LifecycleException e) {
            log.error(sm.getString("catalina.stopError"), e);
        }
    }

    /**
     * Await and shutdown.
     */
    public void await() {
        getServer().await();
    }

    /**
     * Print usage information for this application.
     * 打印此应用程序的使用信息。
     */
    protected void usage() {
        System.out.println(sm.getString("catalina.usage"));
    }

    protected void initStreams() {
        // Replace System.out and System.err with a custom PrintStream
        System.setOut(new SystemLogHandler(System.out));
        System.setErr(new SystemLogHandler(System.err));
    }

    // 初始化命名服务
    protected void initNaming() {
        // Setting additional variables
        // 未开启命名功能
        if (!useNaming) {
            log.info(sm.getString("catalina.noNaming"));
            System.setProperty("catalina.useNaming", "false");
        }
        // 默认开启命名功能
        else {
            System.setProperty("catalina.useNaming", "true");
            String value = "org.apache.naming";

            /**
             * javax.naming.Context.URL_PKG_PREFIXES = "java.naming.factory.url.pkgs"
             * 常量，它保存环境属性的名称，用于指定在URL上下文工厂中加载时要使用的包前缀列表。
             * 该属性的值应该是一个以冒号分隔的包前缀列表，用于创建URL上下文工厂的工厂类的类名。此属性可以在环境、系统属性或一个或多个资源文件中指定。
             * 前缀com.sun.jndi.url总是被附加到可能为空的包前缀列表中。
             */
            String oldValue = System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                value = value + ":" + oldValue;
            }
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if (log.isDebugEnabled()) {
                log.debug("Setting naming prefix=" + value);
            }

            //
            /**
             * 上下文工厂：org.apache.naming.java.javaURLContextFactory
             * javax.naming.Context.INITIAL_CONTEXT_FACTORY = "java.naming.factory.initial"
             * 常量，该常量包含用于指定要使用的初始上下文工厂的环境属性的名称。该属性的值应该是将创建初始上下文的工厂类的完全限定类名。
             * 此属性可以在传递给初始上下文构造函数的环境参数、系统属性或应用程序资源文件中指定。
             * 如果没有在这些源中指定它，则在需要初始上下文来完成操作时抛出NoInitialContextException。
             */
            value = System.getProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {
                System.setProperty(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug("INITIAL_CONTEXT_FACTORY already set " + value);
            }
        }
    }

    /**
     * 实例化对象调用方法
     * Set the security package access/protection.
     */
    protected void setSecurityProtection() {
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }

    protected void generateLoader() {
        String loaderClassName = "DigesterGeneratedCodeLoader";
        StringBuilder code = new StringBuilder();
        code.append("package ").append(generatedCodePackage).append(";").append(System.lineSeparator());
        code.append("public class ").append(loaderClassName);
        code.append(" implements org.apache.tomcat.util.digester.Digester.GeneratedCodeLoader {").append(System.lineSeparator());
        code.append("public Object loadGeneratedCode(String className) {").append(System.lineSeparator());
        code.append("switch (className) {").append(System.lineSeparator());
        for (String generatedClassName : Digester.getGeneratedClasses()) {
            code.append("case \"").append(generatedClassName).append("\" : return new ").append(generatedClassName);
            code.append("();").append(System.lineSeparator());
        }
        code.append("default: return null; }").append(System.lineSeparator());
        code.append("}}").append(System.lineSeparator());
        File loaderLocation = new File(generatedCodeLocation, generatedCodePackage);
        try (FileWriter writer = new FileWriter(new File(loaderLocation, loaderClassName + ".java"))) {
            writer.write(code.toString());
        } catch (IOException e) {
            // Should not happen
            log.debug("Error writing code loader", e);
        }
    }

    /**
     * 手动拼接Java类：
     * ServerXml 或 ServerXmlStop
     * package catalinaembedded;
     * <p>
     * public class ServerXml implements org.apache.catalina.startup.Catalina.ServerXml {
     * <p>
     * public void load(Catalina catalina) {
     */
    protected void generateClassHeader(Digester digester, boolean start) {
        StringBuilder code = digester.getGeneratedCode();
        code.append("package ").append(generatedCodePackage).append(";").append(System.lineSeparator());
        code.append("public class ServerXml");
        if (!start) {
            code.append("Stop");
        }
        code.append(" implements ");
        code.append(ServerXml.class.getName().replace('$', '.')).append(" {").append(System.lineSeparator());
        code.append("public void load(").append(Catalina.class.getName());
        code.append(' ').append(digester.toVariableName(this)).append(") {").append(System.lineSeparator());
    }

    protected void generateClassFooter(Digester digester) {
        StringBuilder code = digester.getGeneratedCode();
        code.append('}').append(System.lineSeparator());
        code.append('}').append(System.lineSeparator());
    }

    public interface ServerXml {
        void load(Catalina catalina);
    }

    // --------------------------------------- CatalinaShutdownHook Inner Class

    // XXX Should be moved to embedded !

    /**
     * Shutdown hook which will perform a clean shutdown of Catalina if needed.
     */
    protected class CatalinaShutdownHook extends Thread {
        @Override
        public void run() {
            try {
                if (getServer() != null) {
                    Catalina.this.stop();
                }
            } catch (Throwable ex) {
                ExceptionUtils.handleThrowable(ex);
                log.error(sm.getString("catalina.shutdownHookFail"), ex);
            } finally {
                // If JULI is used, shut JULI down *after* the server shuts down
                // so log messages aren't lost
                LogManager logManager = LogManager.getLogManager();
                if (logManager instanceof ClassLoaderLogManager) {
                    ((ClassLoaderLogManager) logManager).shutdown();
                }
            }
        }
    }

    /**
     * Rule that sets the parent class loader for the top object on the stack,
     * which must be a <code>Container</code>.
     */
    final class SetParentClassLoaderRule extends Rule {

        public SetParentClassLoaderRule(ClassLoader parentClassLoader) {
            this.parentClassLoader = parentClassLoader;
        }

        ClassLoader parentClassLoader = null;

        @Override
        public void begin(String namespace, String name, Attributes attributes) throws Exception {

            if (digester.getLogger().isDebugEnabled()) {
                digester.getLogger().debug("Setting parent class loader");
            }

            Container top = (Container) digester.peek();
            top.setParentClassLoader(parentClassLoader);

            StringBuilder code = digester.getGeneratedCode();
            if (code != null) {
                code.append(digester.toVariableName(top)).append(".setParentClassLoader(");
                code.append(digester.toVariableName(Catalina.this)).append(".getParentClassLoader());");
                code.append(System.lineSeparator());
            }
        }
    }

}
