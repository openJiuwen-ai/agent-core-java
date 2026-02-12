package com.openjiuwen.core.common.schema.workflow;

/**
 * 组件能力接口（占位接口）
 * 
 * <p><strong>⚠️ 注意：这是一个占位接口</strong></p>
 * 
 * <p>此接口是为了解决循环依赖而创建的临时接口。
 * 实际的{@code ComponentAbility}将在{@code workflow.components.base}模块中实现。
 * 
 * <p><strong>循环依赖说明</strong>：
 * <pre>
 * common.schema.workflow_spec 依赖→ workflow.components.base.ComponentAbility
 * 但是：
 *   workflow模块 依赖→ common.schema
 * </pre>
 * 
 * <p><strong>解决方案</strong>：
 * 在common.schema中定义接口，workflow模块中实现该接口，打破循环依赖。
 * 
 * <p><strong>TODO</strong>：
 * 当转换workflow模块时，需要确保实际的ComponentAbility实现此接口，
 * 或将此接口的定义与实际实现对齐。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public interface ComponentAbility {
    // 占位接口，具体方法将在workflow模块转换时定义
}

