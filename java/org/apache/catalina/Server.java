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

import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.startup.Catalina;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A <code>Server</code> element represents the entire Catalina
 * servlet container.  Its attributes represent the characteristics of
 * the servlet container as a whole.  A <code>Server</code> may contain
 * one or more <code>Services</code>, and the top level set of naming
 * resources.
 * <p>
 * Normally, an implementation of this interface will also implement
 * <code>Lifecycle</code>, such that when the <code>start()</code> and
 * <code>stop()</code> methods are called, all of the defined
 * <code>Services</code> are also started or stopped.
 * <p>
 * In between, the implementation must open a server socket on the port number
 * specified by the <code>port</code> property.  When a connection is accepted,
 * the first line is read and compared with the specified shutdown command.
 * If the command matches, shutdown of the server is initiated.
 *
 * @author Craig R. McClanahan
 * <p>
 * Server:表示服务器，它提供了一种优雅的方式来启动和停止整个系统，不必单独启停连接器和容器；
 * 它是Tomcat构成的顶级构成元素，所有一切均包含在Server中；
 */
public interface Server extends Lifecycle {

    // ------------------------------------------------------------- Properties

    /**
     * @return the global naming resources.
     */
    NamingResourcesImpl getGlobalNamingResources();

    /**
     * Set the global naming resources.
     *
     * @param globalNamingResources The new global naming resources
     */
    void setGlobalNamingResources(NamingResourcesImpl globalNamingResources);

    /**
     * @return the global naming resources context.
     */
    javax.naming.Context getGlobalNamingContext();

    /**
     * @return the port number we listen to for shutdown commands.
     * @see #getPortOffset()
     * @see #getPortWithOffset()
     * <p>
     * 该服务器等待关闭命令的TCP / IP端口号。设置为-1禁用关闭端口。注意：当使用Apache Commons Daemon启动Tomcat （在Windows上作为服务运行，或者在un * xes上使用jsvc运行）时，禁用关闭端口非常有效。
     * 但是，当使用标准shell脚本运行Tomcat时，不能使用它，因为它将阻止shutdown.bat
     */
    int getPort();

    /**
     * Set the port number we listen to for shutdown commands.
     *
     * @param port The new port number
     * @see #setPortOffset(int)
     */
    void setPort(int port);

    /**
     * Get the number that offsets the port used for shutdown commands.
     * For example, if port is 8005, and portOffset is 1000,
     * the server listens at 9005.
     *
     * @return the port offset
     */
    int getPortOffset();

    /**
     * Set the number that offsets the server port used for shutdown commands.
     * For example, if port is 8005, and you set portOffset to 1000,
     * connector listens at 9005.
     *
     * @param portOffset sets the port offset
     */
    void setPortOffset(int portOffset);

    /**
     * Get the actual port on which server is listening for the shutdown commands.
     * If you do not set port offset, port is returned. If you set
     * port offset, port offset + port is returned.
     *
     * @return the port with offset
     */
    int getPortWithOffset();

    /**
     * @return the address on which we listen to for shutdown commands.
     * <p>
     * 该服务器等待关闭命令的TCP / IP地址。如果未指定地址，localhost则使用。
     */
    String getAddress();

    /**
     * Set the address on which we listen to for shutdown commands.
     *
     * @param address The new address
     */
    void setAddress(String address);

    /**
     * @return the shutdown command string we are waiting for.
     * <p>
     * 为了关闭Tomcat，必须通过与指定端口号的TCP / IP连接接收的命令字符串。
     */
    String getShutdown();

    /**
     * Set the shutdown command we are waiting for.
     *
     * @param shutdown The new shutdown command
     */
    void setShutdown(String shutdown);


    /**
     * @return the parent class loader for this component. If not set, return
     * {@link #getCatalina()} {@link Catalina#getParentClassLoader()}. If
     * catalina has not been set, return the system class loader.
     */
    ClassLoader getParentClassLoader();


    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    void setParentClassLoader(ClassLoader parent);


    /**
     * @return the outer Catalina startup/shutdown component if present.
     */
    Catalina getCatalina();

    /**
     * Set the outer Catalina startup/shutdown component if present.
     *
     * @param catalina the outer Catalina component
     */
    void setCatalina(Catalina catalina);


    /**
     * @return the configured base (instance) directory. Note that home and base
     * may be the same (and are by default). If this is not set the value
     * returned by {@link #getCatalinaHome()} will be used.
     */
    File getCatalinaBase();

    /**
     * Set the configured base (instance) directory. Note that home and base
     * may be the same (and are by default).
     *
     * @param catalinaBase the configured base directory
     */
    void setCatalinaBase(File catalinaBase);


    /**
     * @return the configured home (binary) directory. Note that home and base
     * may be the same (and are by default).
     */
    File getCatalinaHome();

    /**
     * Set the configured home (binary) directory. Note that home and base
     * may be the same (and are by default).
     *
     * @param catalinaHome the configured home directory
     */
    void setCatalinaHome(File catalinaHome);


    /**
     * Get the utility thread count.
     *
     * @return the thread count
     * <p>
     * 此service中用于各种实用程序任务（包括重复执行的线程）的线程数。特殊值0将导致使用该值 Runtime.getRuntime().availableProcessors()。
     * Runtime.getRuntime().availableProcessors() + value除非小于1，否则将使用负值， 在这种情况下将使用1个线程。预设值是1。
     */
    int getUtilityThreads();

    /**
     * Set the utility thread count.
     *
     * @param utilityThreads the new thread count
     */
    void setUtilityThreads(int utilityThreads);

    // --------------------------------------------------------- Public Methods

    /**
     * Wait until a proper shutdown command is received, then return.
     */
    void await();

    /**
     * Add a new Service to the set of defined Services.
     *
     * @param service The Service to be added
     */
    void addService(Service service);

    /**
     * Find the specified Service
     *
     * @param name Name of the Service to be returned
     * @return the specified Service, or <code>null</code> if none exists.
     */
    Service findService(String name);

    /**
     * @return the set of Services defined within this Server.
     */
    Service[] findServices();

    /**
     * Remove the specified Service from the set associated from this
     * Server.
     *
     * @param service The Service to be removed
     */
    void removeService(Service service);

    /**
     * @return the token necessary for operations on the associated JNDI naming
     * context.
     */
    Object getNamingToken();

    /**
     * @return the utility executor managed by the Service.
     */
    ScheduledExecutorService getUtilityExecutor();

}
