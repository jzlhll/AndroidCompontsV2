---
description: 项目规则，包含行为, 说中文, 禁止项, skills大全和日志规范
trigger: always_on
---

# 行为准则

## 常规
- 只修改指定部分
- **重要**：每次修改代码之前，一定要以最新的代码为准
- Plan模式下，反复思考只要有疑问就询问我，直到没有任何疑问

## 说中文
- **十分重要**：任何输出，包括并不限于plan/checklist/spec/tasks 等 markdown 或文本，必须中文。
- **注释**：代码中注释必须使用中文，日志英文。

## 禁止项
- 严格按需求输出，不加额外内容
- 不重构未要求代码
- 不说开场白/结束语
- 不生成测试代码和示例代码
- 不重复用户话
- 不输出代码概览
- **不要**主动运行 Gradle 等编译/构建命令
- 不使用 `coerceAtLeast`、`coerceAtMost`、`coerceIn`、`minOf`、`maxOf`，统一使用 `min` / `max`

## 任务完成后的输出格式

任务完成后，不输出实现细节、原因解释或废话，使用 Markdown 列表格式（`-` 开头）逐行输出:
- 类名(不要全路径)：一句话概括变更
- 总结(多文件)：一句话概括整体变更
- 如果只有1-2个文件被修改，则只做一句话总结

# kotlin/kts文件日志规范

- 优先 `logdNoFile`（仅控制台）替代Log.d
- 函数名拆分（如 `onDeviceListChanged` → on deviceList changed）
- 严重错误使用 `loge { "" + e.message }` / `logEx(throwable = e) { "xxx" }`
