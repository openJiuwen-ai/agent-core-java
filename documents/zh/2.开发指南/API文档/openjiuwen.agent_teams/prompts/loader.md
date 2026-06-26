# openjiuwen.agent_teams.prompts.loader

`PromptLoader` 对应 Python `loader.py`，负责从 agent-team prompts 资源目录读取 Markdown 模板并构造 `PromptTemplate`。

## Java 对应

- `com.openjiuwen.agent_teams.prompts.PromptLoader`

## 方法

- `loadTemplate(String name)`：按默认语言 `cn` 读取 `<lang>/<name>.md`。
- `loadTemplate(String name, String language)`：按指定语言读取 `<lang>/<name>.md`。
- `loadSharedTemplate(String name)`：读取语言无关的 `<name>.md`。

读取结果按 `(name, language)` 缓存；缺失资源会抛出 `UncheckedIOException`，对应 Python 读取缺失文件时的异常行为。
