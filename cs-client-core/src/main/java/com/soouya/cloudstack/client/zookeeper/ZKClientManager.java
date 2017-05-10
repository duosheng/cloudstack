package com.soouya.cloudstack.client.zookeeper;

import org.apache.curator.framework.CuratorFramework;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * zk client管理类 统一的zkclient获取
 * Created by xuyuli on 17-5-2.
 */
public class ZKClientManager {

    private static ConcurrentMap<String, CuratorFramework> zkClientMap = new ConcurrentHashMap<String, CuratorFramework>();

    private ZKClientManager(){};

    public static CuratorFramework getClient(String ip){
        if(ip == null || ip.trim().length() == 0){
            throw new IllegalArgumentException("zk ip not null!");
        }

        synchronized (ip) {
            if (!zkClientMap.containsKey(ip)) {
                CuratorFramework client = ZKClient.create(ip);

                CuratorFramework oldClient = zkClientMap.putIfAbsent(ip, client);
                if (oldClient != null) {
                    //close old client
                    oldClient.close();
                }

                return client;
            }
        }
        return zkClientMap.get(ip);
    }

}
