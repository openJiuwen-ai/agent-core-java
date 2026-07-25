# Interact Weather Assistant (Java)

这个目录提供一个 Java 交互天气助理示例，实现上做了两件额外的事：

1. 交互、并发提问、同一 `sessionId` 的 checkpoint 恢复，仍然使用原生 `Workflow`
2. 模型配置改为读取真实 `examples/apiconfig.json`，天气查询接口复用 `examples/reac_agent/ReActWeatherAgentExample.java` 的同款天气 API

## 文件说明

- `WeatherAssistantInteractExample.java`: 示例入口。
- `WeatherAssistantInteractExampleSupport.java`: 共享实现，负责构图、交互循环、真实天气查询和模型摘要。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 这个示例演示什么

1. 调用天气接口前，先做余额确认
2. 在同一轮 checkpoint 里并发询问城市和温度单位
3. 天气查询节点第一次固定失败，用户输入 `retry` 后用同一 `sessionId` 恢复
4. 最终天气摘要由 `examples/apiconfig.json` 中配置的真实模型生成

## 配置

1. 运行前必须在 `examples/apiconfig.json` 中填入真实模型配置。
2. 天气接口默认使用 `https://uapis.cn/api/v1/misc/weather`。
3. 如果你要切换到自己的天气服务，覆盖方式与 `ReActWeatherAgentExample` 完全一致：
   - 环境变量 `WEATHER_URL`
   - JVM 参数 `-Dopenjiuwen.example.weatherUrl=https://your-weather-service/weather`

## 运行方式

以下命令假设当前目录是 Java 仓库根目录，也就是包含 `pom.xml`、`examples` 和 `src` 的目录：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/interact.classpath"
javac -cp "target/classes;$(Get-Content target/interact.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/interact/WeatherAssistantInteractExampleSupport.java examples/interact/WeatherAssistantInteractExample.java
java -cp "target/classes;examples;examples/interact;$(Get-Content target/interact.classpath -Raw)" WeatherAssistantInteractExample
```

也可以在最后一条命令后直接附带一条初始问题：

```powershell
java -cp "target/classes;examples;examples/interact;$(Get-Content target/interact.classpath -Raw)" WeatherAssistantInteractExample 明天我要去上海出差，帮我查天气
```

## 交互说明

- `user>`: 输入新的天气请求，会创建一个新的 `sessionId`
- `reply>`: 回答 workflow 的中断问题
- `retry>`: 当天气节点首次故意失败后，输入 `retry` 用同一 `sessionId` 恢复
- `exit` / `quit`: 结束示例

## Windows PowerShell 中文输入

如果你要通过管道把中文输入直接送进 `System.in`，先切到 UTF-8：

```powershell
chcp 65001 | Out-Null
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$classpath = (Get-Content "target/interact.classpath" -Raw).Trim()
$runtimeClasspath = "target/classes;examples;examples/interact;$classpath"
@(
  "明天我要出差，帮我查天气并给出建议",
  "Yes",
  "Shanghai",
  "C",
  "retry",
  "exit"
) | & java '-Dfile.encoding=UTF-8' '-cp' $runtimeClasspath 'WeatherAssistantInteractExample'
```

## 示例流程

这个示例的稳定流程是：

1. `CheckBalance` 固定模拟低余额，所以一定会先问一次确认
2. 余额确认通过后，`AskCity` 和 `AskUnit` 会在同一轮返回两个交互问题
3. `QueryWeather` 第一次固定报错，用来演示同一 `sessionId` 的失败恢复
4. 第二次输入 `retry` 后，`QueryWeather` 才会真正请求天气 API
5. `SummarizeWeather` 使用 `examples/apiconfig.json` 里的真实模型生成最终回答

因此，这个示例既能展示交互恢复语义，也能验证真实配置和真实外部接口都已经接通。