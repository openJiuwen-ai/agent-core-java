/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的图状态存储。
 * 
 * <p>使用 ConcurrentHashMap 存储图状态，数据在内存中保存，
 * 不持久化到磁盘。适用于测试和开发环境。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/inmemory.py - InMemoryStore
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class InMemoryStore implements Store {
    
    /**
     * 存储最新的图状态：{ sessionId: { ns: state } }
     */
    private final Map<String, Map<String, GraphState>> storeCk = new ConcurrentHashMap<>();
    
    @Override
    public CompletableFuture<Optional<GraphState>> get(String sessionId, String ns) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, GraphState> sessionStore = storeCk.get(sessionId);
            if (sessionStore == null) {
                return Optional.empty();
            }
            GraphState state = sessionStore.get(ns);
            if (state == null) {
                return Optional.empty();
            }
            // 返回深拷贝以防止外部修改
            return Optional.of(deepCopy(state));
        });
    }
    
    @Override
    public CompletableFuture<Void> save(String sessionId, String ns, GraphState state) {
        return CompletableFuture.runAsync(() -> {
            Map<String, GraphState> sessionStore = storeCk.computeIfAbsent(
                sessionId, k -> new ConcurrentHashMap<>()
            );
            // 存储深拷贝以防止外部修改影响存储的数据
            sessionStore.put(ns, deepCopy(state));
        });
    }
    
    @Override
    public CompletableFuture<Void> delete(String sessionId, String ns) {
        return CompletableFuture.runAsync(() -> {
            if (!storeCk.containsKey(sessionId)) {
                return; // 会话ID不存在，无需删除
            }
            
            if (ns == null) {
                // 删除该会话的所有命名空间
                storeCk.remove(sessionId);
            } else {
                // 删除以指定命名空间为前缀的所有状态
                Map<String, GraphState> sessionStore = storeCk.get(sessionId);
                deleteNsByPrefix(sessionStore, ns);
                
                // 如果会话存储变空，清理它
                if (sessionStore.isEmpty()) {
                    storeCk.remove(sessionId);
                }
            }
        });
    }
    
    /**
     * 删除以指定前缀开头的所有命名空间。
     *
     * @param subMap 命名空间到状态的映射
     * @param prefix 命名空间前缀
     */
    private static void deleteNsByPrefix(Map<String, GraphState> subMap, String prefix) {
        if (subMap == null) {
            return;
        }
        
        List<String> nsToDelete = new ArrayList<>();
        for (String key : subMap.keySet()) {
            if (key.startsWith(prefix)) {
                nsToDelete.add(key);
            }
        }
        
        if (nsToDelete.isEmpty()) {
            return;
        }
        
        for (String nsKey : nsToDelete) {
            subMap.remove(nsKey);
        }
    }
    
    /**
     * 深拷贝 GraphState 对象。
     *
     * @param state 要拷贝的状态
     * @return 深拷贝后的状态
     */
    private static GraphState deepCopy(GraphState state) {
        if (state == null) {
            return null;
        }
        
        // 深拷贝 channelValues
        Map<String, Object> channelValuesCopy = deepCopyMap(state.getChannelValues());
        
        // 深拷贝 pendingBuffer
        List<Message> pendingBufferCopy = new ArrayList<>(state.getPendingBuffer());
        
        // 深拷贝 pendingNode
        Map<String, PendingNode> pendingNodeCopy = new HashMap<>();
        for (Map.Entry<String, PendingNode> entry : state.getPendingNode().entrySet()) {
            PendingNode node = entry.getValue();
            pendingNodeCopy.put(entry.getKey(), 
                new PendingNode(node.getNodeName(), node.getStatus(), node.getException()));
        }
        
        // 深拷贝 nodeVersion
        Map<String, Integer> nodeVersionCopy = new HashMap<>(state.getNodeVersion());
        
        return new GraphState(
            state.getNs(),
            state.getStep(),
            channelValuesCopy,
            pendingBufferCopy,
            pendingNodeCopy,
            nodeVersionCopy
        );
    }
    
    /**
     * 深拷贝 Map 对象。
     *
     * @param original 原始 Map
     * @return 深拷贝后的 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> original) {
        if (original == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                // 对于不可变对象或基本类型包装，直接引用
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
    
    /**
     * 获取存储的会话数量（用于测试）。
     *
     * @return 会话数量
     */
    public int getSessionCount() {
        return storeCk.size();
    }
    
    /**
     * 检查是否包含指定会话（用于测试）。
     *
     * @param sessionId 会话标识符
     * @return 如果包含则返回 true
     */
    public boolean containsSession(String sessionId) {
        return storeCk.containsKey(sessionId);
    }
    
    /**
     * 清空所有存储的状态（用于测试）。
     */
    public void clear() {
        storeCk.clear();
    }
}

