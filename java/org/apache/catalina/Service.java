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
package org.apache.catalina;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.mapper.Mapper;

/**
 * A <strong>Service</strong> is a group of one or more
 * <strong>Connectors</strong> that share a single <strong>Container</strong>
 * to process their incoming requests.  This arrangement allows, for example,
 * a non-SSL and SSL connector to share the same population of web apps.
 * <p>
 * A given JVM can contain any number of Service instances; however, they are
 * completely independent of each other and share only the basic JVM facilities
 * and classes on the system class path.
 *
 * @author Craig R. McClanahan
 * Service: 表示服务，Server可以运行多个服务。
 * 比如一个Tomcat里面可运行订单服务、支付服务、用户服务等等；
 * Server的实现类StandardServer可以包含一个到多个Services,
 * Service的实现类为StandardService调用了容器(Container)接口，其实是调用了Servlet Engine(引擎)，
 * 而且StandardService类中也指明了该Service归属的Server;
 */
public interface Service extends Lifecycle {

    /**
     * Service组件中只包含Connector(负责监听不同某端口的客户端请求，不同的端口对应不同的Connector)组件
     * 和Executor（Service抽象层提供的线程池，供Service下的组件共用的线程池）组件，还有子容器Engine
     * 例如：
     * addConnector/findConnectors/removeConnector
     * addExecutor/findExecutors/getExecutor/removeExecutor
     * getContainer/setContainer
     */

    // ------------------------------------------------------------- Properties

    /**
     * @return the name of this Service.
     * 获取当前服务名称
     */
    String getName();

    /**
     * Set the name of this Service.
     * <p>
     * 设置当前服务名称
     *
     * @param name The new service name
     */
    void setName(String name);

    /**
     * @return the <code>Engine</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     * <p>
     * 获取当前服务的与连接器相关的Engine
     */
    Engine getContainer();

    /**
     * Set the <code>Engine</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     *
     * @param engine The new Engine
     */
    void setContainer(Engine engine);

    /**
     * @return the <code>Server</code> with which we are associated (if any).
     * <p>
     * 获取当前服务的Server
     */
    Server getServer();

    /**
     * Set the <code>Server</code> with which we are associated (if any).
     * <p>
     * 设置当前服务的Server
     *
     * @param server The server that owns this Service
     */
    void setServer(Server server);

    /**
     * @return the parent class loader for this component. If not set, return
     * {@link #getServer()} {@link Server#getParentClassLoader()}. If no server
     * has been set, return the system class loader.
     * <p>
     * 获取当前服务的父类加载器
     */
    ClassLoader getParentClassLoader();

    /**
     * Set the parent class loader for this service.
     *
     * @param parent The new parent class loader
     */
    void setParentClassLoader(ClassLoader parent);

    /**
     * @return the domain under which this container will be / has been
     * registered.
     */
    String getDomain();


    // --------------------------------------------------------- Public Methods

    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * @param connector The Connector to be added
     */
    void addConnector(Connector connector);

    /**
     * Find and return the set of Connectors associated with this Service.
     *
     * @return the set of associated Connectors
     */
    Connector[] findConnectors();

    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    void removeConnector(Connector connector);

    /**
     * Adds a named executor to the service
     *
     * @param ex Executor
     */
    void addExecutor(Executor ex);

    /**
     * Retrieves all executors
     *
     * @return Executor[]
     */
    Executor[] findExecutors();

    /**
     * Retrieves executor by name, null if not found
     *
     * @param name String
     * @return Executor
     */
    Executor getExecutor(String name);

    /**
     * Removes an executor from the service
     *
     * @param ex Executor
     */
    void removeExecutor(Executor ex);

    /**
     * @return the mapper associated with this Service.
     */
    Mapper getMapper();
}
