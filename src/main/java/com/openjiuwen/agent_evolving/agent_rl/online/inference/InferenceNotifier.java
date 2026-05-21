/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.inference;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

/**
 * InferenceNotifier — 通知 vLLM 运行时热加载用户 LoRA.
 * <p>
 * vLLM 原生支持 /v1/load_lora_adapter 接口，无需重启服务。
 * 加载后，对应 lora_name 的请求将自动应用新权重。
 * <p>
 * Mirrors Python's {@code InferenceNotifier} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.inference.notifier}.
 */
public class InferenceNotifier {

    private String vllmBaseUrl;
    private double timeout;
    private HttpClient httpClient;
    private boolean ownedClient;

    public InferenceNotifier(String vllmBaseUrl, double timeout) {
        this.vllmBaseUrl = vllmBaseUrl.replaceAll("/$", "");
        this.timeout = timeout;
        this.ownedClient = true;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds((long) timeout))
                .build();
    }

    public InferenceNotifier(String vllmBaseUrl, double timeout, HttpClient httpClient) {
        this.vllmBaseUrl = vllmBaseUrl.replaceAll("/$", "");
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.ownedClient = httpClient == null;
        if (this.httpClient == null) {
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds((long) timeout))
                    .build();
        }
    }

    /**
     * 通知 vLLM 热加载指定用户的 LoRA。
     * 
     * @param userId 用户 ID，作为 vLLM 中的 lora_name
     * @param loraPath LoRA 权重目录的绝对路径
     */
    public void notifyUpdate(String userId, String loraPath) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lora_name", userId);
        payload.put("lora_path", loraPath);
        payload.put("load_inplace", true);

        String jsonBody = toJson(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vllmBaseUrl + "/v1/load_lora_adapter"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds((long) timeout))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException(
                "vLLM load_lora_adapter failed: status=" + response.statusCode() + 
                ", body=" + response.body().substring(0, Math.min(400, response.body().length())));
        }
    }

    /**
     * 卸载用户 LoRA（可选，用于清理不活跃用户）。
     * 
     * @param userId 用户 ID
     */
    public void unload(String userId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lora_name", userId);

        String jsonBody = toJson(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vllmBaseUrl + "/v1/unload_lora_adapter"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds((long) timeout))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("vLLM unload_lora_adapter failed: status=" + response.statusCode());
        }
    }

    /**
     * Close HTTP client if owned.
     */
    public void close() {
        // HttpClient doesn't need explicit close in Java
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    public String getVllmBaseUrl() { return vllmBaseUrl; }
    public double getTimeout() { return timeout; }
}