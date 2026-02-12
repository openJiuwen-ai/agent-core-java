// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.prompt;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interpolatable text prompt template with configurable placeholders.
 * <p>
 * Supports both string and BaseMessage list as content, and provides {@link #toMessages()} 
 * and {@link #format(Map)} methods for placeholder replacement.
 * </p>
 * 
 * <p>Converted from Python: agent-core/openjiuwen/core/foundation/prompt/template.py</p>
 */
public class PromptTemplate {
    private String name;
    private Object content; // Union[String, List<BaseMessage>]
    private String placeholderPrefix;
    private String placeholderSuffix;

    /**
     * Constructs a PromptTemplate with default values.
     */
    public PromptTemplate() {
        this.name = "";
        this.content = "";
        this.placeholderPrefix = "{{";
        this.placeholderSuffix = "}}";
    }

    /**
     * Constructs a PromptTemplate with the specified parameters.
     *
     * @param name              the template name
     * @param content           the template content (String or List&lt;BaseMessage&gt;)
     * @param placeholderPrefix the left delimiter for placeholders (default "{{")
     * @param placeholderSuffix the right delimiter for placeholders (default "}}")
     */
    public PromptTemplate(String name, Object content, String placeholderPrefix, String placeholderSuffix) {
        this.name = name != null ? name : "";
        this.content = content != null ? content : "";
        this.placeholderPrefix = placeholderPrefix != null ? placeholderPrefix : "{{";
        this.placeholderSuffix = placeholderSuffix != null ? placeholderSuffix : "}}";
    }

    /**
     * Gets the template name.
     *
     * @return the template name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the template name.
     *
     * @param name the template name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the template content.
     *
     * @return the template content (String or List&lt;BaseMessage&gt;)
     */
    public Object getContent() {
        return content;
    }

    /**
     * Sets the template content.
     *
     * @param content the template content (String or List&lt;BaseMessage&gt;)
     */
    public void setContent(Object content) {
        this.content = content;
    }

    /**
     * Gets the placeholder prefix.
     *
     * @return the placeholder prefix
     */
    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    /**
     * Sets the placeholder prefix.
     *
     * @param placeholderPrefix the placeholder prefix
     */
    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * Gets the placeholder suffix.
     *
     * @return the placeholder suffix
     */
    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    /**
     * Sets the placeholder suffix.
     *
     * @param placeholderSuffix the placeholder suffix
     */
    public void setPlaceholderSuffix(String placeholderSuffix) {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * Converts the prompt template content (string or BaseMessage list) to a list of BaseMessage objects.
     * <p>
     * If content is a string, it is wrapped as a single UserMessage; if it is already a list,
     * the list is returned as-is (deep copied).
     * </p>
     *
     * @return the list of BaseMessage objects
     * @throws BaseError if content is not a string or list of BaseMessage
     */
    public List<BaseMessage> toMessages() {
        if (content == null || (content instanceof String && ((String) content).isEmpty())) {
            return Collections.emptyList();
        }

        if (content instanceof String) {
            return List.of(new UserMessage((String) content));
        }

        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> contentList = (List<Object>) content;
            
            // Verify all elements are BaseMessage
            for (Object msg : contentList) {
                if (!(msg instanceof BaseMessage)) {
                    throw new BaseError(
                        StatusCode.PROMPT_TEMPLATE_INVALID,
                        "prompt template type must be in str or list[BaseMessage]",
                        null, null, null
                    );
                }
            }

            // Deep copy the message list
            @SuppressWarnings("unchecked")
            List<BaseMessage> messageList = (List<BaseMessage>) content;
            return deepCopyMessageList(messageList);
        }

        throw new BaseError(
            StatusCode.PROMPT_TEMPLATE_INVALID,
            "prompt template type must be in str or list[BaseMessage]",
            null, null, null
        );
    }

    /**
     * Replaces all placeholders in the prompt template content with the provided keywords
     * and returns a new PromptTemplate instance with the interpolated content.
     * <p>
     * Placeholders are identified by the configured prefix and suffix.
     * If keywords is null or empty, the original prompt template is returned unchanged (deep copied).
     * </p>
     *
     * @param keywords the key-value pairs for placeholder replacement (can be null)
     * @return a new PromptTemplate with placeholders replaced
     */
    public PromptTemplate format(Map<String, Object> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return deepCopy();
        }

        PromptAssembler assembler = new PromptAssembler(
            deepCopyContent(content),
            placeholderPrefix,
            placeholderSuffix,
            new HashMap<>()
        );

        List<String> inputKeys = assembler.getInputKeys();
        Map<String, Object> validKeywords = keywords.entrySet().stream()
            .filter(e -> inputKeys.contains(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Object formattedContent = assembler.promptAssemble(validKeywords);

        return new PromptTemplate(
            this.name,
            formattedContent,
            this.placeholderPrefix,
            this.placeholderSuffix
        );
    }

    /**
     * Creates a deep copy of this PromptTemplate.
     *
     * @return a deep copy of this template
     */
    private PromptTemplate deepCopy() {
        return new PromptTemplate(
            this.name,
            deepCopyContent(this.content),
            this.placeholderPrefix,
            this.placeholderSuffix
        );
    }

    /**
     * Creates a deep copy of the content (String or List&lt;BaseMessage&gt;).
     *
     * @param content the content to copy
     * @return a deep copy of the content
     */
    private Object deepCopyContent(Object content) {
        if (content instanceof String) {
            return content; // Strings are immutable
        }
        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<BaseMessage> messageList = (List<BaseMessage>) content;
            return deepCopyMessageList(messageList);
        }
        return content;
    }

    /**
     * Creates a deep copy of a list of BaseMessage objects.
     * <p>
     * Note: This is a simple implementation that creates new instances.
     * For production code, consider implementing proper cloning or serialization-based deep copy.
     * </p>
     *
     * @param messages the list of messages to copy
     * @return a deep copy of the message list
     */
    private List<BaseMessage> deepCopyMessageList(List<BaseMessage> messages) {
        List<BaseMessage> copy = new ArrayList<>();
        for (BaseMessage msg : messages) {
            // Simple shallow copy for now - subclasses need proper copy constructors
            BaseMessage msgCopy = createMessageCopy(msg);
            copy.add(msgCopy);
        }
        return copy;
    }

    /**
     * Creates a copy of a BaseMessage.
     * <p>
     * This is a simplified implementation. In production, BaseMessage and its subclasses
     * should implement proper copy constructors or Cloneable interface.
     * </p>
     *
     * @param msg the message to copy
     * @return a copy of the message
     */
    private BaseMessage createMessageCopy(BaseMessage msg) {
        BaseMessage copy = new BaseMessage(msg.getRole(), msg.getContent(), msg.getName());
        return copy;
    }
}

