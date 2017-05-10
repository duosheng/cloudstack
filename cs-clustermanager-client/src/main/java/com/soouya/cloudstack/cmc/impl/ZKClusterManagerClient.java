package com.soouya.cloudstack.cmc.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.soouya.cloudstack.client.zookeeper.ZKClient;
import com.soouya.cloudstack.cmc.ClusterManagerClientFactory;
import com.soouya.cloudstack.cmc.IClusterManagerClient;
import com.soouya.cloudstack.context.CloudContextFactory;
import com.soouya.cloudstack.dynconfig.DynConfigClientFactory;
import com.soouya.cloudstack.dynconfig.IChangeListener;
import com.soouya.cloudstack.dynconfig.domain.Configuration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 注册(使用zk管理）
 * Created by qyang on 14-7-4.
 */
public class ZKClusterManagerClient implements IClusterManagerClient {
    public static final Logger logger = LoggerFactory.getLogger(ZKClusterManagerClient.class);
    private volatile boolean isStart = false;

    private static final String ROOT_PATH_FORMAT = "/servers/%s";
    private static final String CLOUD_ROOT_PATH_FORMAT = "/servers/%s/%s";
    private static final String ROOT_PATH_PREFIX = "/servers";
    private static final String DEFAULT_SERVICE_NAME = "cluster-ip";
    /** 云管理中心域名 */
//    public static final String DEFAULT_DOMAIN_NAME = "mc.zk.thinkjoy.cn";

    /** 当前的业务系统列表 */
    private List<String> bizSystems = Lists.newArrayList();
    /** 已经进行了目录监听的业务系统集合 */
    private Set<String> regedBizSystems = Sets.newHashSet();
    /** 被删除的业务系统 */
    private Set<String> removedBizSystems = Sets.newHashSet();
    /** 是否对服务发现的根目录进行了监听 */
    private volatile boolean isAlreadyReg = false;
    @Override
    public synchronized ZKAliveServer register(String appName) {
        return register(null, appName);
    }

    @Override
    public synchronized ZKAliveServer register(String productCode, String appName) {

        final CuratorFramework client = ZKClient.getClient();
        String path = null;
        if (productCode == null) {
            path = String.format(ROOT_PATH_FORMAT, CloudContextFactory.getCloudContext().getApplicationName());
        } else {
            path = String.format(CLOUD_ROOT_PATH_FORMAT, productCode, CloudContextFactory.getCloudContext().getApplicationName());
        }


        Map<String, String> serverMetadata = Maps.newHashMap();
        serverMetadata.put("number", CloudContextFactory.getCloudContext().getApplicationName());
        serverMetadata.put("name", CloudContextFactory.getCloudContext().getApplicationZhName());
        serverMetadata.put("owner", CloudContextFactory.getCloudContext().getOwner());
        serverMetadata.put("ownerContact", CloudContextFactory.getCloudContext().getOwnerContact());
        serverMetadata.put("description", CloudContextFactory.getCloudContext().getDescription());
        serverMetadata.put("port", String.valueOf(CloudContextFactory.getCloudContext().getPort()));
        serverMetadata.put("httpPort", String.valueOf(CloudContextFactory.getCloudContext().getHttpPort()));
        serverMetadata.put("httpPort", String.valueOf(CloudContextFactory.getCloudContext().getHttpPort()));
        serverMetadata.put("product", CloudContextFactory.getCloudContext().getProduct());
        serverMetadata.put("productCode", CloudContextFactory.getCloudContext().getProductCode());

        ZKAliveServer server =  innerRegister(client, path);

        //增加业务系统节点描述
        try {
            Thread.sleep(10);
//            ZKClient.getClient().setData().forPath(path, JSONObject.fromObject(serverMetadata).toString().getBytes());
            ZKClient.getClient().setData().forPath(path, JSON.toJSONBytes(serverMetadata, SerializerFeature.PrettyFormat));
        } catch (Exception e) {
            logger.error("register error", e);
            System.exit(-1);
        }
        return server;
    }

    @Override
    public ZKAliveServer register() {
        return register(CloudContextFactory.getCloudContext().getProductCode(), CloudContextFactory.getCloudContext().getApplicationName());
    }

    /**
     * 把当前服务注册到zk
     * @param client
     */
    private ZKAliveServer innerRegister(CuratorFramework client, String path) {
        ZKAliveServer server = null;
        try {
            server = new ZKAliveServer(client, path, DEFAULT_SERVICE_NAME, "cluster server ip");
            server.start();
        } catch (Exception e) {
            logger.error("", e);
//            System.exit(-1);
        }
        return server;

    }

    @Override
    public List<String> getLiveServers(String appName) {
        return this.getLiveServers(null, appName);
    }

    @Override
    public List<String> getLiveServers(String productCode, String appName) {
        JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<InstanceDetails>(InstanceDetails.class);
        String path = null;
        if(productCode == null) {
            path = String.format(ROOT_PATH_FORMAT, appName);
        } else{
            path = String.format(CLOUD_ROOT_PATH_FORMAT, productCode, appName);
        }
        ServiceDiscovery<InstanceDetails> serviceDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class).client(ZKClient.getClient()).basePath(path).serializer(serializer).build();
        try {
            serviceDiscovery.start();
        } catch (Exception e) {
            logger.error("serviceDiscovery start error!", e);
        }

        List<String> servers = Lists.newArrayList();
        try{
            Collection<ServiceInstance<InstanceDetails>> instances = serviceDiscovery.queryForInstances(DEFAULT_SERVICE_NAME);

            for ( ServiceInstance<InstanceDetails> instance : instances ){
                servers.add(instance.getAddress());
            }
            serviceDiscovery.close();
        } catch (Exception e) {
            //only logger
            logger.error("getBizSystems error!", e);
        }
        return servers;
    }

    @Override
    public List<String> getLiveServers() {
        return getLiveServers(CloudContextFactory.getCloudContext().getApplicationName());
    }

    @Override
    public List<String> getBizSystems() {
        try {
            List<String> tempBizSystems = DynConfigClientFactory.getClient().getNodes(ROOT_PATH_PREFIX);
            bizSystems = tempBizSystems;
            return bizSystems;
        } catch (Exception e) {
            //only logger
            logger.error("getBizSystems error!", e);
        }

        return bizSystems;
    }

    @Override
    public String getBizSystemMetadata(String appName) {
        return getBizSystemMetadata(null, appName);
    }

    @Override
    public String getBizSystemMetadata(String productCode, String appName) {
        try {
            if(productCode == null) {
                return DynConfigClientFactory.getClient().getConfig(String.format(ROOT_PATH_FORMAT, appName));
            } else {
                return DynConfigClientFactory.getClient().getConfig(String.format(CLOUD_ROOT_PATH_FORMAT, productCode, appName));
            }
        } catch (Exception e) {
            logger.error("getBizSystemMetadata error for [" + appName + "]", e);
        }

        return null;
    }


    @Override
    public synchronized void initListenerServerChange(final IChangeListener listener) {
        //先注册根目录
        if(!isAlreadyReg){//没有注册过
            logger.warn("register /servers listener");
            DynConfigClientFactory.getClient().registerListeners(ROOT_PATH_PREFIX, new IChangeListener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor();
                }

                @Override
                public void receiveConfigInfo(final Configuration configuration) {
                    getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            logger.warn("bizsystem change {}", configuration);
                            removedBizSystems.clear();
                            List<String> newNodes = configuration.getNodes();
                            for(String node : bizSystems){
                                if(!newNodes.contains(node)) { //新的里面不存在表示被删除
                                    removedBizSystems.add(node);
                                }
                            }
                            bizSystems = newNodes;

                            if(configuration.getPathChildrenCacheEvent() != null) {
                                //从path获取appName
                                String[] splits = configuration.getPathChildrenCacheEvent().getData().getPath().split("/");
                                if (splits != null && splits.length > 2) {
                                    configuration.setAppName(splits[2]);
                                }
                            }

                            //需要触发上层告诉有新应用上线
                            Map<String, String> datas = Maps.newHashMap();
                            if(configuration.getPathChildrenCacheEvent() != null
                                    && PathChildrenCacheEvent.Type.CHILD_REMOVED == configuration.getPathChildrenCacheEvent().getType()) {
                                datas.put(FIRST_ADD, "remove");

                            } else {
                                datas.put(FIRST_ADD, "add");
                            }
                            configuration.setDatas(datas);

                            if(PathChildrenCacheEvent.Type.CHILD_ADDED != configuration.getPathChildrenCacheEvent().getType()) {//不为add的才触发事件 因为add的时候还没有业务系统元数据
                                listener.receiveConfigInfo(configuration);
                            }

                            //重新注册
                            initListenerServerChange(listener);
                        }
                    });
                }
            });

            isAlreadyReg = true;
        }

        //删除的节点监听器清空，防止内存泄露
        String path = null;
        for(String node : removedBizSystems){
            path = String.format(ROOT_PATH_FORMAT, node) + "/" + DEFAULT_SERVICE_NAME;

            DynConfigClientFactory.getClient().removeListeners(path);
            regedBizSystems.remove(path);

            logger.warn("remove server {}", node);
        }

        //进行节点数据的监听
        List<String> tempBizSystems = getBizSystems();
        for(String appName : tempBizSystems){
            path = String.format(ROOT_PATH_FORMAT, appName) + "/" + DEFAULT_SERVICE_NAME;


            if(!regedBizSystems.contains(path)) {  //之前未监听的才监听
                logger.warn("register {} listener", path);
                DynConfigClientFactory.getClient().registerListeners(path, new IChangeListener() {
                    @Override
                    public Executor getExecutor() {
                        return Executors.newSingleThreadExecutor();
                    }

                    @Override
                    public void receiveConfigInfo(final Configuration configuration) {
                        getExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                Map<String, String> datas = Maps.newHashMap();
                                if(configuration.getPathChildrenCacheEvent() != null
                                        && configuration.getPathChildrenCacheEvent().getData() != null
                                        && configuration.getPathChildrenCacheEvent().getData().getPath() != null){
                                    String[] splits = configuration.getPathChildrenCacheEvent().getData().getPath().split("/");
                                    if(splits != null && splits.length > 2) {
                                        configuration.setAppName(splits[2]);
                                    }
                                    String data = new String(configuration.getPathChildrenCacheEvent().getData().getData());
//                                    String changeAddress = JSONObject.fromObject(data).getString("address");
                                    String changeAddress = (String) JSON.parseObject(new String(configuration.getPathChildrenCacheEvent().getData().getData()), Map.class).get("address");
                                    if(configuration.getPathChildrenCacheEvent().getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {
                                        datas.put("add", changeAddress);
                                        configuration.setDatas(datas);

                                        //real handle
                                        listener.receiveConfigInfo(configuration);
                                    } else if(configuration.getPathChildrenCacheEvent().getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
                                        datas.put("remove", changeAddress);
                                        configuration.setDatas(datas);

                                        //如果 还有相同的节点数据 ，不触发上层处理
                                        if(!ClusterManagerClientFactory.createClient().getLiveServers(configuration.getAppName()).contains(changeAddress)){
                                            //real handle
                                            listener.receiveConfigInfo(configuration);
                                        } else {
                                            logger.warn("还有相同的节点数据 ，不触发上层处理");
                                        }
                                    } else if(configuration.getPathChildrenCacheEvent().getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED) {
                                        datas.put("update", new String(configuration.getPathChildrenCacheEvent().getData().getData()));
                                        configuration.setDatas(datas);

                                        //real handle
                                        listener.receiveConfigInfo(configuration);
                                    } else {
                                        logger.error("unsupport path child change {}", configuration);
                                    }
                                }

                            }
                        });
                    }
                });
            }
            regedBizSystems.add(path);
        }
    }







}
