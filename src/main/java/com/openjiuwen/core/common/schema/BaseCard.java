// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.schema;

import java.util.UUID;

/**
 * 数字名片基类
 *
 * <p>用于标识和描述数字世界中的各种"名片"（如Agent、工具、工作流等）。</p>
 *
 * <p>Attributes:
 *     id: 唯一标识符
 *     name: 名称，也是在某个 namespace 中的唯一标识符
 *     description: 功能、适用场景等描述信息
 * </p>
 */
public class BaseCard {

    private final String id;
    private String name;
    private String description;

    /**
     * 默认构造函数，自动生成UUID
     */
    public BaseCard() {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.name = "";
        this.description = "";
    }

    /**
     * 使用指定ID构造（用于测试或重建场景）
     *
     * @param id 指定的ID
     */
    public BaseCard(String id) {
        this.id = id;
        this.name = "";
        this.description = "";
    }

    /**
     * 全参数构造函数
     *
     * @param id 唯一标识符
     * @param name 名称
     * @param description 描述
     */
    public BaseCard(String id, String name, String description) {
        this.id = id;
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
    }

    /**
     * 获取唯一标识符
     *
     * @return ID字符串
     */
    public String getId() {
        return id;
    }

    /**
     * 获取名称
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    /**
     * 获取描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述
     *
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /**
     * 获取工具信息（子类实现）
     *
     * @return 工具信息
     */
    public Object toolInfo() {
        throw new UnsupportedOperationException("toolInfo() must be implemented by subclass");
    }

    /**
     * 转换为字符串表示
     *
     * @return 字符串表示
     */
    public String str() {
        return String.format("id=%s,name=%s", id, name);
    }

    @Override
    public String toString() {
        return str();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseCard baseCard = (BaseCard) o;
        return id.equals(baseCard.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}