package com.soouya.cloudstack.context;

import com.soouya.cloudstack.ILifecycle;
import com.soouya.cloudstack.context.impl.CloudContextImpl;

/**
 * ICloudContext 工厂类
 * Created by xuyuli on 17-4-28.
 */
public class CloudContextFactory {

    private static class CloudContextHolder{
        private static final ICloudContext instance = new CloudContextImpl();
    }

    public static ICloudContext getCloudContext(){
        ((ILifecycle) CloudContextHolder.instance).start();
        return CloudContextHolder.instance;
    }
}
