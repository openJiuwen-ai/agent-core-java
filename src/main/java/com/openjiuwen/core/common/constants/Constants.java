// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants;

/**
 * 核心常量定义类
 *
 * <p>包含 IR 相关常量、Workflow 相关常量和安全限制常量。
 * 所有常量均为 public static final，不可修改。</p>
 */
public final class Constants {

    // ==================== IR 相关常量 ====================

    /**
     * IR userFields 键名
     */
    public static final String USER_FIELDS = "userFields";

    /**
     * IR query 键名
     */
    public static final String QUERY = "query";

    /**
     * IR systemFields 键名
     */
    public static final String SYSTEM_FIELDS = "systemFields";

    // ==================== Workflow 相关常量 ====================

    /**
     * 交互标识符
     * <p>用于标记动态交互，Python 中使用 sys.intern() 优化内存，Java 字符串字面量默认被 intern</p>
     */
    public static final String INTERACTION = "__interaction__";

    /**
     * 交互输入标识符
     * <p>用于标记节点引发的动态交互输入</p>
     */
    public static final String INTERACTIVE_INPUT = "__interactive_input__";

    /**
     * 输入键名
     */
    public static final String INPUTS_KEY = "inputs";

    /**
     * 配置键名
     */
    public static final String CONFIG_KEY = "config";

    /**
     * 结束帧标识
     */
    public static final String END_FRAME = "all streaming outputs finish";

    /**
     * 结束节点流标识
     */
    public static final String END_NODE_STREAM = "end node stream";

    /**
     * 循环 ID 键名
     */
    public static final String LOOP_ID = "__sys_loop_id";

    /**
     * 索引键名
     */
    public static final String INDEX = "index";

    /**
     * 完成索引键名
     */
    public static final String FINISH_INDEX = "finish_index";

    // ==================== 安全限制常量 ====================

    /**
     * 最大允许的集合大小
     */
    public static final int MAX_COLLECTION_SIZE = 100000;

    /**
     * 最大允许的表达式长度
     */
    public static final int MAX_EXPRESSION_LENGTH = 5000;

    /**
     * 最大允许的 AST 深度
     */
    public static final int MAX_AST_DEPTH = 50;

    /**
     * 最大允许的嵌套循环深度
     * <p>值为 1 表示不允许嵌套</p>
     */
    public static final int NESTED_LOOP_DEPTH = 1;

    /**
     * 私有构造函数，防止实例化
     */
    private Constants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}