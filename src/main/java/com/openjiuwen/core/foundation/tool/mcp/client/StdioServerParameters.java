// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stdio服务器参数配置
 * 
 * <p>用于配置通过标准输入输出与MCP服务器通信的参数。
 * 对应Python的StdioServerParameters。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class StdioServerParameters {
    
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private String cwd;
    private String encodingErrorHandler;
    
    /**
     * 默认构造函数
     */
    public StdioServerParameters() {
        this.args = new ArrayList<>();
        this.env = new HashMap<>();
        this.encodingErrorHandler = "strict";
    }
    
    /**
     * 完整构造函数
     */
    public StdioServerParameters(String command, List<String> args, 
                                 Map<String, String> env, String cwd, 
                                 String encodingErrorHandler) {
        this.command = command;
        this.args = args != null ? args : new ArrayList<>();
        this.env = env != null ? env : new HashMap<>();
        this.cwd = cwd;
        this.encodingErrorHandler = encodingErrorHandler != null ? encodingErrorHandler : "strict";
    }
    
    // Getters and Setters
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public List<String> getArgs() {
        return args;
    }
    
    public void setArgs(List<String> args) {
        this.args = args != null ? args : new ArrayList<>();
    }
    
    public Map<String, String> getEnv() {
        return env;
    }
    
    public void setEnv(Map<String, String> env) {
        this.env = env != null ? env : new HashMap<>();
    }
    
    public String getCwd() {
        return cwd;
    }
    
    public void setCwd(String cwd) {
        this.cwd = cwd;
    }
    
    /**
     * 获取编码错误处理器
     * 
     * @return "strict", "ignore", 或 "replace"
     */
    public String getEncodingErrorHandler() {
        return encodingErrorHandler;
    }
    
    /**
     * 设置编码错误处理器
     * 
     * @param encodingErrorHandler 必须是 "strict", "ignore", 或 "replace"
     */
    public void setEncodingErrorHandler(String encodingErrorHandler) {
        if (encodingErrorHandler == null || 
            (!encodingErrorHandler.equals("strict") && 
             !encodingErrorHandler.equals("ignore") && 
             !encodingErrorHandler.equals("replace"))) {
            this.encodingErrorHandler = "strict";
        } else {
            this.encodingErrorHandler = encodingErrorHandler;
        }
    }
    
    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder类
     */
    public static class Builder {
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private String cwd;
        private String encodingErrorHandler = "strict";
        
        public Builder command(String command) {
            this.command = command;
            return this;
        }
        
        public Builder args(List<String> args) {
            this.args = args;
            return this;
        }
        
        public Builder addArg(String arg) {
            this.args.add(arg);
            return this;
        }
        
        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }
        
        public Builder addEnv(String key, String value) {
            this.env.put(key, value);
            return this;
        }
        
        public Builder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }
        
        public Builder encodingErrorHandler(String encodingErrorHandler) {
            this.encodingErrorHandler = encodingErrorHandler;
            return this;
        }
        
        public StdioServerParameters build() {
            return new StdioServerParameters(command, args, env, cwd, encodingErrorHandler);
        }
    }
}

