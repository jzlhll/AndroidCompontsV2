# 数据迁移

- 后续修改均须评估数据迁移与旧版本兼容，涵盖 Room、配置、本地文件及媒体引用。
- Room 结构变更须同步维护版本、schema 和 Migration 或 AutoMigration，保留已有用户数据，禁止以清库或 `fallbackToDestructiveMigration` 代替迁移。
- 迁移须考虑默认值、数据转换、重复执行和中断恢复；交付时说明兼容限制与未解决风险。
