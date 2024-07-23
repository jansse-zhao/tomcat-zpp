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
package org.apache.catalina.loader;

import org.apache.catalina.LifecycleException;

public class WebappClassLoader extends WebappClassLoaderBase {

    /**
     * WebappClassLoader 并没有遵循双亲委派机制，而是按自己的策略顺序加载类。根据委托标识，加载分为两种方式。
     * ① 当委托标识 delegate 为false 时，WebappClassLoader 类加载器首先先尝试从本地缓存中查找加载该类，
     * 然后用System 类加载器尝试加载类，接着由自己尝试加载类，最后才由父类加载(Common)器尝试加载。
     * 所以此时它搜索的目录顺序是<JAVA HOME>/jre/lib---→
     * <JAVAHOME>jre/lib/ext---->
     * CLASSPATH---->
     * /WEB-INF/classes---→
     * /WEB-INF/ib---->
     * $CATALINA BASE/ib---->
     * $CATALINA HOME/ib。
     * ② 当委托标识 delegate 为 true 时,WebappClassLoader 类加载器首先先尝试从本地缓存中查找加载该类，
     * 然后用System 类加载器尝试加载类，接着由父类加载器(Common)尝试加载类，最后才由自己尝试加载。
     * 所以此时它的搜索的目录顺序是<JAVA HOME>/jre/lib---→
     * <JAVAHOME>/jre/lib/ext---->
     * CLASSPATH---->
     * $CATALINA_BASE/lib--->$CATALINA_HOME/lib---->
     * /WEB-INF/classes---->
     * /WEB-INF/lib。
     */

    public WebappClassLoader() {
        super();
    }


    public WebappClassLoader(ClassLoader parent) {
        super(parent);
    }


    /**
     * Returns a copy of this class loader without any class file
     * transformers. This is a tool often used by Java Persistence API
     * providers to inspect entity classes in the absence of any
     * instrumentation, something that can't be guaranteed within the
     * context of a {@link java.lang.instrument.ClassFileTransformer}'s
     * {@link java.lang.instrument.ClassFileTransformer#transform(ClassLoader,
     * String, Class, java.security.ProtectionDomain, byte[]) transform} method.
     * <p>
     * The returned class loader's resource cache will have been cleared
     * so that classes already instrumented will not be retained or
     * returned.
     *
     * @return the transformer-free copy of this class loader.
     */
    @Override
    public WebappClassLoader copyWithoutTransformers() {

        WebappClassLoader result = new WebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }


    /**
     * This class loader is not parallel capable so lock on the class loader
     * rather than a per-class lock.
     */
    @Override
    protected Object getClassLoadingLock(String className) {
        return this;
    }
}
