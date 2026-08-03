package com.openjiuwen.core.common.schema;

import java.util.UUID;

/**
 * Mirrors Python's {@code BaseCard} in
 * {@code openjiuwen/core/common/schema/card.py}.
 */
public class BaseCard {
    private String id;
    private String name;
    private String description;

    public BaseCard() {
        this.id = UUID.randomUUID().toString().replace("-", "");
        this.name = "";
        this.description = "";
    }

    public BaseCard(String id, String name, String description) {
        this.id = id;
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
    }

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
        this.name = name != null ? name : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public Object toolInfo() {
        return null;
    }

    public BaseCard copy() {
        return new BaseCard(id, name, description);
    }

    @Override
    public String toString() {
        return "id=" + id + ",name=" + name;
    }

    public String toStr() {
        return "id=" + id + ",name=" + name;
    }
}
