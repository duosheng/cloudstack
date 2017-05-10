package com.soouya.cloudstack.context;

/**
 * 被管理server上下文
 * Created by xuyuli on 17-4-28.
 */
public interface ICloudContext {

    /**
     * 应用英文名
     * @return
     */
    String getApplicationName();

    String getId();

    String getApplicationZhName();

    String getAppType();

    /**
     * 负责人
     * @return
     */
    String getOwner();

    String getOwnerContact();

    String getDescription();

    /**
     * 应用与zookeeper的通信端口(注册端口),也是dubbo端口
     * @return
     */
    int getPort();

    int getHttpPort();

    /** 产品中文名称 */
    String getProduct();

    /** 产品编码 */
    String getProductCode();


}
