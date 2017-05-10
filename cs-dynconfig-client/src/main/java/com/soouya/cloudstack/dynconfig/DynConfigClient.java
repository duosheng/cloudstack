package com.soouya.cloudstack.dynconfig;

import com.soouya.cloudstack.client.zookeeper.ZKClient;
import com.soouya.cloudstack.client.zookeeper.ZKClientManager;
import com.soouya.cloudstack.client.zookeeper.recover.ZKRecoverUtil;
import com.soouya.cloudstack.context.CloudContextFactory;
import com.soouya.cloudstack.dynconfig.domain.Configuration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.framework.recipes.cache.*;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 动态配置中心客户端
 * 获取动态配置信息主动从zk获取，但考虑到zk server故障时的处理 需要本地存储容灾（zk client是否已经实现了该功能）
 * Created by xuyuli on 17-5-2.
 */
public class DynConfigClient {

    public static final Logger logger = LoggerFactory.getLogger(DynConfigClient.class);

    private volatile boolean isStart = false;
    //private CuratorFramework zkClient = null;
    public static final String PATH_FORMAT = "/configs/%s/%s/%s";
    /** product/bizsystem/group/dataid */
    public static final String CLOUD_PATH_FORMAT = "/configs/%s/%s/%s/%s";


    private List<IChangeListener> listeners;
//    private ConcurrentMap<String, PathChildrenCache> pathChildrenCacheMap = Maps.newConcurrentMap();
    private ConcurrentMap<String, PathChildrenCache> pathChildrenCacheMap = new ConcurrentHashMap();
    private ConcurrentMap<String, Object> recoverDataCache = new ConcurrentHashMap();

    /** 指定的zk ip */
    private String zkIp = null;
    public DynConfigClient() {}
    public DynConfigClient(String zkIp) {
        this.zkIp = zkIp;
    }

    /**
     * 初始化zookeeper客户端   按默认zk ip走
     */
    public void init(){
        if(!isStart) {
            //注册内置监听器
            innerRegisterListeners(ZKClient.getClient());
        }
    }

    /**
     * 对 指定ip的zk进行相关初始化
     * @param isMulti
     */
    public void init(boolean isMulti){
        if(!isStart && isMulti) {
            //注册内置监听器
            innerRegisterListeners(ZKClientManager.getClient(zkIp));
        }
    }

    /**
     * 从zk server获取配置信息 （主动调用）
     * @param group
     * @param dataId
     * @return
     * @throws Exception   抛出异常，由上层业务处理(一般退出应用，启动失败)
     */
    public String getConfig(String group, String dataId) throws Exception {
        return getConfig(CloudContextFactory.getCloudContext().getProductCode(), CloudContextFactory.getCloudContext().getApplicationName(), group, dataId);
    }

    /**
     * 获取特定appName的配置项值
     * @param appName
     * @param group
     * @param dataId
     * @return
     * @throws Exception
     */
    public String getConfig(String appName, String group, String dataId) throws Exception {
        String path = String.format(PATH_FORMAT, appName, group, dataId);

        return getConfig(path);
    }

    /**
     *
     * @param productCode 产品线code
     * @param appName
     * @param group
     * @param dataId
     * @return
     * @throws Exception
     */
    public String getConfig(String productCode, String appName, String group, String dataId) throws Exception {
        String path = null;
        if(productCode == null) {
            path = String.format(PATH_FORMAT, appName, group, dataId);
        } else {
            path = String.format(CLOUD_PATH_FORMAT, productCode, appName, group, dataId);
        }

        return getConfig(path);
    }

    /**
     * 获取特定路径的配置项值
     * @param path
     * @return
     * @throws Exception
     */
    public final String getConfig(final String path) throws Exception {
        String recoverPath = null;
        if(zkIp != null && zkIp.trim().length() > 0){
            recoverPath = "/" + zkIp + path;
        } else {
            recoverPath = path;
        }
        byte[] bytes = null;
        boolean isSucc = false;
        try {
            CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);
            bytes = client.getData().forPath(path);
            isSucc = true;
        }catch(Exception e){
            //从本地获取配置数据
            bytes = ZKRecoverUtil.loadRecoverData(recoverPath);
        }
        ZKRecoverUtil.doRecover(bytes, recoverPath, recoverDataCache);

        //ingore NPE.
        if (bytes == null) {
            return "";
        }
        return new String(bytes);
    }

    /**
     * 获取特定路径的子节点
     * @param path
     * @return
     * @throws Exception
     */
    public List<String> getNodes(String path) throws Exception {
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);
        List<String> nodes = client.getChildren().forPath(path);
        return nodes;
    }

    /**
     * 为特定组下特定的配置项注册监听器
     * @param group
     * @param dataId
     * @param listener
     */
    public void registerListeners(final String group, final String dataId, final IChangeListener listener) {
        registerListeners(CloudContextFactory.getCloudContext().getApplicationName(), group, dataId, listener);
    }

    /**
     * @deprecated 支持多产品模式
     * @param appName
     * @param group
     * @param dataId
     * @param listener
     */
    public void registerListeners(final String appName, final String group, final String dataId, final IChangeListener listener) {
        String path = String.format(PATH_FORMAT, appName, group, dataId);

        doRegisterListeners(null, appName, path, group, dataId, listener);
    }

    public void registerListeners(final String productCode, final String appName, final String group, final String dataId, final IChangeListener listener) {
        String path = null;
        if(productCode == null) {
            path = String.format(PATH_FORMAT, appName, group, dataId);
        } else {
            path = String.format(CLOUD_PATH_FORMAT, productCode, appName, group, dataId);
        }

        doRegisterListeners(productCode, appName, path, group, dataId, listener);
    }

    /**
     * 清空该path的所有监听器    目前仅用于 集群发现
     * @param path
     */
    public void removeListeners(final String path) {
        //清空此cache的监听器（only one）
        pathChildrenCacheMap.get(path).getListenable().clear();
    }

    /**
     * 注册 集群发现path的监听器      目前仅用于 集群发现
     * @param path
     * @param listener
     */
    public void registerListeners(final String path, final IChangeListener listener) {
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);

        //使用Curator的NodeCache来做ZNode的监听，不用我们自己实现重复监听
        if(pathChildrenCacheMap.get(path) == null) {
            final PathChildrenCache cache = new PathChildrenCache(client, path, true);
            pathChildrenCacheMap.putIfAbsent(path, cache);
            cache.getListenable().addListener(new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                    List<String> nodes = getNodes("/servers");
                    Configuration configuration = new Configuration();
                    configuration.setPathChildrenCacheEvent(event);
                    configuration.setNodes(nodes);

                    listener.receiveConfigInfo(configuration);
                }
            });
            try {
                cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
            } catch (Exception e) {
                logger.error("Start NodeCache error for path: {}, error info: {}", path, e.getMessage());
            }
        }
    }

    private final void doRegisterListeners(final String productCode, final String appName, final String path, final String group, final String dataId, final IChangeListener listener) {
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);

        //
        String cloud_path = path;
        try {
            if (client.checkExists().forPath(path) == null && productCode != null) { //cloud mode
                cloud_path = String.format(CLOUD_PATH_FORMAT, productCode, appName, group, dataId);
            }
        }catch (Exception e){
            logger.error("doRegisterListeners error", e);
        }

        final String cloud_path_final = cloud_path;
        //使用Curator的NodeCache来做ZNode的监听，不用我们自己实现重复监听
        final NodeCache cache = new NodeCache(client, cloud_path);
        cache.getListenable().addListener(new NodeCacheListener() {
            @Override
            public void nodeChanged() throws Exception {
                byte[] data = null;
                try {
                    data = cache.getCurrentData().getData();
                }catch (Exception e){
                    logger.warn("{} loadRecoverData ", cloud_path_final);
                    data = ZKRecoverUtil.loadRecoverData(cloud_path_final);
                }
                //TODO 对传回的值进行比较，只有真正的改变才进行对应的业务回调处理？

                //具体的业务处理
                if (data != null) {
                    //
                    Configuration configuration = new Configuration();
                    configuration.setConfig(new String(data));
                    configuration.setGroup(group);
                    configuration.setDataId(dataId);

                    //对动态变化的数据进行容灾处理
                    ZKRecoverUtil.doRecover(data, cloud_path_final, recoverDataCache);
                    listener.receiveConfigInfo(configuration);
                }
            }
        });
        try {
            cache.start(true);
        } catch (Exception e) {
            logger.error("Start NodeCache error for path: {}, error info: {}", cloud_path_final, e.getMessage());
        }
    }

    /**
     * 原生zk监听器注册
     * @param zkClient
     */
    private void innerRegisterListeners(CuratorFramework zkClient) {
        zkClient.getConnectionStateListenable().addListener(new ConnectionStateListener() {
            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                logger.info("CuratorFramework state changed: {}", newState);
                if(newState == ConnectionState.CONNECTED || newState == ConnectionState.RECONNECTED){




//                    for(IChangeListener listener : listeners){
//                        //获取key 执行对应的listener
//
//
//                        listener.receiveConfigInfo().executor(client);
//                        logger.info("Listener {} executed!", listener.getClass().getName());
//                    }
                }
            }
        });

        zkClient.getUnhandledErrorListenable().addListener(new UnhandledErrorListener() {
            @Override
            public void unhandledError(String message, Throwable e) {
                logger.info("CuratorFramework unhandledError: {}", message);
            }
        });
    }


    public void setConfig(String path , String value) throws Exception{
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);

        if (client.checkExists().forPath(path) == null ) {
            createConfig(path,value);
        } else {
            client.setData().forPath(path, value.getBytes("utf-8"));
        }
    }

    public void createConfig(String path, String value) throws Exception {
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);
        client.create().creatingParentsIfNeeded().forPath(path, value.getBytes("utf-8"));
    }

    public void deleteConfig(String path) throws Exception {
        CuratorFramework client = zkIp == null ? ZKClient.getClient() : ZKClientManager.getClient(zkIp);
        client.delete().forPath(path);
    }
}
