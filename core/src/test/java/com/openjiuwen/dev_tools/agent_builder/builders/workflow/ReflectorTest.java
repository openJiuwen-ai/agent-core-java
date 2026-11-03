/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's DL reflector behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_reflector.py}.
 */
class ReflectorTest {

    @Test
    void extractsPlaceholderContent() {
        Reflector.PlaceholderContent content = Reflector.extractPlaceholderContent("${start.query} and ${llm.rawOutput}");

        assertThat(content.hasPlaceholder()).isTrue();
        assertThat(content.matches()).containsExactly("start.query", "llm.rawOutput");
        assertThat(Reflector.extractPlaceholderContent("plain").hasPlaceholder()).isFalse();
    }

    @Test
    void acceptsValidStartLlmEndFlow() {
        String dl = """
                [
                  {
                    "id": "start",
                    "type": "Start",
                    "description": "start node",
                    "parameters": {
                      "outputs": [
                        {"name": "query", "description": "用户输入"}
                      ]
                    },
                    "next": "llm"
                  },
                  {
                    "id": "llm",
                    "type": "LLM",
                    "description": "llm node",
                    "parameters": {
                      "inputs": [
                        {"name": "query", "value": "${start.query}"}
                      ],
                      "outputs": [
                        {"name": "rawOutput", "description": "answer"}
                      ],
                      "configs": {
                        "system_prompt": "system",
                        "user_prompt": "user"
                      }
                    },
                    "next": "end"
                  },
                  {
                    "id": "end",
                    "type": "End",
                    "description": "end node",
                    "parameters": {
                      "inputs": [
                        {"name": "answer", "value": "${llm.rawOutput}"}
                      ],
                      "configs": {
                        "template": "${llm.rawOutput}"
                      }
                    }
                  }
                ]
                """;

        Reflector reflector = new Reflector();
        reflector.checkFormat(dl);

        assertThat(reflector.getErrors()).isEmpty();
        assertThat(reflector.getNodeIds()).containsExactly("start", "llm", "end");
        assertThat(reflector.getAvailableNodeOutputs()).containsExactly("start.query", "llm.rawOutput");
    }

    @Test
    void rejectsNonArrayJsonTopLevel() {
        Reflector reflector = new Reflector();

        reflector.checkFormat("{\"id\":\"only\"}");

        assertThat(reflector.getErrors()).hasSize(1);
        assertThat(reflector.getErrors().get(0)).startsWith("JSON格式错误:");
    }

    @Test
    void detectsBranchReferenceAndDefaultErrors() {
        String dl = """
                [
                  {
                    "id": "start",
                    "type": "Start",
                    "description": "start node",
                    "parameters": {
                      "outputs": [
                        {"name": "query", "description": "用户输入"}
                      ]
                    },
                    "next": "branch"
                  },
                  {
                    "id": "branch",
                    "type": "Branch",
                    "description": "branch node",
                    "parameters": {
                      "conditions": [
                        {
                          "branch": "bad",
                          "description": "bad reference",
                          "expression": "${missing.value} eq 1",
                          "next": "end"
                        }
                      ]
                    }
                  },
                  {
                    "id": "end",
                    "type": "End",
                    "description": "end node",
                    "parameters": {
                      "inputs": [
                        {"name": "answer", "value": "${start.query}"}
                      ],
                      "configs": {
                        "template": "${start.query}"
                      }
                    }
                  }
                ]
                """;

        Reflector reflector = new Reflector();
        reflector.checkFormat(dl);

        assertThat(reflector.getErrors())
                .anySatisfy(error -> assertThat(error).contains("引用了不存在的变量"))
                .anySatisfy(error -> assertThat(error).contains("缺少default分支"));
    }
}
