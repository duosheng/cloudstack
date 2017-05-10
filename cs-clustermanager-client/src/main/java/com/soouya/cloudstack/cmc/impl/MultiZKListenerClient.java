package com.soouya.cloudstack.cmc.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.soouya.cloudstack.cmc.ClusterManagerClientFactory;
import com.soouya.cloudstack.dynconfig.DynConfigClientFactory;
import com.soouya.cloudstack.dynconfig.IChangeListener;
import com.soouya.cloudstack.dynconfig.domain.Configuration;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 多zk client监听
 * <p/>
 * 创建时间: 15/6/23 下午3:11<br/>
 *
 * @author qyang
 * @since v0.0.1
 */
public class MultiZKListenerClient {
    public static final Logger logger = LoggerFactory.getLogger(ZKClusterManagerClient.class);
    private volatile boolean isStart = false;

    public static final String FIRST_ADD = "firstadd";

    private static final String ROOT_PATH_FORMAT = "/servers/%s";
    private static final String ROOT_PATH_PREFIX = "/servers";
    private static final String DEFAULT_SERVICE_NAME = "cluster-ip";

    /** 当前的业务系统列表 */
    private List<String> bizSystems = Lists.newArrayList();
    /** 已经进行了目录监听的业务系统集合 */
    private Set<String> regedBizSystems = Sets.newHashSet();
    /** 被删除的业务系统 */
    private Set<String> removedBizSystems = Sets.newHashSet();
    /** 是否对服务发现的根目录进行了监听 */
    private ConcurrentMap<String, Boolean> isAlreadyRegMap = new ConcurrentHashMap();

    public MultiZKListenerClient(){};

    public static class MultiZKListenerClientHolder{
        private static MultiZKListenerClient instance = new MultiZKListenerClient();
    }

    public static MultiZKListenerClient getInstance(){
        return MultiZKListenerClientHolder.instance;
    }

    /**
     * 对任意的业务 云体系zk进行数据节点监听   除去当前默认的,因为在其他地方已注册
     * @param zkIp
     * @param listener
     */
    public void initListenerServerChange(final String zkIp, final IChangeListener listener) {
        //先注册根目录
        if(!isAlreadyRegMap.get(zkIp)){//没有注册过
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
                            initListenerServerChange(zkIp, listener);
                        }
                    });
                }
            });

            isAlreadyRegMap.putIfAbsent(zkIp, Boolean.valueOf(true));
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
        List<String> tempBizSystems = getBizSystems(zkIp);
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

    public List<String> getBizSystems(String zkIp) {
        try {
            List<String> tempBizSystems = DynConfigClientFactory.getClient(zkIp).getNodes(ROOT_PATH_PREFIX);
            bizSystems = tempBizSystems;
            return bizSystems;
        } catch (Exception e) {
            //only logger
            logger.error("getBizSystems error!", e);
        }

        return bizSystems;
    }

}
