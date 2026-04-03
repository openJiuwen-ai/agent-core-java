# ReAct Agent Java Example

这个目录对应 Python 版 `examples/react_agent` notebook，提供了一个基于 Java 框架的 ReAct 天气助手示例。

## 文件说明

- `ReActWeatherAgentExample.java`: 示例入口，创建 `ReActAgent`、注册天气 REST 工具并发起一次查询。
- `ExampleApiConfigLoader.java`: 从 `examples/apiconfig.json` 读取模型 API 配置。

## 配置

1. 运行时读取 `examples/apiconfig.json` 中的真实大模型 API 配置。
2. `examples/apiconfig_example.json` 只是脱敏示例模板，不会被运行时代码自动读取。
3. 天气工具默认调用 `https://uapis.cn/api/v1/misc/weather`，查询参数使用 `city`，并默认带上 `forecast=true` 获取多天预报。
4. 如果你想切换到自己的天气服务，可以通过以下任一方式覆盖天气接口 URL:
   - 环境变量 `WEATHER_URL`
   - JVM 参数 `-Dopenjiuwen.example.weatherUrl=https://your-weather-service/weather`

如果没有提供 `WEATHER_URL`，示例会直接使用默认的 `uapis` 天气接口，不再启动本地 mock 服务。

## 运行方式

建议先在 `agent-core-java-myfork` 目录下执行一次编译：

注意：下面的 `javac` 命令会把 `.class` 文件直接生成到 `examples/reac_agent` 目录中。

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/reac_agent.classpath"
javac -cp "target/classes;$(Get-Content target/reac_agent.classpath -Raw)" examples/reac_agent/ExampleApiConfigLoader.java examples/reac_agent/ReActWeatherAgentExample.java
java -cp "target/classes;examples/reac_agent;$(Get-Content target/reac_agent.classpath -Raw)" ReActWeatherAgentExample
```

也可以在最后一条命令后追加查询内容，例如：

```powershell
java -cp "target/classes;examples/reac_agent;$(Get-Content target/reac_agent.classpath -Raw)" ReActWeatherAgentExample 查询北京明天天气
```

## 输出

示例最终会打印 `Runner.runAgent(...)` 返回的结果对象，通常包含：

- `output`: Agent 最终回答
- `result_type`: 一般为 `answer`