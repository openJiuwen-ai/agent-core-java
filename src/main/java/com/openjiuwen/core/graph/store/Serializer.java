/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

/**
 * 序列化器接口。
 * 
 * <p>定义了带类型标识的序列化和反序列化操作。
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/graph/store/serde.py - Serializer
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public interface Serializer {
    
    /**
     * 将对象序列化为带类型标识的数据。
     *
     * @param obj 要序列化的对象
     * @return 包含类型标识和字节数组的 TypedData 对象
     */
    TypedData dumpsTyped(Object obj);
    
    /**
     * 从带类型标识的数据反序列化对象。
     *
     * @param data 带类型标识的数据
     * @return 反序列化后的对象，如果数据为 null 或类型不匹配则返回 null
     */
    Object loadsTyped(TypedData data);
    
    /**
     * 表示带类型标识的序列化数据。
     */
    record TypedData(String type, byte[] data) {
        
        /**
         * 检查数据是否有效。
         *
         * @return 如果类型和数据都不为 null 则返回 true
         */
        public boolean isValid() {
            return type != null && data != null;
        }
    }
}

