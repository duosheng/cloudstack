package com.soouya.cloudstack.dynconfig;

import com.soouya.cloudstack.dynconfig.domain.Configuration;

import java.util.concurrent.Executor;

/**
 * 数据改变监听器
 * Created by xuyuli on 17-5-2.
 */
public interface IChangeListener {

    /**
     * 返回线程池执行器
     * @return
     */
    public Executor getExecutor();


    /**
     * 接收到配置项文件处理
     * @param configuration
     */
    public void receiveConfigInfo(final Configuration configuration);


}
