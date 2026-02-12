package com.openjiuwen.core.common.schema.workflow;

/**
 * 组件输入/输出配置
 * 
 * <p>定义组件数据处理的schema和转换器。
 * 
 * <p><strong>字段说明</strong>：
 * <ul>
 *   <li>{@code inputsSchema} - 输入schema（可以是Map或Transformer）</li>
 *   <li>{@code outputsSchema} - 输出schema（可以是Map或Transformer）</li>
 * </ul>
 * 
 * <p><strong>注意</strong>：
 * 由于Python源码中使用了Union类型（{@code Dict | Transformer}），
 * Java中使用{@code Object}来存储，实际使用时需要类型检查。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class CompIOConfig {
    
    /**
     * 输入schema（可以是Map或Transformer）
     */
    private Object inputsSchema;
    
    /**
     * 输出schema（可以是Map或Transformer）
     */
    private Object outputsSchema;
    
    /**
     * 默认构造器
     */
    public CompIOConfig() {
    }
    
    /**
     * 完整构造器
     * 
     * @param inputsSchema 输入schema
     * @param outputsSchema 输出schema
     */
    public CompIOConfig(Object inputsSchema, Object outputsSchema) {
        this.inputsSchema = inputsSchema;
        this.outputsSchema = outputsSchema;
    }
    
    // Getters and Setters
    
    public Object getInputsSchema() {
        return inputsSchema;
    }
    
    public void setInputsSchema(Object inputsSchema) {
        this.inputsSchema = inputsSchema;
    }
    
    public Object getOutputsSchema() {
        return outputsSchema;
    }
    
    public void setOutputsSchema(Object outputsSchema) {
        this.outputsSchema = outputsSchema;
    }
    
    @Override
    public String toString() {
        return "CompIOConfig{" +
                "inputsSchema=" + inputsSchema +
                ", outputsSchema=" + outputsSchema +
                '}';
    }
}

