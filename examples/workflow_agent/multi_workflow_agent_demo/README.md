# Multi Workflow Agent Demo (Java)

这个子目录提供多工作流 agent 的推荐 Java 示例入口。

这里没有额外拆分独立的 event handler / executor 示例文件，因为这些职责已经由框架内置类承担：

- `com.openjiuwen.core.application.workflow.WorkflowAgent`
- `com.openjiuwen.core.application.workflow.WorkflowEventHandler`

这个目录只保留推荐入口类，实际多工作流示例逻辑复用上层目录中的 `WorkflowAgentExampleSupport.java`。

## 文件说明

- `MultiWorkflowAgentDemo.java`: 推荐入口。
- `../WorkflowAgentExampleSupport.java`: 共享的多工作流示例实现。
- `../../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 配置

1. 运行时读取 `examples/apiconfig.json` 中的真实大模型 API 配置。
2. `examples/apiconfig_example.json` 只是脱敏模板，不会被运行时代码自动读取。
3. 示例依赖具备中文理解能力的对话模型，用于工作流意图路由和字段提取。

## 运行方式

以下命令假设当前目录是 Java 仓库根目录，也就是包含 `pom.xml`、`examples` 和 `src` 的目录：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/workflow_agent.classpath"
javac -cp "target/classes;$(Get-Content target/workflow_agent.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/workflow_agent/WorkflowAgentExampleSupport.java examples/workflow_agent/multi_workflow_agent_demo/MultiWorkflowAgentDemo.java
java -cp "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$(Get-Content target/workflow_agent.classpath -Raw)" MultiWorkflowAgentDemo
```

也可以在最后一条命令后面追加初始查询：

```powershell
java -cp "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$(Get-Content target/workflow_agent.classpath -Raw)" MultiWorkflowAgentDemo 我要转账
```

如果你要在 Windows PowerShell 下通过管道把中文输入直接传给示例，先切到 UTF-8；否则 Java 进程可能把中文读成 `????`：

```powershell
chcp 65001 | Out-Null
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$classpath = (Get-Content "target/workflow_agent.classpath" -Raw).Trim()
$runtimeClasspath = "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$classpath"
@("帮我查一下余额", "62220001", "exit") | & java '-Dfile.encoding=UTF-8' '-cp' $runtimeClasspath 'MultiWorkflowAgentDemo'
```

## 已验证场景

这个推荐入口已经用真实模型配置实际验证过以下三条场景：

- 转账：`我要转账` -> `2000元` -> `转账服务完成，记录的转账金额为 2000元。`
- 理财：`我想买理财产品` -> `稳健理财` -> `理财服务完成，选择的理财产品为 稳健理财。`
- 余额查询：`帮我查一下余额` -> `62220001` -> `余额查询完成，登记的账户号码为 62220001。`

## 日志说明

`QuestionerComponent` 在等待用户补充信息时，底层图执行器会打印 `GraphInterrupt` 的 `ERROR` 日志。当前这是预期中的中断恢复行为，不是运行失败；只要后续出现 `assistant>` 的追问和完成结果，就说明示例正常。

## 兼容入口

如果你已经在使用旧入口 `WorkflowAgentExample`，它仍然保留并转发到同一份实现；只是推荐入口现在是 `MultiWorkflowAgentDemo`。