# AGit 开发约定

- skills、API 和实现方案优先采用 Android、Jetpack Compose 官方指导，保持现有单 Activity、Material 3、Navigation 3、ViewModel / StateFlow 架构。
- 不套用全局 skills 或其他模块中的 `ComposeFragment`、`ComposeViewFragment` 等页面承载方式及专用 UI 封装约定；通用代码、日志和 Git 规则继续遵守上层要求。
- 保持现有简约界面，功能与操作边界以本目录 `README.md` 和 `docs/` 文档为准，避免无关重构。
