// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 优化历史记录存储类
 * 
 * <p>用于存储和管理执行轨迹历史记录，支持按用例ID和LLM调用ID进行查询。</p>
 * <p>使用线程安全的数据结构实现，支持并发访问。</p>
 * 
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.tune.optimizer.base.OptimizeHistory}</p>
 * 
 * @author OpenJiuwen Team
 * @since 1.0.0
 */
public class OptimizeHistory {
    
    /**
     * 轨迹存储，键为用例ID，值为该用例的追踪节点列表
     */
    private final ConcurrentHashMap<String, List<TraceNode>> trajectory;
    
    /**
     * 构造函数，初始化空的轨迹存储
     */
    public OptimizeHistory() {
        this.trajectory = new ConcurrentHashMap<>();
    }
    
    /**
     * 添加历史记录
     * 
     * <p>将追踪节点添加到指定用例ID的历史记录中。</p>
     * <p>线程安全：使用ConcurrentHashMap和同步列表保证并发安全。</p>
     * 
     * @param caseId 用例ID
     * @param node 追踪节点
     */
    public void addHistory(String caseId, TraceNode node) {
        trajectory.computeIfAbsent(caseId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(node);
    }
    
    /**
     * 获取指定用例的历史记录
     * 
     * @param caseId 用例ID
     * @return 追踪节点列表，如果不存在则返回Optional.empty()
     */
    public Optional<List<TraceNode>> getHistory(String caseId) {
        List<TraceNode> history = trajectory.get(caseId);
        return Optional.ofNullable(history);
    }
    
    /**
     * 获取指定用例和LLM调用的历史记录
     * 
     * <p>根据用例ID和LLM调用ID过滤历史记录。</p>
     * 
     * @param caseId 用例ID
     * @param llmCallId LLM调用ID
     * @return 过滤后的追踪节点列表，如果不存在则返回Optional.empty()
     */
    public Optional<List<TraceNode>> getLlmCallHistory(String caseId, String llmCallId) {
        return getHistory(caseId)
                .map(history -> history.stream()
                        .filter(node -> llmCallId.equals(node.getLlmCallId()))
                        .collect(Collectors.toList()))
                .filter(list -> !list.isEmpty());
    }
    
    /**
     * 清空所有历史记录
     */
    public void clearHistory() {
        trajectory.clear();
    }
    
    /**
     * 获取所有用例ID
     * 
     * @return 用例ID集合
     */
    public java.util.Set<String> getCaseIds() {
        return trajectory.keySet();
    }
    
    /**
     * 检查是否包含指定用例的历史记录
     * 
     * @param caseId 用例ID
     * @return 如果存在则返回true
     */
    public boolean containsCase(String caseId) {
        return trajectory.containsKey(caseId);
    }
    
    /**
     * 获取历史记录总数
     * 
     * @return 所有用例的追踪节点总数
     */
    public int getTotalCount() {
        return trajectory.values().stream()
                .mapToInt(List::size)
                .sum();
    }
}