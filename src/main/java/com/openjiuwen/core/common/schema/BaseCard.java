package com.openjiuwen.core.common.schema;

import java.util.UUID;

/**
 * 数字名片基类
 * 
 * <p>为工具、Agent、组件等提供统一的标识和描述信息。
 * 
 * <p><strong>核心字段</strong>：
 * <ul>
 *   <li>{@code id} - 唯一标识符，自动生成UUID（32位hex）</li>
 *   <li>{@code name} - 名称，也是在某个namespace中的唯一标识符</li>
 *   <li>{@code description} - 功能、适用场景等描述信息</li>
 * </ul>
 * 
 * <p><strong>使用示例</strong>：
 * <pre>{@code
 * public class MyTool extends BaseCard {
 *     public MyTool(String name, String description) {
 *         super(name, description);
 *     }
 *     
 *     @Override
 *     public Object toolInfo() {
 *         return Map.of(
 *             "id", getId(),
 *             "name", getName(),
 *             "type", "calculator"
 *         );
 *     }
 * }
 * }</pre>
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public abstract class BaseCard {
    
    /**
     * 唯一标识符（32位hex格式UUID）
     */
    private String id;
    
    /**
     * 名称，也是在某个namespace中的唯一标识符
     */
    private String name;
    
    /**
     * 功能、适用场景等描述信息
     */
    private String description;
    
    /**
     * 输入参数（JSON Schema或自定义对象）
     */
    protected Object inputParams;
    
    /**
     * 默认构造器，自动生成UUID作为id
     */
    public BaseCard() {
        this.id = generateId();
        this.name = "";
        this.description = "";
        this.inputParams = null;
    }
    
    /**
     * 构造器（指定name和description）
     *
     * @param name 名称
     * @param description 描述
     */
    public BaseCard(String name, String description) {
        this.id = generateId();
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.inputParams = null;
    }
    
    /**
     * 完整构造器（指定所有字段）
     * 
     * @param id 唯一标识符（如果为null，则自动生成）
     * @param name 名称
     * @param description 描述
     * @param inputParams 输入参数（可以为null）
     */
    public BaseCard(String id, String name, String description, Object inputParams) {
        this.id = (id != null && !id.isEmpty()) ? id : generateId();
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.inputParams = inputParams;
    }
    
    /**
     * 生成32位hex格式的UUID
     * 
     * @return 32位hex UUID字符串
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 获取工具信息（抽象方法，由子类实现）
     * 
     * <p>子类应返回包含工具详细信息的对象，
     * 通常是Map、自定义对象或JSON字符串。
     * 
     * @return 工具信息对象
     */
    public abstract Object toolInfo();
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    /**
     * 字符串表示形式
     * 
     * @return 格式化的字符串 "id={id},name={name}"
     */
    @Override
    public String toString() {
        return String.format("id=%s,name=%s", id, name);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        BaseCard baseCard = (BaseCard) o;
        return id != null ? id.equals(baseCard.id) : baseCard.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
    
    /**
     * 获取输入参数
     *
     * @return 输入参数对象
     */
    public Object getInputParams() {
        return inputParams;
    }
}

