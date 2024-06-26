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
package org.apache.catalina.util;

import org.apache.catalina.Globals;
import org.apache.catalina.JmxEnabled;
import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * 生命周期抽象对象：
 * 几个主要生命周期组件：
 * 1. WebappLoader
 * 2. Connector
 * 3. StandardServer
 * 4. StandardService
 * 5. ContainerBase
 * 5.1 StandardEngine
 * 5.2 StandardWrapper
 * 5.3 StandardContext
 * 5.4 StandardHost
 * <p>
 * <p>
 * LifecycleMBeanBase包含两块，一个是Lifecycle的接口实现，一个是Jmx接口封装实现。
 * 该抽象类提供的对MBeanRegistration的抽象实现类，运用抽象模板模式将所有容器统一注册到JMX。
 * ContainerBase、StandardServer、StandardService、WebappLoader、Connector、StandardContext、
 * StandardEngine、StandardHost、StandardWrapper等容器都继承了LifecycleMBeanBase，
 * 因此这些容器都具有了同样的生命周期并可以通过JMX进行管理。
 */
public abstract class LifecycleMBeanBase extends LifecycleBase implements JmxEnabled {

    private static final Log log = LogFactory.getLog(LifecycleMBeanBase.class);

    private static final StringManager sm = StringManager.getManager("org.apache.catalina.util");

    // 缓存组件的MBean注册。
    private String domain = null;
    // ObjectName：是Java Management Extensions (JMX)中用于唯一标识一个MBean（Managed Bean）
    // ObjectName的基本语法是：domain:key1=value1,key2=value2,...
    private ObjectName oname = null;

    /**
     * Sub-classes wishing to perform additional initialization should override
     * this method, ensuring that super.initInternal() is the first call in the
     * overriding method.
     */
    @Override
    protected void initInternal() throws LifecycleException {
        // If oname is not null then registration has already happened via
        // preRegister().
        if (oname == null) {
            oname = register(this, getObjectNameKeyProperties());
        }
    }

    /**
     * Sub-classes wishing to perform additional clean-up should override this
     * method, ensuring that super.destroyInternal() is the last call in the
     * overriding method.
     */
    @Override
    protected void destroyInternal() throws LifecycleException {
        unregister(oname);
    }


    /**
     * Specify the domain under which this component should be registered. Used
     * with components that cannot (easily) navigate the component hierarchy to
     * determine the correct domain to use.
     */
    @Override
    public final void setDomain(String domain) {
        this.domain = domain;
    }


    /**
     * Obtain the domain under which this component will be / has been
     * registered.
     */
    @Override
    public final String getDomain() {
        if (domain == null) {
            domain = getDomainInternal();
        }

        if (domain == null) {
            domain = Globals.DEFAULT_MBEAN_DOMAIN;
        }

        return domain;
    }


    /**
     * Method implemented by sub-classes to identify the domain in which MBeans
     * should be registered.
     *
     * @return The name of the domain to use to register MBeans.
     */
    protected abstract String getDomainInternal();


    /**
     * Obtain the name under which this component has been registered with JMX.
     */
    @Override
    public final ObjectName getObjectName() {
        return oname;
    }


    /**
     * Allow sub-classes to specify the key properties component of the
     * {@link ObjectName} that will be used to register this component.
     *
     * @return The string representation of the key properties component of the
     * desired {@link ObjectName}
     */
    protected abstract String getObjectNameKeyProperties();


    /**
     * Utility method to enable sub-classes to easily register additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param obj                     The object the register
     * @param objectNameKeyProperties The key properties component of the
     *                                object name to use to register the
     *                                object
     * @return The name used to register the object
     */
    protected final ObjectName register(Object obj, String objectNameKeyProperties) {
        // Construct an object name with the right domain
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        name.append(objectNameKeyProperties);

        ObjectName on = null;

        try {
            on = new ObjectName(name.toString());
            Registry.getRegistry(null, null).registerComponent(obj, on, null);
        } catch (Exception e) {
            log.warn(sm.getString("lifecycleMBeanBase.registerFail", obj, name), e);
        }

        return on;
    }


    /**
     * Utility method to enable sub-classes to easily unregister additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param objectNameKeyProperties The key properties component of the
     *                                object name to use to unregister the
     *                                object
     */
    protected final void unregister(String objectNameKeyProperties) {
        // Construct an object name with the right domain
        StringBuilder name = new StringBuilder(getDomain());
        name.append(':');
        name.append(objectNameKeyProperties);
        Registry.getRegistry(null, null).unregisterComponent(name.toString());
    }


    /**
     * Utility method to enable sub-classes to easily unregister additional
     * components that don't implement {@link JmxEnabled} with an MBean server.
     * <br>
     * Note: This method should only be used once {@link #initInternal()} has
     * been called and before {@link #destroyInternal()} has been called.
     *
     * @param on The name of the component to unregister
     */
    protected final void unregister(ObjectName on) {
        Registry.getRegistry(null, null).unregisterComponent(on);
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postDeregister() {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void postRegister(Boolean registrationDone) {
        // NOOP
    }


    /**
     * Not used - NOOP.
     */
    @Override
    public final void preDeregister() throws Exception {
        // NOOP
    }


    /**
     * Allows the object to be registered with an alternative
     * {@link MBeanServer} and/or {@link ObjectName}.
     */
    @Override
    public final ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {

        this.oname = name;
        this.domain = name.getDomain().intern();

        return oname;
    }

}
