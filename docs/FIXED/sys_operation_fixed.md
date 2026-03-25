# sys_operation 第二轮检查缺漏

## 检查结论

- 第二轮复核后，`sysop` 已不存在“整类缺失”问题。
- 旧文档里提到的下列缺口已确认不是当前事实:
  - Java 已有 `SandboxGateway`、`ContainerManager`、`Container`、`SandboxClient`
  - Java `BaseOperation` 已有 `safeModelDump(...)` 和带 `extras` 的 `createSysOperationEvent(...)`
  - Java `OperationRegistry` 已有动态包扫描
  - Java `SysOperationToolAdapter` 已有 `getToolIdPrefix(List<String>)`
- 当前剩余问题集中在 `local/LocalFsOperation.java` 的实现语义，而不是 API 面缺类。

## 仍缺的部分

| 优先级 | 位置 | 缺口 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- | --- |
| P0 | `src/main/java/com/openjiuwen/core/sysop/local/LocalFsOperation.java` | `uploadFileStream(...)` 的 `lastChunk` 标记不正确 | Python `upload_file_stream()` 通过预读下一个 chunk 精确设置最后一块，最终块一定带 `is_last_chunk=True` | Java 在第 271-311 行按“双块一轮”处理，第二个 chunk 被硬编码成 `lastChunk(false)`；两块文件场景下没有任何块会被标记为最后一块 | 流式上传消费者无法可靠判断流结束，和 Python 的 DTO 契约不一致 |
| P1 | `src/main/java/com/openjiuwen/core/sysop/local/LocalFsOperation.java` | `resolvePath(...)` 没有完整保留 Python 的 work_dir 沙箱语义 | Python `_resolve_path()` 使用 `resolve()` + `relative_to(work_dir)`，并对路径片段做 `[^\\w.-] -> '_'` 清洗 | Java 第 577-605 行仅做 `normalize()` + `startsWith(workDir)`，没有 `resolve()` 级别的真实路径校验，也没有文件名片段清洗 | 与 Python 相比，Java 版对 work_dir 下的符号链接逃逸防护更弱，也丢失了路径片段清洗行为 |
| P1 | `src/main/java/com/openjiuwen/core/sysop/local/LocalFsOperation.java` | 文本模式 `readFile/readFileStream` 没有完整保留原始换行语义 | Python `read_file()` / `_stream_text_file()` 通过 `splitlines(True)` 或逐行读取保留原始换行；过滤读取时仍保留每行原始结尾 | Java `readTextContent()` 在第 666-682 行使用 `String.join(\"\\n\", ...)` 重新拼接；`streamTextFile()` / `emitStreamChunks()` 在第 716-794 行基于 `Files.readAllLines(...)` 产出 chunk，行结尾被剥离 | `head/tail/lineRange` 和文本流式读取的返回内容与 Python 不完全一致，尤其是 CRLF、末尾换行和空文件边界行为 |

## 不再算缺漏的适配项

- `snake_case -> camelCase`
- `async def` / `AsyncIterator` -> 同步方法 / `Iterator`
- `@operation(...)` -> `@Operation(...)`
- `ToolIdProxy.__getattr__` / `SysOperationCard.__getattr__` / `SysOperation.__getattr__` -> `toolId(...)` / `proxy(...)` / `getOperation(...)`

## 建议修复顺序

1. 先修 `uploadFileStream(...)` 的 `lastChunk` 判定，避免流式上传结果对象失真。
2. 再补 `resolvePath(...)` 的真实路径约束与路径片段清洗，恢复 Python 的 work_dir 沙箱语义。
3. 最后统一文本模式 `readFile/readFileStream` 的换行保留策略，确保 `head/tail/lineRange` 与流式输出结果一致。
