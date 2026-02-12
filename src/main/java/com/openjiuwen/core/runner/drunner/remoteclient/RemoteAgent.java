// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.RunnerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * 远程Agent
 * 
 * <p>封装 {@link RemoteClient}，对外提供简单的 invoke/stream 接口，
 * 并将底层的 CancellationException / TimeoutException 转换为 {@link JiuWenBaseException}。
 * 
 * 对应Python: drunner/remote_client/remote_agent.py - RemoteAgent
 */
public class RemoteAgent {

    private static final Logger logger = LoggerFactory.getLogger(RemoteAgent.class);

    private final String agentId;
    private String version;
    private String description;
    private String topic;
    private ProtocolEnum protocol;
    private RemoteClientConfig config;
    private RemoteClient client;

    /**
     * 简单构造函数（用于向后兼容和测试）
     *
     * @param agentId Agent ID
     */
    public RemoteAgent(String agentId) {
        this.agentId = agentId;
    }

    /**
     * 完整构造函数，匹配Python RemoteAgent.__init__
     *
     * @param agentId     Agent ID
     * @param version     版本号（默认空串）
     * @param description 描述
     * @param topic       请求主题（null时从配置模板生成）
     * @param protocol    协议类型（默认MQ）
     * @param extraConfig 额外配置（传入RemoteClientConfig.kwargs）
     */
    public RemoteAgent(String agentId, String version, String description,
                       String topic, ProtocolEnum protocol, Map<String, Object> extraConfig) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.description = description;
        this.protocol = protocol != null ? protocol : ProtocolEnum.MQ;

        // 未提供topic时使用配置模板
        if (topic != null) {
            this.topic = topic;
        } else {
            this.topic = RunnerConfig.getRunnerConfig().agentTopicTemplate()
                .replace("{agent_id}", agentId)
                .replace("{version}", this.version);
        }

        // 构建RemoteClientConfig
        this.config = new RemoteClientConfig();
        this.config.setId(agentId);
        this.config.setProtocol(this.protocol.getValue());
        this.config.setTopic(this.topic);
        if (extraConfig != null) {
            this.config.setKwargs(extraConfig);
        }

        this.client = createClient();
    }

    /**
     * 便捷构造函数（仅agentId）
     * 使用默认协议和从配置模板生成的topic
     *
     * @param agentId Agent ID
     * @param version 版本号
     */
    public RemoteAgent(String agentId, String version) {
        this(agentId, version, null, null, ProtocolEnum.MQ, null);
    }

    /**
     * 创建远程客户端实例
     *
     * @return RemoteClient实例
     */
    protected RemoteClient createClient() {
        if (protocol == ProtocolEnum.MQ) {
            return new MqRemoteClient(config);
        }
        return null;
    }

    /**
     * 非流式调用远程Agent
     * 
     * <p>将 CancellationException 转换为 REMOTE_AGENT_REQUEST_CANCELLED，
     * 将 TimeoutException 转换为 REMOTE_AGENT_REQUEST_TIMEOUT。
     *
     * @param inputs  输入参数
     * @param timeout 超时时间（秒），null表示使用默认配置
     * @return 响应结果
     */
    public Object invoke(Map<String, Object> inputs, Double timeout) {
        try {
            client.start();
            return client.invoke(inputs, timeout);
        } catch (CancellationException e) {
            throw new JiuWenBaseException(
                StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getCode(),
                StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getMessage());
        } catch (TimeoutException e) {
            throw new JiuWenBaseException(
                StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getCode(),
                StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getMessage());
        }
    }

    /**
     * 流式调用远程Agent
     * 
     * <p>将 CancellationException 转换为 REMOTE_AGENT_REQUEST_CANCELLED，
     * 将 TimeoutException 转换为 REMOTE_AGENT_REQUEST_TIMEOUT。
     *
     * @param inputs  输入参数
     * @param timeout 超时时间（秒），null表示使用默认配置
     * @return 流式响应列表
     */
    public List<Object> stream(Map<String, Object> inputs, Double timeout) {
        try {
            client.start();
            return client.stream(inputs, timeout);
        } catch (CancellationException e) {
            throw new JiuWenBaseException(
                StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getCode(),
                StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getMessage());
        } catch (TimeoutException e) {
            throw new JiuWenBaseException(
                StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getCode(),
                StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getMessage());
        }
    }

    /**
     * 获取Agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getTopic() {
        return topic;
    }

    public ProtocolEnum getProtocol() {
        return protocol;
    }

    public RemoteClientConfig getConfig() {
        return config;
    }

    /**
     * 获取底层客户端（用于测试）
     */
    RemoteClient getClient() {
        return client;
    }

    /**
     * 设置底层客户端（用于测试）
     */
    void setClient(RemoteClient client) {
        this.client = client;
    }
}
