# ToolGroup + Skill 绑定示例

这个示例在不调用真实大模型的情况下，演示 Issue #196 引入的三个关键语义：

1. ToolGroup 只影响当前 `AgentSession` 的可见性和可执行性。
2. Skill 通过 `FileSystemSkillRepository` 注册，激活后通过保留组 `skill:<skill-name>` 暴露绑定的工具。
3. 显式停用的 ToolGroup 优先于激活 Skill 带来的可见性。

## 前置条件

- JDK 17 或以上。
- 从仓库根目录 `F:\huawei\agent-core-java` 运行命令。
- 不需要真实 LLM、真实 MCP server 或网络访问；MCP 只注册一个 mock 配置，用于验证分组规则。

## 运行

先编译主模块：

```powershell
mvn -DskipTests package
```

生成 classpath 并编译示例：

```powershell
mvn -pl core dependency:build-classpath "-Dmdep.outputFile=target/toolgroup_example.classpath"
javac -encoding UTF-8 -cp "core/target/classes;$(Get-Content core/target/toolgroup_example.classpath -Raw)" examples/toolgroup/ToolGroupSkillExample.java
```

运行示例：

```powershell
java '-Dfile.encoding=UTF-8' -cp "core/target/classes;.;$(Get-Content core/target/toolgroup_example.classpath -Raw)" examples.toolgroup.ToolGroupSkillExample
```

Linux/macOS 把 classpath 分隔符 `;` 改成 `:`，并删除 PowerShell 的引号写法。

## 示例覆盖的规则

- `clock` 未分组，任何有 groups 的 session 中仍然可见、可执行。
- `skill_search` 只通过 Skill 绑定注册，同时被显式组 `research` 引用；停用 `research` 后，即使 Skill 仍激活，`skill_search` 也会从 prompt 中消失，并拒绝执行。
- `delete_file` 属于危险组 `danger`；未激活时直连调用会得到包含 `not active` 的 `ToolMessage`。
- `mcp_weather_forecast` 属于 `mcp_tools`；它由本地假实现提供结果，同时注册一个 MCP 配置元数据，不建立网络连接。
- Skill 样例位于 `examples/toolgroup/skills/web_research/SKILL.md`，prompt 默认只渲染元数据；正文通过 `skill_tool` 按需读取。

## 已知边界

- 示例不执行完整 Agent loop，也不请求 LLM。
- 分组控制不替代操作系统沙箱、权限护栏或内容安全检测。
- 同名工具跨组采用并集语义；只要其中一个组激活即可见，停用某一个组不会禁用另一个激活组中的同名工具。
