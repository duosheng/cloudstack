package com.soouya.cloudstack;

/**
 * 组件的生命周期控制接口
 */
public interface ILifecycle
{
  public abstract void start();

  public abstract void stop();

  public abstract boolean isStarted();
}