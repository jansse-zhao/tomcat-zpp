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

import javax.management.MBeanRegistration;
import javax.management.ObjectName;

/**
 * This interface is implemented by components that will be registered with an
 * MBean server when they are created and unregistered when they are destroyed.
 * It is primarily intended to be implemented by components that implement
 * {@link Lifecycle} but is not exclusively for them.
 * <p>
 * MBeanRegistration：java JMX框架提供的MBean注册接口，引入此接口是为了便于使用JMX提供的管理功能，通过 MBeans，可以监控和管理各种 Java 应用程序和服务器的性能、状态和配置。
 * 在设计上JmxEnabled引用一个域（Domain）对注册的MBeans进行隔离，这个域类似于MBean上层的命名空间一样。
 * 此接口由组件实现，这些组件在创建时将注册到MBean服务器，在销毁时将注销这些组件。它主要是由实现生命周期的组件来实现的，但并不是专门为它们实现的。
 */
public interface JmxEnabled extends MBeanRegistration {

    /**
     * @return the domain under which this component will be / has been
     * registered.
     * <p>
     * 获取MBean所属于的Domain
     */
    String getDomain();

    /**
     * Specify the domain under which this component should be registered. Used
     * with components that cannot (easily) navigate the component hierarchy to
     * determine the correct domain to use.
     * <p>
     * 设置Domain
     *
     * @param domain The name of the domain under which this component should be
     *               registered
     */
    void setDomain(String domain);

    /**
     * @return the name under which this component has been registered with JMX.
     * <p>
     * 获取MBean的名字
     */
    ObjectName getObjectName();
}
