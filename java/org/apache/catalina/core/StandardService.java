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
package org.apache.catalina.core;

import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;
import org.apache.catalina.mapper.MapperListener;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

import javax.management.ObjectName;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

/**
 * Standard implementation of the <code>Service</code> interface.  The
 * associated Container is generally an instance of Engine, but this is
 * not required.
 *
 * @author Craig R. McClanahan
 */
public class StandardService extends LifecycleMBeanBase implements Service {

    private static final Log log = LogFactory.getLog(StandardService.class);
    private static final StringManager sm = StringManager.getManager(StandardService.class);

    /**
     * Service 是服务的抽象，它代表请求从接收到处理的所有组件的集合。在设计上Server 组件可以包含多个 Service 组件，
     * 每个 Service 组件都包含了若干用于接收客户端消息的 Connector 组件和处理请求的 Engine 组件。
     * 其中，不同的Connector 组件使用不同的通信协议，如 HTTP协议和AJP协议，当然还可以有其他的协议A和协议B。
     * 若干Connector组件和一个客户端请求处理组件Engine组成的集合即为Service。
     * 此外，Service 组件还包含了若干Executor 组件，每个Executor 都是一个线程池，它可以为Service 内所有组件提供线程池执行任务。
     */

    /**
     * Service类的几种主要类型的方法：
     * 1. Engine相关属性和方法：getContainer()、setContainer()
     * 2. Connectors相关属性和方法：addConnector()、getConnectorNames()、findConnectors()、removeConnector()
     * 3. Executor相关属性和方法：addExecutor()、findExecutors()、getExecutor()、removeExecutor()
     * 4. Lifecycle相关模板方法：initInternal()、startInternal()
     */

    // ----------------------------------------------------- Instance Variables

    /**
     * The name of this service.
     */
    private String name = null;

    /**
     * The <code>Server</code> that owns this Service, if any.
     */
    private Server server = null;

    /**
     * The property change support for this component.
     * PropertyChangeSupport：它的主要作用在于帮助JavaBean在属性值发生变化时通知所有相关的监听器
     */
    protected final PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * The set of Connectors associated with this Service.
     * 与此服务关联的一组连接器。
     */
    protected Connector connectors[] = new Connector[ 0 ];
    private final Object connectorsLock = new Object();

    /**
     * The list of executors held by the service.
     * 服务持有的线程执行器列表。
     */
    protected final ArrayList<Executor> executors = new ArrayList<>();

    private Engine engine = null;

    private ClassLoader parentClassLoader = null;

    /**
     * ######Mapper######
     * Mapper是 Tomcat 处理 Http 请求时非常重要的组件。
     * Tomcat 使用 Mapper 来处理一个 Request 到 Host、Context 的映射关系，从而决定使用哪个 Service 来处理请求。
     */
    protected final Mapper mapper = new Mapper();

    /**
     * Mapper listener.
     * mapperListener 的作用是在 start 的时候将容器类对象注册到 Mapper 对象中。
     */
    protected final MapperListener mapperListener = new MapperListener(this);

    private long gracefulStopAwaitMillis = 0;

    // ------------------------------------------------------------- Properties

    public long getGracefulStopAwaitMillis() {
        return gracefulStopAwaitMillis;
    }

    public void setGracefulStopAwaitMillis(long gracefulStopAwaitMillis) {
        this.gracefulStopAwaitMillis = gracefulStopAwaitMillis;
    }

    @Override
    public Mapper getMapper() {
        return mapper;
    }

    @Override
    public Engine getContainer() {
        return engine;
    }

    /**
     * 给当前Service服务设置新引擎Engine
     * 1. 停止当前服务的引擎，开始启动新的引擎
     * 2. 刷新MapperListener
     */
    @Override
    public void setContainer(Engine engine) {
        Engine oldEngine = this.engine;
        if (oldEngine != null) {
            oldEngine.setService(null);
        }
        this.engine = engine;
        if (this.engine != null) {
            this.engine.setService(this);
        }
        if (getState().isAvailable()) {
            if (this.engine != null) {
                try {
                    this.engine.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.engine.startFailed"), e);
                }
            }
            // Restart MapperListener to pick up new engine.
            try {
                mapperListener.stop();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardService.mapperListener.stopFailed"), e);
            }
            try {
                mapperListener.start();
            } catch (LifecycleException e) {
                log.error(sm.getString("standardService.mapperListener.startFailed"), e);
            }
            if (oldEngine != null) {
                try {
                    oldEngine.stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.engine.stopFailed"), e);
                }
            }
        }

        // Report this property change to interested listeners
        // 触发container属性变更事件
        // 当前类没有配置属性监听器（通过addPropertyChangeListener()方法设置），也就不会执行事件
        support.firePropertyChange("container", oldEngine, this.engine);
    }

    /**
     * Return the name of this Service.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the <code>Server</code> with which we are associated (if any).
     */
    @Override
    public Server getServer() {
        return this.server;
    }

    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    @Override
    public void setServer(Server server) {
        this.server = server;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     * <p>
     * 给当前Service添加一个Connector连接器，并start()启动该连接器
     *
     * @param connector The Connector to be added
     */
    @Override
    public void addConnector(Connector connector) {
        synchronized (connectorsLock) {
            connector.setService(this);
            Connector results[] = new Connector[ connectors.length + 1 ];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[ connectors.length ] = connector;
            connectors = results;
        }

        try {
            if (getState().isAvailable()) {
                connector.start();
            }
        } catch (LifecycleException e) {
            throw new IllegalArgumentException(sm.getString("standardService.connector.startFailed", connector), e);
        }

        // Report this property change to interested listeners
        support.firePropertyChange("connector", null, connector);
    }

    /**
     * 获取连接器Connector在JMX中注册的名称
     */
    public ObjectName[] getConnectorNames() {
        ObjectName[] results = new ObjectName[ connectors.length ];
        for (int i = 0; i < results.length; i++) {
            results[ i ] = connectors[ i ].getObjectName();
        }
        return results;
    }

    /**
     * Find and return the set of Connectors associated with this Service.
     */
    @Override
    public Connector[] findConnectors() {
        return connectors;
    }

    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     * <p>
     * 删除指定连接器
     *
     * @param connector The Connector to be removed
     */
    @Override
    public void removeConnector(Connector connector) {
        synchronized (connectorsLock) {
            // 待删除的连接器的下标
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[ i ]) {
                    j = i;
                    break;
                }
            }
            if (j < 0) {
                return;
            }

            // 连接器状态可用，则先停止连接器
            if (connectors[ j ].getState().isAvailable()) {
                try {
                    connectors[ j ].stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                        "standardService.connector.stopFailed",
                        connectors[ j ]), e);
                }
            }
            connector.setService(null);
            int k = 0;

            Connector[] results = new Connector[ connectors.length - 1 ];
            for (int i = 0; i < connectors.length; i++) {
                // 跳过待删除的连接器，将剩余的放到新的连接器数组中
                if (i != j) {
                    results[ k++ ] = connectors[ i ];
                }
            }
            connectors = results;

            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }
    }

    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append(']');
        return sb.toString();
    }

    /**
     * Adds a named executor to the service
     * <p>
     *
     * @param ex Executor
     * @see org.apache.catalina.core.StandardThreadExecutor#startInternal()
     * 向当前服务添加线程执行器，并启动该线程执行器
     */
    @Override
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (getState().isAvailable()) {
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error(sm.getString("standardService.executor.start"), x);
                    }
                }
            }
        }
    }

    /**
     * Retrieves all executors
     *
     * @return Executor[]
     */
    @Override
    public Executor[] findExecutors() {
        synchronized (executors) {
            return executors.toArray(new Executor[ 0 ]);
        }
    }

    /**
     * Retrieves executor by name, null if not found
     *
     * @param executorName String
     * @return Executor
     */
    @Override
    public Executor getExecutor(String executorName) {
        synchronized (executors) {
            for (Executor executor : executors) {
                if (executorName.equals(executor.getName())) {
                    return executor;
                }
            }
        }
        return null;
    }

    /**
     * Removes an executor from the service
     *
     * @param ex Executor
     */
    @Override
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if (executors.remove(ex) && getState().isAvailable()) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString("standardService.executor.stop"), e);
                }
            }
        }
    }

    /**
     * Start nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("standardService.start.name", this.name));
        }
        setState(LifecycleState.STARTING);

        // Start our defined Container first
        // 首先启动Engine引擎
        if (engine != null) {
            synchronized (engine) {
                engine.start();
            }
        }

        // 启动线程执行器
        synchronized (executors) {
            for (Executor executor : executors) {
                executor.start();
            }
        }

        mapperListener.start();

        // Start our defined Connectors second
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                // If it has already failed, don't try and start it
                if (connector.getState() != LifecycleState.FAILED) {
                    connector.start();
                }
            }
        }
    }

    /**
     * Stop nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {
        synchronized (connectorsLock) {
            // Initiate a graceful stop for each connector
            // This will only work if the bindOnInit==false which is not the
            // default.
            for (Connector connector : connectors) {
                connector.getProtocolHandler().closeServerSocketGraceful();
            }

            // Wait for the graceful shutdown to complete
            long waitMillis = gracefulStopAwaitMillis;
            if (waitMillis > 0) {
                for (Connector connector : connectors) {
                    waitMillis = connector.getProtocolHandler().awaitConnectionsClose(waitMillis);
                }
            }

            // Pause the connectors
            for (Connector connector : connectors) {
                connector.pause();
            }
        }

        if (log.isInfoEnabled()) {
            log.info(sm.getString("standardService.stop.name", this.name));
        }
        setState(LifecycleState.STOPPING);

        // Stop our defined Container once the Connectors are all paused
        if (engine != null) {
            synchronized (engine) {
                engine.stop();
            }
        }

        // Now stop the connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                if (!LifecycleState.STARTED.equals(
                    connector.getState())) {
                    // Connectors only need stopping if they are currently
                    // started. They may have failed to start or may have been
                    // stopped (e.g. via a JMX call)
                    continue;
                }
                connector.stop();
            }
        }

        // If the Server failed to start, the mapperListener won't have been
        // started
        if (mapperListener.getState() != LifecycleState.INITIALIZED) {
            mapperListener.stop();
        }

        synchronized (executors) {
            for (Executor executor : executors) {
                executor.stop();
            }
        }
    }

    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     * <p>
     * 初始化了引擎Engine、mapperListener引擎、线程执行器Executor和连接器Connector
     */
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        if (engine != null) {
            engine.init();
        }

        // Initialize any Executors
        for (Executor executor : findExecutors()) {
            if (executor instanceof JmxEnabled) {
                ((JmxEnabled) executor).setDomain(getDomain());
            }
            executor.init();
        }

        // Initialize mapper listener
        mapperListener.init();

        // Initialize our defined Connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.init();
            }
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        mapperListener.destroy();

        // Destroy our defined Connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                connector.destroy();
            }
        }

        // Destroy any Executors
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        if (engine != null) {
            engine.destroy();
        }

        super.destroyInternal();
    }


    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null) {
            return parentClassLoader;
        }
        if (server != null) {
            return server.getParentClassLoader();
        }
        return ClassLoader.getSystemClassLoader();
    }


    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
            this.parentClassLoader);
    }


    @Override
    protected String getDomainInternal() {
        String domain = null;
        Container engine = getContainer();

        // Use the engine name first
        if (engine != null) {
            domain = engine.getName();
        }

        // No engine or no engine name, use the service name
        if (domain == null) {
            domain = getName();
        }

        // No service name, return null which will trigger the use of the
        // default
        return domain;
    }


    @Override
    public final String getObjectNameKeyProperties() {
        return "type=Service";
    }
}
