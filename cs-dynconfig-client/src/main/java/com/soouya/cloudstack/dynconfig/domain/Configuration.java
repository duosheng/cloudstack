package com.soouya.cloudstack.dynconfig.domain;

import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 配置对象
 * Created by xuyuli on 17-5-2.
 */
public class Configuration implements Serializable{

    private static final long serialVersionUID = 5578228351252777377L;

    /** 配置项所在组 */
    private String group;
    /** 配置项key */
    private String dataId;
    /** 配置项值 */
    private String config;
    /** 集群下的所有节点 */
    private List<String> nodes;
    /** 服务器集群变化的事件 */
    private PathChildrenCacheEvent pathChildrenCacheEvent;
    /** 节点变化的详情 */
    private Map<String, String> datas;

    private String appName;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public PathChildrenCacheEvent getPathChildrenCacheEvent() {
        return pathChildrenCacheEvent;
    }

    public void setPathChildrenCacheEvent(PathChildrenCacheEvent pathChildrenCacheEvent) {
        this.pathChildrenCacheEvent = pathChildrenCacheEvent;
    }

    public Map<String, String> getDatas() {
        return datas;
    }

    public void setDatas(Map<String, String> datas) {
        this.datas = datas;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "group='" + group + '\'' +
                ", dataId='" + dataId + '\'' +
                ", config='" + config + '\'' +
                ", nodes=" + nodes +
                ", pathChildrenCacheEvent=" + pathChildrenCacheEvent +
                ", datas=" + datas +
                ", appName='" + appName + '\'' +
                '}';
    }
}
