# PostgreSQL

第一版暂不使用 Flyway，数据库初始化统一维护在以下两个文件中：

- `schema.sql`：扩展、Schema、表、索引和约束。
- `data.sql`：基础角色、权限、系统配置等初始化数据。

初始化顺序固定为先执行 `schema.sql`，再执行 `data.sql`。这两个文件是当前数据库结构和初始化数据的
唯一事实来源，不在 `admin` 或 `application` 模块中维护其他初始化脚本。
