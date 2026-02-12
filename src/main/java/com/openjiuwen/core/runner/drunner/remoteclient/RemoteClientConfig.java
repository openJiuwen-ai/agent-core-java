// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import java.util.HashMap;
import java.util.Map;

/**
 * 远程客户端配置
 * 
 * 对应Python: drunner/remote_client/remote_client_config.py - RemoteClientConfig
 */
public class RemoteClientConfig {

    private String id;
    private String version;
    private String name;
    private String description;
    private String protocol = ProtocolEnum.MQ.getValue();
    private String type;
    private String topic;
    private String url;
    private Map<String, Object> kwargs = new HashMap<>();

    public RemoteClientConfig() {
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, Object> getKwargs() {
        return kwargs;
    }

    public void setKwargs(Map<String, Object> kwargs) {
        this.kwargs = kwargs != null ? kwargs : new HashMap<>();
    }
}

