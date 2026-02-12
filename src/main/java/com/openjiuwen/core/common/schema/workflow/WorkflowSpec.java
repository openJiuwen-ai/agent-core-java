package com.openjiuwen.core.common.schema.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完整的工作流结构规范
 * 
 * <p>定义工作流的图结构、连接关系和组件配置。
 * 
 * <p><strong>字段说明</strong>：
 * <ul>
 *   <li>{@code edges} - 常规数据流边（源节点 → [目标节点列表]）</li>
 *   <li>{@code streamEdges} - 流式数据流边（源节点 → [目标节点列表]）</li>
 *   <li>{@code compConfigs} - 每个组件的配置（节点名称 → NodeSpec）</li>
 * </ul>
 * 
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * WorkflowSpec spec = new WorkflowSpec();
 * 
 * // 添加边：node1 -> node2, node3
 * spec.addEdge("node1", List.of("node2", "node3"));
 * 
 * // 添加组件配置
 * NodeSpec nodeSpec = new NodeSpec();
 * spec.addComponentConfig("node1", nodeSpec);
 * }</pre>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class WorkflowSpec {
    
    /**
     * 常规数据流边（source -> [targets]）
     */
    private Map<String, List<String>> edges;
    
    /**
     * 流式数据流边（source -> [targets]）
     */
    private Map<String, List<String>> streamEdges;
    
    /**
     * 工作流中每个组件的配置
     */
    private Map<String, NodeSpec> compConfigs;
    
    /**
     * 默认构造器
     */
    public WorkflowSpec() {
        this.edges = new HashMap<>();
        this.streamEdges = new HashMap<>();
        this.compConfigs = new HashMap<>();
    }
    
    /**
     * 完整构造器
     * 
     * @param edges 常规数据流边
     * @param streamEdges 流式数据流边
     * @param compConfigs 组件配置
     */
    public WorkflowSpec(Map<String, List<String>> edges, 
                       Map<String, List<String>> streamEdges,
                       Map<String, NodeSpec> compConfigs) {
        this.edges = edges != null ? new HashMap<>(edges) : new HashMap<>();
        this.streamEdges = streamEdges != null ? new HashMap<>(streamEdges) : new HashMap<>();
        this.compConfigs = compConfigs != null ? new HashMap<>(compConfigs) : new HashMap<>();
    }
    
    // Getters and Setters
    
    public Map<String, List<String>> getEdges() {
        return new HashMap<>(edges);
    }
    
    public void setEdges(Map<String, List<String>> edges) {
        this.edges = edges != null ? new HashMap<>(edges) : new HashMap<>();
    }
    
    public Map<String, List<String>> getStreamEdges() {
        return new HashMap<>(streamEdges);
    }
    
    public void setStreamEdges(Map<String, List<String>> streamEdges) {
        this.streamEdges = streamEdges != null ? new HashMap<>(streamEdges) : new HashMap<>();
    }
    
    public Map<String, NodeSpec> getCompConfigs() {
        return new HashMap<>(compConfigs);
    }
    
    public void setCompConfigs(Map<String, NodeSpec> compConfigs) {
        this.compConfigs = compConfigs != null ? new HashMap<>(compConfigs) : new HashMap<>();
    }
    
    // 便捷方法
    
    /**
     * 添加常规边
     * 
     * @param source 源节点
     * @param targets 目标节点列表
     */
    public void addEdge(String source, List<String> targets) {
        edges.put(source, new ArrayList<>(targets));
    }
    
    /**
     * 添加流式边
     * 
     * @param source 源节点
     * @param targets 目标节点列表
     */
    public void addStreamEdge(String source, List<String> targets) {
        streamEdges.put(source, new ArrayList<>(targets));
    }
    
    /**
     * 添加组件配置
     * 
     * @param componentName 组件名称
     * @param nodeSpec 节点规范
     */
    public void addComponentConfig(String componentName, NodeSpec nodeSpec) {
        compConfigs.put(componentName, nodeSpec);
    }
    
    /**
     * 获取指定组件的配置
     * 
     * @param componentName 组件名称
     * @return 节点规范，如果不存在则返回null
     */
    public NodeSpec getComponentConfig(String componentName) {
        return compConfigs.get(componentName);
    }
    
    @Override
    public String toString() {
        return "WorkflowSpec{" +
                "edges=" + edges +
                ", streamEdges=" + streamEdges +
                ", compConfigs=" + compConfigs +
                '}';
    }
}

