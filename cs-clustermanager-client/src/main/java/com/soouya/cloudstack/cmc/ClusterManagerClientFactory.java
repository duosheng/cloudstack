package com.soouya.cloudstack.cmc;


import com.soouya.cloudstack.cmc.impl.ZKClusterManagerClient;

/**
 * ClusterManagerClient工厂类
 * <p/>
 * 创建时间: 14-8-1 下午7:34<br/>
 *
 * @author qyang
 * @since v0.0.1
 */
public class ClusterManagerClientFactory {
    private static class ClusterManagerClientFactoryHolder{
        private static final IClusterManagerClient instance = new ZKClusterManagerClient();
    }

    private ClusterManagerClientFactory(){}

    /**
     * 生成client单例对象
     * @return
     */
    public static IClusterManagerClient createClient(){
        return ClusterManagerClientFactoryHolder.instance;
    }

    /**
     * 根据 不同的 zkip 获取 不同的 IClusterManagerClient
     * @param zkIp
     * @return
     */
    public static IClusterManagerClient createClient(String zkIp){
        return new ZKClusterManagerClient();
    }
}
