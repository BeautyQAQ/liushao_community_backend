# NAS 开发基础设施

2026-10-13：读取 `.codex/config.toml`，通过配置中的 HTTP MCP 服务连接 dbx，使用指定的 `nas-mysql` 与 `nas-redis` 创建项目开发资源。

| 项目 | MySQL 数据库 | Redis 逻辑库 | 用途 |
| --- | --- | --- | --- |
| authority_management_backend | liushao_community | DB 12 | 管理端，Redis 用于验证码等 |
| liushao_community_backend | liushao_community | DB 13 | 社区端，登录会话与限流 |

应用可连接的 NAS 地址：`192.168.20.5`，MySQL 端口 `3306`，Redis 端口 `6379`。DBX 连接中的 `host.docker.internal` 是 DBX 容器内部使用的地址，本机使用 NAS IP。

2026-10-28 通过 dbx 将 `vueadmin` 的 5 张业务表及版本历史表原子迁移到 `liushao_community`，管理端历史表改名为 `flyway_admin_schema_history`，社区历史表保留 `flyway_schema_history`。共享数据库采用 `utf8mb4 / utf8mb4_unicode_ci`。同日启动两端应用完成增量升级，当前管理端为 V2、社区端为 V5，共 25 张表（含两张历史表和历史 ID 映射表）。旧 `vueadmin` 数据库保留为空库。移表记录见 [数据库合并记录](../../authority_management_backend/docs/database-consolidation.md)。

仓库旧社区 SQL 已转换为当前整数 ID 并导入共享测试库：2 个社区账号、12 篇帖子、14 个标签、14 条有效标签关联和 1 条有效关注，以及公告、提示语和推荐链接。重复邮箱和无效关联已处理，67 条 ID 映射持久保留；重复执行没有新增记录。见 [历史社区迁移记录](legacy-community-migration.md)。

Redis 逻辑库预先存在，不需要 CREATE DATABASE；已确认 DB 12、13 为空后，用以下键标记项目用途：

- DB 12：`liushao:admin:database-purpose`
- DB 13：`liushao:community:database-purpose`

这属于项目约定，未修改 Redis ACL 或服务级配置。社区后端已接入 Redis 会话和登录/注册限流，使用 `liushao:community:` 前缀。

## 本地配置与验证

- 两个后端的 `.env.local` 已写入连接配置、独立 JWT 密钥；文件权限为 `0600`，被 Git 忽略。
- 管理端支持 `REDIS_DATABASE`，本地设置为 `12`；示例中的默认值仍为 `0`。
- 管理端业务管理员 `admin` 已初始化，独立密码保存在本地 `.env.local` 的 `ADMIN_PASSWORD` 中。首次初始化开关已恢复为 `BOOTSTRAP_ADMIN=false`，后续改密使用个人中心。
- 使用 `/opt/java/jdk21` 和实际 MySQL JDBC 驱动验证两端连接共享库。
- 使用实际本地配置连接 Redis，认证、选择 DB 12/13 和 PING 均成功。
- 通过 dbx MCP 复核了数据库字符集和 Redis 项目标识；阶段二再次检查管理库迁移与测试数据清理。

管理端已完成真实 HTTP/MySQL/Redis 与浏览器联调：验证码、会话、用户/角色/菜单 CRUD、权限分配与撤销、改密及退出均通过。2026-10-28 在共享库再次通过上述管理接口验证，并验证社区历史内容读取/搜索、新注册、关注动态、点赞收藏评论、草稿发布、作者编辑和后台审核。临时业务数据已清理，历史数据和原管理账号保留。两端本地配置已设置相同的 `COMMUNITY_ADMIN_KEY` 以连通运营代理，凭据不写入本说明或版本库。
