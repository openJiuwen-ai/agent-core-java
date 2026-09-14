/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.form_handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Java-side surrogate for the mutable {@code aiohttp.FormData} object used by this module.
 *
 * <p>Mirrors Python's form-data handling in
 * {@code openjiuwen/core/foundation/tool/form_handler/form_handler_manager.py}.</p>
 */
public final class ToolFormData {

    private final List<FieldEntry> fields = new ArrayList<>();

    public ToolFormData addField(String name, String value) {
        return addField(name, value, null);
    }

    public ToolFormData addField(String name, String value, String contentType) {
        fields.add(new FieldEntry(
                Objects.requireNonNull(name, "name"),
                value,
                contentType
        ));
        return this;
    }

    public int size() {
        return fields.size();
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (FieldEntry field : fields) {
            names.add(field.name);
        }
        return List.copyOf(names);
    }

    public List<String> values(String name) {
        List<String> values = new ArrayList<>();
        for (FieldEntry field : fields) {
            if (field.name.equals(name)) {
                values.add(field.value);
            }
        }
        return List.copyOf(values);
    }

    public List<String> contentTypes(String name) {
        List<String> contentTypes = new ArrayList<>();
        for (FieldEntry field : fields) {
            if (field.name.equals(name) && field.contentType != null) {
                contentTypes.add(field.contentType);
            }
        }
        return List.copyOf(contentTypes);
    }

    private record FieldEntry(String name, String value, String contentType) {
    }
}
