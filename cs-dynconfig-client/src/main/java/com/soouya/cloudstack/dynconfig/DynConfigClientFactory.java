package com.soouya.cloudstack.dynconfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by xuyuli on 17-5-2.
 * DynConfigClient 工厂类
 */
public class DynConfigClientFactory {

    private static class DynConfigClientHolder{
        private static DynConfigClient instance = new DynConfigClient();
    }

    private DynConfigClientFactory(){}

    private static ConcurrentMap<String, DynConfigClient> dynConfigClientMap = new ConcurrentHashMap();

    /**
     * 获取DynConfigClient实例(单例)
     * @return
     */
    public static DynConfigClient getClient(){
        DynConfigClientHolder.instance.init();
        return  DynConfigClientHolder.instance;
    }

    public static DynConfigClient getClient(final String zkIp){
        synchronized (zkIp){
            if(dynConfigClientMap.get(zkIp) == null) {
                DynConfigClient client = new DynConfigClient(zkIp);
                client.init(true);
                dynConfigClientMap.put(zkIp, client);
            }

            return dynConfigClientMap.get(zkIp);
        }
    }

}
