package com.soouya.cloudstack.client.zookeeper;

import com.soouya.cloudstack.AbstractLifecycle;
import com.soouya.cloudstack.utils.ConfigLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * zk客户端
 */
public class ZKClient extends AbstractLifecycle {

    public static final Log logger = LogFactory.getLog(ZKClient.class);

    private volatile static CuratorFramework zkClient = null;

    private ZKClient(){};

    @Override
    protected void doStart() {
        this.isStart = true;
        String ip = ConfigLoader.getInstance().getProperty("zk.ip");
        String port = ConfigLoader.getInstance().getProperty("zk.port");


        String url = ip + ":" + port;
        zkClient = CuratorFrameworkFactory.newClient(url, new ExponentialBackoffRetry(1000, 3));

        zkClient.start();
        logger.warn("ZKClient start success!");
    }

    /**
     * 根据ip获取zk client, 可以直接调用该方法，但不建议 请使用 ZKClientManager 调用
     * @param ip
     * @return
     */
    public static CuratorFramework create(String ip){
        logger.warn(" start conn zk server {"+ip+"} ");

        CuratorFramework newClient = null;
        synchronized (ip){
            String url = ip + ":" + ConfigLoader.getInstance().getProperty("zk.port");
            newClient = CuratorFrameworkFactory.newClient(url, new ExponentialBackoffRetry(1000, 3));
            //innerRegisterListeners(zkClient);

            newClient.start();
        }

        logger.warn("  conn zk server { "+ip+"} success!");
        return newClient;
    }

    @Override
    public void stop() {
        if(zkClient != null) {
            zkClient.close();
        }
        //throw new RuntimeException("un implemented");
    }

    private static class ZKClientHolder{
        private static final ZKClient instance = new ZKClient();
    }

    /**
     * 获取zk客户端实例（单例）
     * @return
     */
    public static CuratorFramework getClient(){
        //初始化client
        ZKClientHolder.instance.start();
        return zkClient;
    }
}