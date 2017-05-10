package com.soouya.cloudstack.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;


/**
 * 配置加载器
 */
public class ConfigLoader {
    public static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static ResourceBundle messages = null;

    public static ConfigLoader getInstance() {
        return ConfigLoaderHolder.instance;
    }

    public String getProperty(String key) {
        return messages.getString(key);
    }

    static {
        try {
            messages = ResourceBundle.getBundle("application");
        } catch (Exception e) {
            logger.error("load properties {} error", "cloud", e);
            System.exit(-1);
        }
    }

    private static class ConfigLoaderHolder {
        private static final ConfigLoader instance = new ConfigLoader();
    }
}