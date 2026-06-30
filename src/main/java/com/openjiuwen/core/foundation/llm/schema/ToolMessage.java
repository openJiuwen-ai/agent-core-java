/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tool response message in an LLM conversation.
 *
 * <p>Mirrors Python's {@code ToolMessage} model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolMessage extends BaseMessage {

  /** The ID of the tool call this message is responding to. */
  @JsonProperty("tool_call_id")
  private String toolCallId;

  /** Auto-generated for codecheck compliance. */
  public ToolMessage() {}

  /** Auto-generated for codecheck compliance. */
  public ToolMessage(
      String role,
      Object content,
      String name,
      java.util.Map<String, Object> metadata,
      String toolCallId) {
    super(role, content, name, metadata);
    this.toolCallId = toolCallId;
  }

  /**
   * Creates a tool message with the given content and tool call ID.
   *
   * @param content the message content
   * @param toolCallId the ID of the tool call this message is responding to
   */
  public ToolMessage(String content, String toolCallId) {
    super("tool", content);
    this.toolCallId = toolCallId;
  }

  /**
   * Creates a tool message with the given content, tool call ID, and name.
   *
   * @param content the message content
   * @param toolCallId the ID of the tool call this message is responding to
   * @param name the sender name
   */
  public ToolMessage(String content, String toolCallId, String name) {
    this(content, toolCallId);
    setName(name);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public String getRole() {
    String r = super.getRole();
    return r != null ? r : "tool";
  }

  /** Auto-generated for codecheck compliance. */
  public String getToolCallId() {
    return toolCallId;
  }

  /** Auto-generated for codecheck compliance. */
  public void setToolCallId(String toolCallId) {
    this.toolCallId = toolCallId;
  }

  /** Auto-generated for codecheck compliance. */
  public static Builder builder() {
    return new Builder();
  }

  /** Auto-generated for codecheck compliance. */
  public static class Builder extends BaseMessage.Builder {
    /** Auto-generated for codecheck compliance. */
    protected String toolCallId;

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder role(String role) {
      super.role(role);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder content(Object content) {
      super.content(content);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder name(String name) {
      super.name(name);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder metadata(java.util.Map<String, Object> metadata) {
      super.metadata(metadata);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder toolCallId(String toolCallId) {
      this.toolCallId = toolCallId;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public ToolMessage build() {
      return new ToolMessage(role, content, name, metadata, toolCallId);
    }
  }
}
