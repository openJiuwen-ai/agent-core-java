// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import java.util.Map;

/**
 * 单个Markdown元素。
 * 对应 Python: markdown_output_parser.py - MarkdownElement (dataclass)
 * 
 * @param type 元素类型
 * @param content 元素内容
 * @param startPos 在原文本中的起始位置
 * @param endPos 在原文本中的结束位置
 * @param raw 原始文本
 */
public record MarkdownElement(
    String type,
    Map<String, Object> content,
    int startPos,
    int endPos,
    String raw
) {}

