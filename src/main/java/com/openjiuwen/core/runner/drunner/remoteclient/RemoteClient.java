// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 远程客户端抽象接口
 * 
 * <p>定义远程客户端的生命周期和通信方法。
 * 
 * 对应Python: drunner/remote_client/remote_client.py - RemoteClient(ABC)
 */
public interface RemoteClient {

    /**
     * 启动客户端，初始化与Runner的连接。
     */
    void start();

    /**
     * 停止客户端。
     */
    void stop();

    /**
     * 非流式请求：发送消息并等待单个响应。
     *
     * @param inputs  输入参数
     * @param timeout 超时时间（秒），null表示使用默认配置
     * @return 响应结果
     * @throws TimeoutException 超时异常
     */
    Object invoke(Map<String, Object> inputs, Double timeout) throws TimeoutException;

    /**
     * 流式请求：发送消息并收集所有流式响应。
     *
     * @param inputs  输入参数
     * @param timeout 超时时间（秒），null表示使用默认配置
     * @return 流式响应列表
     * @throws TimeoutException 超时异常
     */
    List<Object> stream(Map<String, Object> inputs, Double timeout) throws TimeoutException;
}

