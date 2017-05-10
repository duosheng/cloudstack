package com.soouya.cloudstack;

/**
 * 生命周期基类
 */
public abstract class AbstractLifecycle implements ILifecycle {

    protected volatile boolean isStart = false;

    /**
     * 避免子类组件启动多次
     */
    @Override
    public void start() {
        if (!this.isStart) {
            doStart();
            this.isStart = true;
        }
    }

    @Override
    public boolean isStarted() {
        return this.isStart;
    }

    /**
     * 字类实现的开始方法
     */
    protected abstract void doStart();
}