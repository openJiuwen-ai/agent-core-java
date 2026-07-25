# Hierarchical Group Java Example

这个目录演示如何在 Java 框架中组织一个 leader-worker 结构的 group：

1. 外部请求先进入 group。
2. group 默认把请求交给 leader `main_controller`。
3. leader 根据请求内容把任务分配给对应 worker。
4. worker 通过 workflow 执行业务，并在缺少信息时向用户提问。
5. 用户回答后，leader 会把 reply 继续路由回上一次中断的 worker。

## 文件说明

- `HierarchicalGroupExample.java`: 轻量入口。
- `HierarchicalGroupExampleSupport.java`: 创建 group、leader、worker，并启动命令行交互。
- `HierarchicalGroupController.java`: group 级三段式路由控制器。
- `HierarchicalLeaderAgent.java`: 示例内部的 leader agent，负责 worker 选择与中断恢复。
- `../README.md`: groups 目录总览。
- `../../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 当前实现说明

这个 Java 示例保留了 leader-worker 的核心结构：

1. `HierarchicalGroupController` 仍然实现三段式路由：显式 `receiver_id`、订阅路由、默认 leader。
2. leader 仍然只做分发与恢复，不直接处理业务。
3. worker 仍然是真正执行业务的 agent。

当前 Java 示例的实现边界是：

1. 这里的 leader 采用示例内的确定性关键词路由，而不是完整的 LLM intent detection。
2. 这样做是为了避开当前 Java examples 层 group/controller event 适配尚未完全统一的问题，同时保证示例能直接运行。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行下面的命令，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。
3. 使用的模型需要能理解中文，供 worker 内部的 `QuestionerComponent` 提问和字段抽取。
4. 模型服务账号需要有足够额度；如果额度不足，worker 在补充信息后的字段抽取阶段可能直接返回服务端错误。

## 运行方式

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/groups_examples.classpath"
javac -cp "target/classes;$(Get-Content target/groups_examples.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/groups/hierarchical_group/HierarchicalGroupController.java examples/groups/hierarchical_group/HierarchicalLeaderAgent.java examples/groups/hierarchical_group/HierarchicalGroupExampleSupport.java examples/groups/hierarchical_group/HierarchicalGroupExample.java
java -cp "target/classes;examples;examples/groups/hierarchical_group;$(Get-Content target/groups_examples.classpath -Raw)" HierarchicalGroupExample
```

也可以在最后一条命令后先追加一条初始查询，程序会先执行该查询，再进入交互模式：

```powershell
java -cp "target/classes;examples;examples/groups/hierarchical_group;$(Get-Content target/groups_examples.classpath -Raw)" HierarchicalGroupExample 我要转账
```

## Windows PowerShell 中文输入

如果你是在终端里手工输入中文，通常不需要额外处理。

如果你想通过 PowerShell 管道把中文直接喂给 `System.in`，建议像其他 Java examples 一样先强制切到 UTF-8：

```powershell
chcp 65001 | Out-Null
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$classpath = (Get-Content "target/groups_examples.classpath" -Raw).Trim()
$runtimeClasspath = "target/classes;examples;examples/groups/hierarchical_group;$classpath"
@("我要转账", "2000元", "exit") | & java '-Dfile.encoding=UTF-8' '-cp' $runtimeClasspath 'HierarchicalGroupExample'
```

## 已验证目标场景

这个示例的目标行为是：

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

```text
user> 帮我写个旅游攻略
assistant> 我目前只支持转账、理财和余额查询三类 worker，请明确说明你的需求。
```

## 输出说明

示例直接打印最终用户可读结果：

1. 如果 worker 正常完成，会打印完成结果。
2. 如果 worker 进入 `QuestionerComponent` 中断，会打印补充问题，并把下一条 `reply>` 自动路由回同一个 worker。
3. 如果 leader 没有匹配到已知 worker，会打印默认回复。

## 日志说明

这个示例直接复用了框架里的 controller、graph 和 workflow 执行链路，因此终端里会看到一批 `INFO` 或 `ERROR` 日志穿插在交互输出之间。

其中有两类日志尤其需要注意：

1. `GraphInterrupt` 相关日志通常表示 `QuestionerComponent` 正常中断并等待用户补充输入，不代表示例失败。
2. 如果模型服务返回 `HTTP 403`、额度不足或鉴权失败，这会是真正的外部依赖错误，示例会输出一条简化后的失败信息后结束。

## 当前实现边界

这个示例有意保持在 example 层：

1. 没有把 `HierarchicalGroup` 提升为 `src/main` 的正式公共类。
2. 没有引入完整的 LLM 意图分类 leader。
3. 重点是提供一个能运行、能展示 leader-worker 协作模式的 Java 示例。