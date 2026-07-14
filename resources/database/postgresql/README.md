# PostgreSQL

用户端工作台的正式迁移脚本位于 `application/src/main/resources/db/migration`。本目录用于数据库说明、
诊断 SQL 和受控运维脚本，避免出现第二套迁移事实源。管理端产生独立表结构后，应在 `admin` 模块中
维护自己的迁移位置和 Flyway history table，避免两个服务争用同一迁移版本空间。
