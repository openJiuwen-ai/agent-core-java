# Workflow Agent Java Example

这个目录现在同时提供：

1. 推荐子目录入口 `multi_workflow_agent_demo`
2. 兼容旧命令的 `WorkflowAgentExample` 入口

整体演示目标不变，仍然是在 Java 框架里：

1. 创建一个 `WorkflowAgent`
2. 注册多个金融工作流
3. 在 `QuestionerComponent` 中断后继续同一个会话
4. 通过 `InteractiveInput` 恢复工作流执行

## 文件说明

- `multi_workflow_agent_demo/MultiWorkflowAgentDemo.java`: 推荐入口。
- `WorkflowAgentExampleSupport.java`: 共享的多工作流示例实现，负责创建金融助手、注册三个工作流并启动命令行交互。
- `WorkflowAgentExample.java`: 兼容入口，转发到新的共享实现。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 配置

1. 运行时读取 `examples/apiconfig.json` 中的真实大模型 API 配置。
2. `examples/apiconfig_example.json` 只是脱敏模板，不会被运行时代码自动读取。
3. 该示例默认依赖一个具备中文理解能力的对话模型，用于：
   - 在多个 workflow 之间做意图路由
   - 从用户回答中提取 `amount`、`product`、`account` 等字段

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行下面的命令，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。
3. 如果模型服务启用了自签名证书，请在 `apiconfig.json` 中把 `LLM_SSL_VERIFY` 配成合适的值。

## 运行方式

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/workflow_agent.classpath"
javac -cp "target/classes;$(Get-Content target/workflow_agent.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/workflow_agent/WorkflowAgentExampleSupport.java examples/workflow_agent/multi_workflow_agent_demo/MultiWorkflowAgentDemo.java examples/workflow_agent/WorkflowAgentExample.java
java -cp "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$(Get-Content target/workflow_agent.classpath -Raw)" MultiWorkflowAgentDemo
```

也可以在最后一条命令后先追加一条初始查询，程序会先执行该查询，再进入交互模式：

```powershell
java -cp "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$(Get-Content target/workflow_agent.classpath -Raw)" MultiWorkflowAgentDemo 我要转账
```

如果你已经依赖旧类名，也可以继续运行兼容入口：

```powershell
java -cp "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$(Get-Content target/workflow_agent.classpath -Raw)" WorkflowAgentExample
```

## Windows PowerShell 中文输入

如果你是在终端里手工输入中文，通常不需要额外处理。

如果你想像自动化验证那样通过 PowerShell 管道把中文直接喂给 `System.in`，需要先强制切到 UTF-8；否则 Java 进程可能会读到 `????`，意图识别会退回默认回复。

下面是一个已经验证可用的非交互运行示例：

```powershell
chcp 65001 | Out-Null
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$classpath = (Get-Content "target/workflow_agent.classpath" -Raw).Trim()
$runtimeClasspath = "target/classes;examples;examples/workflow_agent;examples/workflow_agent/multi_workflow_agent_demo;$classpath"
@("我要转账", "2000元", "exit") | & java '-Dfile.encoding=UTF-8' '-cp' $runtimeClasspath 'MultiWorkflowAgentDemo'
```

启动后支持以下交互形式：

- `user>`: 输入新的业务请求，例如 `我要转账`
- `reply>`: 回答上一个 workflow 的补充问题，例如 `2000 元`
- `quit` 或 `exit`: 退出示例

## 已验证场景

这个 Java 示例已经用真实 `examples/apiconfig.json` 配置实际跑通以下三条 workflow：

```text
user> 我要转账
assistant> 请补充转账金额，必须是数字或带货币单位的金额描述。
reply> 2000元
assistant> 转账服务完成，记录的转账金额为 2000元。
```

```text
user> 我想买理财产品
assistant> 请补充理财产品名称，例如稳健理财、现金管理类产品。
reply> 稳健理财
assistant> 理财服务完成，选择的理财产品为 稳健理财。
```

```text
user> 帮我查一下余额
assistant> 请补充需要查询余额的账户号码。
reply> 62220001
assistant> 余额查询完成，登记的账户号码为 62220001。
```

## 输出说明

示例会打印 `WorkflowAgent` 的用户可读结果，而不是原始控制器对象。典型流程如下：

```text
user> 我要转账
assistant> 请补充转账金额
reply> 2000 元
assistant> 转账服务完成，记录的转账金额为 2000 元。
```

```text
user> 我想买理财产品
assistant> 请补充理财产品名称
reply> 稳健理财
assistant> 理财服务完成，选择的理财产品为 稳健理财。
```

```text
user> 帮我查一下余额
assistant> 请补充账户号码
reply> 62220001
assistant> 余额查询完成，登记的账户号码为 62220001。
```

如果用户输入和这三个 workflow 都不匹配，示例会返回默认回复，提醒用户当前只支持转账、理财和余额查询。

## 日志说明

`QuestionerComponent` 触发补充提问时，底层图执行器目前会打印一段 `GraphInterrupt` 的 `ERROR` 日志。这是当前实现里“正常中断并等待用户输入”的表现，不代表示例失败。

判断示例是否真的失败，应以这几个信号为准：

- 是否打印了 `assistant>` 的补充问题。
- 在 `reply>` 回答后，是否进入对应 workflow 的完成结果。
- 是否最终输出了用户可读的完成文本，而不是停在异常堆栈后无响应。

更多关于推荐子目录入口的说明见 `multi_workflow_agent_demo/README.md`。