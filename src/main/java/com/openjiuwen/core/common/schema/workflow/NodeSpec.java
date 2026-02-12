package com.openjiuwen.core.common.schema.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流节点/组件规范
 * 
 * <p>包含常规和流式I/O的配置，以及组件能力。
 * 
 * <p><strong>字段说明</strong>：
 * <ul>
 *   <li>{@code ioConfig} - 常规（非流式）I/O配置</li>
 *   <li>{@code streamIoConfigs} - 流式I/O配置</li>
 *   <li>{@code abilities} - 组件支持的能力列表</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class NodeSpec {
    
    /**
     * 常规（非流式）I/O配置
     */
    private CompIOConfig ioConfig;
    
    /**
     * 流式I/O配置
     */
    private CompIOConfig streamIoConfigs;
    
    /**
     * 组件支持的能力列表
     */
    private List<ComponentAbility> abilities;
    
    /**
     * 默认构造器
     */
    public NodeSpec() {
        this.abilities = new ArrayList<>();
    }
    
    /**
     * 完整构造器
     * 
     * @param ioConfig 常规I/O配置
     * @param streamIoConfigs 流式I/O配置
     * @param abilities 组件能力列表
     */
    public NodeSpec(CompIOConfig ioConfig, CompIOConfig streamIoConfigs, 
                    List<ComponentAbility> abilities) {
        this.ioConfig = ioConfig;
        this.streamIoConfigs = streamIoConfigs;
        this.abilities = abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
    }
    
    // Getters and Setters
    
    public CompIOConfig getIoConfig() {
        return ioConfig;
    }
    
    public void setIoConfig(CompIOConfig ioConfig) {
        this.ioConfig = ioConfig;
    }
    
    public CompIOConfig getStreamIoConfigs() {
        return streamIoConfigs;
    }
    
    public void setStreamIoConfigs(CompIOConfig streamIoConfigs) {
        this.streamIoConfigs = streamIoConfigs;
    }
    
    public List<ComponentAbility> getAbilities() {
        return abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
    }
    
    public void setAbilities(List<ComponentAbility> abilities) {
        this.abilities = abilities != null ? new ArrayList<>(abilities) : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return "NodeSpec{" +
                "ioConfig=" + ioConfig +
                ", streamIoConfigs=" + streamIoConfigs +
                ", abilities=" + abilities +
                '}';
    }
}

