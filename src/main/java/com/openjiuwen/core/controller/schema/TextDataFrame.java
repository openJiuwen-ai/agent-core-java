// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import java.util.Objects;

/**
 * Text DataFrame.
 *
 * <p>Used for transmitting text-type data.
 * Suitable for transmitting plain text content, such as user input, task descriptions, etc.
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class TextDataFrame extends BaseDataFrame {

    private final String text;

    /**
     * Constructor.
     *
     * @param text the text content (must not be null)
     */
    public TextDataFrame(String text) {
        super("text");
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    /**
     * Gets the text content.
     *
     * @return the text string
     */
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TextDataFrame that = (TextDataFrame) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }

    @Override
    public String toString() {
        return "TextDataFrame{type='text', text='" + text + "'}";
    }
}

