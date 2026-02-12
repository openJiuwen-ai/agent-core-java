package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 工具抽象基类
 * 
 * <p>定义了LLM模块使用的工具数据类型和内容。
 * 
 * @param <I> 输入类型
 * @param <O> 输出类型
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public abstract class Tool<I, O> {
    protected final ToolCard card;
    
    /**
     * 构造工具实例
     * 
     * @param card 工具配置卡片
     */
    public Tool(ToolCard card) {
        if (card == null) {
            throw ErrorBuilder.build(StatusCode.TOOL_CARD_NOT_SUPPORTED);
        }
        if (card.getId() == null || card.getId().isEmpty()) {
            Map<String, Object> params = Map.of("card", card.toString());
            throw ErrorBuilder.build(StatusCode.TOOL_CARD_ID_NOT_SUPPORTED, null, null, null, params);
        }
        this.card = card;
    }
    
    /**
     * 获取工具卡片
     * 
     * @return 工具卡片
     */
    public ToolCard getCard() {
        return card;
    }
    
    /**
     * 执行工具并返回最终结果
     * 
     * <p>该方法执行完整的工具操作，处理所有输入并在操作完全完成时返回最终输出。
     * 
     * @param inputs 符合工具输入schema的结构化输入数据
     * @param kwargs 额外的执行参数，如timeout、retry策略或工具特定选项
     * @return 工具执行的完整结果
     */
    public abstract CompletableFuture<O> invoke(I inputs, Map<String, Object> kwargs);
    
    /**
     * 执行工具并流式返回增量结果
     * 
     * <p>该方法支持长时间运行的操作，通过产生部分结果来实现实时处理和进度跟踪。
     * 
     * @param inputs 符合工具输入schema的结构化输入数据
     * @param kwargs 流式行为的额外执行参数
     * @return 工具执行期间的增量结果流
     */
    public abstract Stream<O> stream(I inputs, Map<String, Object> kwargs);
}

