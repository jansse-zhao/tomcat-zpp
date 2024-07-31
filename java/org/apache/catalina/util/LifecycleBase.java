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

import org.apache.catalina.*;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base implementation of the {@link Lifecycle} interface that implements the
 * state transition rules for {@link Lifecycle#start()} and
 * {@link Lifecycle#stop()}
 * <p>
 * LifecycleBase是使用了状态机+模板模式来实现的。模板方法有下面这几个：
 * 初始化方法
 * protected abstract void initInternal() throws LifecycleException;
 * 启动方法
 * protected abstract void startInternal() throws LifecycleException;
 * 停止方法
 * protected abstract void stopInternal() throws LifecycleException;
 * 销毁方法
 * protected abstract void destroyInternal() throws LifecycleException;
 */
public abstract class LifecycleBase implements Lifecycle {

    private static final Log log = LogFactory.getLog(LifecycleBase.class);

    private static final StringManager sm = StringManager.getManager(LifecycleBase.class);

    /**
     * The list of registered LifecycleListeners for event notifications.
     * 生命周期事件监听列表，该列表保存该组件已注册的监听器
     * <p>
     * 这里的 LifecycleListener 对象是在 Catalina 对象解析 server.xml 文件时就已经创建好并加到 lifecycleListeners 里的。这个不是特别重要就不细讲了。
     */
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    /**
     * The current state of the source component.
     */
    private volatile LifecycleState state = LifecycleState.NEW;

    private boolean throwOnFailure = true;

    /**
     * Will a {@link LifecycleException} thrown by a sub-class during
     * {@link #initInternal()}, {@link #startInternal()},
     * {@link #stopInternal()} or {@link #destroyInternal()} be re-thrown for
     * the caller to handle or will it be logged instead?
     *
     * @return {@code true} if the exception will be re-thrown, otherwise
     * {@code false}
     */
    public boolean getThrowOnFailure() {
        return throwOnFailure;
    }

    /**
     * Configure if a {@link LifecycleException} thrown by a sub-class during
     * {@link #initInternal()}, {@link #startInternal()},
     * {@link #stopInternal()} or {@link #destroyInternal()} will be re-thrown
     * for the caller to handle or if it will be logged instead.
     *
     * @param throwOnFailure {@code true} if the exception should be re-thrown,
     *                       otherwise {@code false}
     */
    public void setThrowOnFailure(boolean throwOnFailure) {
        this.throwOnFailure = throwOnFailure;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycleListeners.toArray(new LifecycleListener[ 0 ]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    /**
     * Allow sub classes to fire {@link Lifecycle} events.
     * <p>
     * 调用事件监听
     *
     * @param type Event type
     * @param data Data associated with event.
     */
    protected void fireLifecycleEvent(String type, Object data) {
        LifecycleEvent event = new LifecycleEvent(this, type, data);
        for (LifecycleListener listener : lifecycleListeners) {
            listener.lifecycleEvent(event);
        }
    }

    @Override
    public final synchronized void init() throws LifecycleException {
        // 非NEW状态，不允许调用init()方法
        if (!state.equals(LifecycleState.NEW)) {
            // 抛出生命周期异常：LifecycleException
            invalidTransition(Lifecycle.BEFORE_INIT_EVENT);
        }

        try {
            // 初始化逻辑之前，先将状态变更为`INITIALIZING`
            setStateInternal(LifecycleState.INITIALIZING, null, false);
            // 初始化，该方法为一个abstract方法，需要组件自行实现
            initInternal();
            // 初始化完成之后，状态变更为`INITIALIZED`
            setStateInternal(LifecycleState.INITIALIZED, null, false);
        } catch (Throwable t) {
            // 初始化的过程中，可能会有异常抛出，这时需要捕获异常，并将状态变更为`FAILED`
            handleSubClassException(t, "lifecycleBase.initFail", toString());
        }
    }

    /**
     * Sub-classes implement this method to perform any instance initialisation
     * required.
     *
     * @throws LifecycleException If the initialisation fails
     */
    protected abstract void initInternal() throws LifecycleException;

    /**
     * {@inheritDoc}
     * <p>
     * 父类中的start方法基本都是一写状态判断的操作，真正的启动操作交由子类的startInternal方法执行
     */
    @Override
    public final synchronized void start() throws LifecycleException {
        // `STARTING_PREP`、`STARTING`和`STARTED时，将忽略start()逻辑
        if (LifecycleState.STARTING_PREP.equals(state)
            || LifecycleState.STARTING.equals(state)
            || LifecycleState.STARTED.equals(state)) {

            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStarted", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStarted", toString()));
            }
            return;
        }

        // `NEW`状态时，执行init()方法
        if (state.equals(LifecycleState.NEW)) {
            init();
        }
        // `FAILED`状态时，执行stop()方法
        else if (state.equals(LifecycleState.FAILED)) {
            stop();
        }
        // 不是`INITIALIZED`和`STOPPED`时，则说明是非法的操作
        else if (!state.equals(LifecycleState.INITIALIZED) && !state.equals(LifecycleState.STOPPED)) {
            invalidTransition(Lifecycle.BEFORE_START_EVENT);
        }

        try {
            // start前的状态设置
            setStateInternal(LifecycleState.STARTING_PREP, null, false);

            // start逻辑，抽象方法，由组件自行实现
            startInternal();

            // start过程中，可能因为某些原因失败，这时需要stop操作
            if (state.equals(LifecycleState.FAILED)) {
                // This is a 'controlled' failure. The component put itself into the
                // FAILED state so call stop() to complete the clean-up.
                stop();
            } else if (!state.equals(LifecycleState.STARTING)) {
                // Shouldn't be necessary but acts as a check that sub-classes are
                // doing what they are supposed to.
                invalidTransition(Lifecycle.AFTER_START_EVENT);
            }
            // 设置状态为STARTED
            else {
                setStateInternal(LifecycleState.STARTED, null, false);
            }
        } catch (Throwable t) {
            // This is an 'uncontrolled' failure so put the component into the
            // FAILED state and throw an exception.
            handleSubClassException(t, "lifecycleBase.startFail", toString());
        }
    }

    /**
     * Sub-classes must ensure that the state is changed to
     * {@link LifecycleState#STARTING} during the execution of this method.
     * Changing state will trigger the {@link Lifecycle#START_EVENT} event.
     * <p>
     * If a component fails to start it may either throw a
     * {@link LifecycleException} which will cause it's parent to fail to start
     * or it can place itself in the error state in which case {@link #stop()}
     * will be called on the failed component but the parent component will
     * continue to start normally.
     *
     * @throws LifecycleException Start error occurred
     */
    protected abstract void startInternal() throws LifecycleException;

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void stop() throws LifecycleException {
        // `STOPPING_PREP`、`STOPPING`和STOPPED时，将忽略stop()的执行
        if (LifecycleState.STOPPING_PREP.equals(state) || LifecycleState.STOPPING.equals(state) || LifecycleState.STOPPED.equals(state)) {
            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyStopped", toString()), e);
            } else if (log.isInfoEnabled()) {
                log.info(sm.getString("lifecycleBase.alreadyStopped", toString()));
            }

            return;
        }

        // `NEW`状态时，直接将状态变更为`STOPPED`
        if (state.equals(LifecycleState.NEW)) {
            state = LifecycleState.STOPPED;
            return;
        }

        // stop()的执行，必须要是`STARTED`和`FAILED`
        if (!state.equals(LifecycleState.STARTED) && !state.equals(LifecycleState.FAILED)) {
            invalidTransition(Lifecycle.BEFORE_STOP_EVENT);
        }

        try {
            // `FAILED`时，直接触发BEFORE_STOP_EVENT事件
            if (state.equals(LifecycleState.FAILED)) {
                // Don't transition to STOPPING_PREP as that would briefly mark the
                // component as available but do ensure the BEFORE_STOP_EVENT is
                // fired
                fireLifecycleEvent(BEFORE_STOP_EVENT, null);
            }
            // 设置状态为STOPPING_PREP
            else {
                setStateInternal(LifecycleState.STOPPING_PREP, null, false);
            }

            // stop逻辑，抽象方法，组件自行实现
            stopInternal();

            // Shouldn't be necessary but acts as a check that sub-classes are
            // doing what they are supposed to.
            if (!state.equals(LifecycleState.STOPPING) && !state.equals(LifecycleState.FAILED)) {
                invalidTransition(Lifecycle.AFTER_STOP_EVENT);
            }

            // 设置状态为STOPPED
            setStateInternal(LifecycleState.STOPPED, null, false);
        } catch (Throwable t) {
            handleSubClassException(t, "lifecycleBase.stopFail", toString());
        } finally {
            if (this instanceof Lifecycle.SingleUse) {
                // Complete stop process first
                setStateInternal(LifecycleState.STOPPED, null, false);
                destroy();
            }
        }
    }

    /**
     * Sub-classes must ensure that the state is changed to
     * {@link LifecycleState#STOPPING} during the execution of this method.
     * Changing state will trigger the {@link Lifecycle#STOP_EVENT} event.
     *
     * @throws LifecycleException Stop error occurred
     */
    protected abstract void stopInternal() throws LifecycleException;

    @Override
    public final synchronized void destroy() throws LifecycleException {
        // `FAILED`状态时，直接触发stop()逻辑
        if (LifecycleState.FAILED.equals(state)) {
            try {
                // Triggers clean-up
                stop();
            } catch (LifecycleException e) {
                // Just log. Still want to destroy.
                log.error(sm.getString("lifecycleBase.destroyStopFail", toString()), e);
            }
        }

        // `DESTROYING`和`DESTROYED`时，忽略destroy的执行
        if (LifecycleState.DESTROYING.equals(state) || LifecycleState.DESTROYED.equals(state)) {
            if (log.isDebugEnabled()) {
                Exception e = new LifecycleException();
                log.debug(sm.getString("lifecycleBase.alreadyDestroyed", toString()), e);
            } else if (log.isInfoEnabled() && !(this instanceof Lifecycle.SingleUse)) {
                // Rather than have every component that might need to call
                // destroy() check for SingleUse, don't log an info message if
                // multiple calls are made to destroy()
                log.info(sm.getString("lifecycleBase.alreadyDestroyed", toString()));
            }
            return;
        }

        // 非法状态判断
        if (!state.equals(LifecycleState.STOPPED) && !state.equals(LifecycleState.FAILED) &&
            !state.equals(LifecycleState.NEW) && !state.equals(LifecycleState.INITIALIZED)) {
            invalidTransition(Lifecycle.BEFORE_DESTROY_EVENT);
        }

        try {
            // destroy前状态设置
            setStateInternal(LifecycleState.DESTROYING, null, false);
            // 抽象方法，组件自行实现
            destroyInternal();
            // destroy后状态设置
            setStateInternal(LifecycleState.DESTROYED, null, false);
        } catch (Throwable t) {
            handleSubClassException(t, "lifecycleBase.destroyFail", toString());
        }
    }

    /**
     * Sub-classes implement this method to perform any instance destruction
     * required.
     *
     * @throws LifecycleException If the destruction fails
     */
    protected abstract void destroyInternal() throws LifecycleException;

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleState getState() {
        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStateName() {
        return getState().toString();
    }

    /**
     * Provides a mechanism for sub-classes to update the component state.
     * Calling this method will automatically fire any associated
     * {@link Lifecycle} event. It will also check that any attempted state
     * transition is valid for a sub-class.
     *
     * @param state The new state for this component
     * @throws LifecycleException when attempting to set an invalid state
     */
    protected synchronized void setState(LifecycleState state) throws LifecycleException {
        setStateInternal(state, null, true);
    }

    /**
     * Provides a mechanism for sub-classes to update the component state.
     * Calling this method will automatically fire any associated
     * {@link Lifecycle} event. It will also check that any attempted state
     * transition is valid for a sub-class.
     *
     * @param state The new state for this component
     * @param data  The data to pass to the associated {@link Lifecycle} event
     * @throws LifecycleException when attempting to set an invalid state
     */
    protected synchronized void setState(LifecycleState state, Object data) throws LifecycleException {
        setStateInternal(state, data, true);
    }

    /**
     * 设置组件状态
     */
    private synchronized void setStateInternal(LifecycleState state, Object data, boolean check) throws LifecycleException {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("lifecycleBase.setState", this, state));
        }

        // 检查服务状态
        if (check) {
            // Must have been triggered by one of the abstract methods (assume
            // code in this class is correct)
            // null is never a valid state
            if (state == null) {
                invalidTransition("null");
                // Unreachable code - here to stop eclipse complaining about
                // a possible NPE further down the method
                return;
            }

            // Any method can transition to failed
            // startInternal() permits STARTING_PREP to STARTING
            // stopInternal() permits STOPPING_PREP to STOPPING and FAILED to
            // STOPPING
            if (!(state == LifecycleState.FAILED ||
                // 组件状态启动前 并且 当前状态是启动中
                (this.state == LifecycleState.STARTING_PREP && state == LifecycleState.STARTING) ||
                // 组件状态停止前 并且 当前状态是停止中
                (this.state == LifecycleState.STOPPING_PREP && state == LifecycleState.STOPPING) ||
                // 组件状态失败 并且 当前状态是停止中
                (this.state == LifecycleState.FAILED && state == LifecycleState.STOPPING))) {
                // No other transition permitted
                invalidTransition(state.name());
            }
        }

        this.state = state;
        // 当前状态关联的生命周期事件类型
        String lifecycleEvent = state.getLifecycleEvent();
        if (lifecycleEvent != null) {
            fireLifecycleEvent(lifecycleEvent, data);
        }
    }

    /**
     * 抛出指定类型异常
     */
    private void invalidTransition(String type) throws LifecycleException {
        String msg = sm.getString("lifecycleBase.invalidTransition", type, toString(), state);
        throw new LifecycleException(msg);
    }

    private void handleSubClassException(Throwable t, String key, Object... args) throws LifecycleException {
        setStateInternal(LifecycleState.FAILED, null, false);
        ExceptionUtils.handleThrowable(t);
        String msg = sm.getString(key, args);
        if (getThrowOnFailure()) {
            if (!(t instanceof LifecycleException)) {
                t = new LifecycleException(msg, t);
            }
            throw (LifecycleException) t;
        } else {
            log.error(msg, t);
        }
    }
}
