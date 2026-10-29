# 流沙社区后端

JDK 21 + Spring Boot 3.5.16 + Spring Data JPA/Hibernate 6 + Flyway + MySQL + Redis。

## 启动

准备与管理后端共用的 MySQL 8 数据库 `liushao_community` 和 Redis，然后在本项目目录执行：

```bash
cp .env.example .env.local
# 填入 MySQL/Redis 连接配置及 JWT_SECRET
./mvnw spring-boot:run
```

默认端口 8080。包装脚本使用 `/opt/java/jdk21`，支持 `JAVA_HOME` 覆盖；首次下载并校验 Maven 3.9.11。未指定 profile 时默认启用 `local`，读取 `.env.local`（Java properties，值不用引号），环境变量优先。JWT 密钥至少 64 字节，可以用 `openssl rand -hex 64` 生成。

Flyway V1 自动创建 7 张基础业务表和版本历史表，初始化欢迎公告和提示语；V2 增加互动与运营，V3 增加邮件验证码和图片记录，V4 增加私有草稿，V5 保存历史数据 ID 映射，V6 将新帖默认状态改为待审核。社区迁移历史保存在 `flyway_schema_history`，管理端使用 `flyway_admin_schema_history`；新环境允许从版本 0 建立历史，管理端先建表时仍执行社区 V1。Hibernate 使用 `validate` 检查实体与表结构。

旧 `liushao_community.sql` 使用 varchar 雪花 ID，不能直接导入当前整数自增模型。2026-10-28 已通过下述转换工具将仓库历史数据导入 NAS 共享测试库；原密码哈希、正文和有效关系已保留。

```bash
./mvnw -DskipTests package
# 先启动当前社区后端，使目标库完成 Flyway V5，再执行：
python3 scripts/migrate_legacy.py
```

工具读取 `.env.local`，通过可执行 JAR 内的 JDBC 驱动连接 `liushao_community`；需要 Python 3、JDK 21，以及创建和删除临时数据库的权限。历史 SQL 在随机临时数据库中恢复，转换写入采用事务，结束后删除临时库；映射表用于避免重复导入。默认将旧 DATETIME 视为 `+08:00` 并转为 UTC，可用 `--source-offset` 指定来源时区偏移。支持 `--source`、`--env-file`、`--report`；结果及 ID 映射默认写入本项目 `.artifacts/legacy-community-migration/import.json`。重复邮箱改为 `legacy-旧ID@example.invalid`，邮箱均标记未验证，旧 token 不继承；跳过缺失对象的关联并重新计算计数。不要跳过 Flyway V1 或清空已有表。

NAS 开发环境与管理端共用 `liushao_community` 数据库。社区 Redis 使用 DB 13，管理端使用 DB 12。实际凭据只在被 Git 忽略的本地配置中。社区账号通过注册页面创建，不复用后台管理员。

## 当前功能与接口

响应为 `{code,message,data}`，错误同时使用对应 HTTP 状态。前端开发代理剥离 `/api` 前缀。认证使用 `Authorization: Bearer <token>`。

| 接口 | 行为 |
| --- | --- |
| `POST /ums/user/register` | name/pass/checkPass/email；账号和邮箱唯一，两次密码一致 |
| `POST /ums/user/login` | username/password，返回 JWT |
| `GET /ums/user/info` | 当前用户资料，排除密码和认证版本 |
| `POST /ums/user/logout` | 撤销当前会话 |
| `GET /ums/user/profile/{username}` | 公开资料，不含邮箱、手机号等联系信息 |
| `POST /ums/user/profile` | 修改自己的 alias/bio |
| `POST /ums/user/password` | currentPass/password/checkPass；改密使全部旧令牌失效 |
| `GET /post/list` | tab=latest/hot/essence、pageNo、size、query、tag、author |
| `GET /me/feed` | 当前用户关注的作者的公开帖子，按发布时间排序；支持相同分页与筛选 |
| `GET /me/drafts` | 当前用户的私有草稿列表，按更新时间排序 |
| `GET/PUT/DELETE /me/drafts/{uuid}` | 读取、保存、删除本人草稿；写操作校验草稿 version |
| `POST /me/drafts/{uuid}/publish` | 校验 version 后提交新帖或修改待审核，同一事务移除草稿 |
| `GET /post/{id}` | 详情，浏览计数原子增加 |
| `POST /post/add` | 提交 title/content/tags，新帖进入待审核 |
| `PUT /post/{id}` | 作者编辑，传入详情返回的 version，修改后重新待审核 |
| `DELETE /post/{id}` | 作者删除，事务清理标签关联与计数 |
| `GET /tag/list` | 最多 50 个有帖子的标签，按使用量排序 |
| `GET /billboard/show,/tip/today,/promotion/all` | 首页公告、提示语和资源链接；空数据可正常返回 |

账号为 2–32 位字母、数字或下划线，登录标识统一为小写；密码至少 8 个字符且最多 72 个 UTF-8 字节，使用 BCrypt。邮箱执行格式和唯一性检查，配置 SMTP 后支持邮箱验证和密码找回；未配置时邮件发送接口明确返回 503。

标题最多 160 个字符，正文最多 20000 个字符，最多 5 个标签，每个最多 32 字符。标签统一小写并去重。正文原样保存，包括 Unicode 和 Markdown，前端负责安全渲染。编辑版本冲突返回 409，防止旧编辑页面覆盖新内容；非作者修改/删除返回 403。

分页 `pageNo>=1`、`size=1..50`；query 最长 100 字符，支持标题/正文/标签/作者搜索，SQL 使用绑定参数并转义通配符。热门范围为近七天。分页 JSON 使用固定 DTO，标签通过批量查询装配。

Redis 使用 `liushao:community:` 前缀保存登录会话与原子限流计数。每 IP 每分钟最多 10 次注册、20 次登录。默认 JWT 24 小时；每次受保护请求检查账号状态、认证版本和 Redis 会话。禁用账号、改密或退出会使相关旧登录失效。只允许配置的 CORS 来源；默认支持本机 localhost/127.0.0.1:5173。

## 验证

```bash
./mvnw verify
```

当前 71 项常规测试全部通过，覆盖健康探针、生产配置、社会互动/运营、邮箱与图片上传，以及精华、关注动态和私有草稿。测试使用 Flyway 初始化的 H2 与认证状态替身，不连接 NAS。覆盖登录/JWT/过滤器、联表分页、注册冲突、帖子全流程、越权、标签计数、旧版本冲突、搜索边界、公开资料隐私、改密/退出、限流和 CORS，以及真实事务锁等待下的账号安全状态一致性。运营搜索覆盖提示语正文/署名、推广标题/描述，并验证分页、大小写及特殊字符匹配。草稿场景验证账号隔离、过期版本拒绝、发布与移除的事务、原帖版本冲突、提交后待审和草稿数量限制。

另有 4 项真实 MySQL 时区回归，指定 `MYSQL_TIME_ZONE_TEST_URL`、`MYSQL_TIME_ZONE_TEST_USERNAME`、`MYSQL_TIME_ZONE_TEST_PASSWORD` 后运行 `./mvnw -Dtest=MysqlTimeZoneIntegrationTest test`。URL 指向隔离测试库且不要添加时区参数；测试通过当前连接的临时表，覆盖 UTC/上海 JVM 和新旧连接 URL，验证数据库默认时间、Java 时间及数据库 epoch 一致；不修改业务记录。未提供配置时跳过这一组。

本轮功能验收使用隔离的本地 MySQL、Redis 和 SMTP，配合真实 API 与浏览器检查，不使用 NAS 数据。


## 容器与生产配置

```bash
docker build -t liushao/community-backend:local .
# 在 .env.prod 中填写实际 MySQL/Redis 地址、独立应用数据库账号及强密钥
docker run --rm --name liushao-community --env-file .env.prod \
  -e UPLOAD_DIRECTORY=/app/data/uploads -v liushao-community-uploads:/app/data/uploads \
  -p 127.0.0.1:8080:8080 liushao/community-backend:local
```

镜像以 Maven 3.9.11/JDK 21 编译，使用 Temurin JRE 21 运行，UID/GID 为 `10001:10001`。仅复制指定的可执行 JAR 到 `/app/app.jar`；构建上下文仅包含 `pom.xml` 和 `src`，本地环境配置、Git 历史及 `target` 不进入镜像。日志写 stdout/stderr，由容器运行时负责采集和轮转。JVM 参数使用 `JAVA_TOOL_OPTIONS`。

镜像默认启用 `prod`；直接运行 JAR 时设置 `SPRING_PROFILES_ACTIVE=prod`。生产启动前校验 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`JWT_SECRET` 必须显式配置且非空，错误仅列出缺失变量名。JWT 密钥仍要求至少 64 字节。生产 profile 不读取 `.env.local`；`.env.prod` 由 Docker 的 `--env-file` 加载，支持的格式是 `KEY=value`，不要放入仓库。社区端还必须提供 `CORS_ALLOWED_ORIGINS`，填写实际前端来源（含协议及端口，多个来源用逗号分隔），生产禁用 Swagger UI 和 OpenAPI 文档。

默认 JDBC URL 使用 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`，连接池也启用 `forceConnectionTimeZoneToSession`，确保驱动与 MySQL 当前连接的时区一致。已有 `serverTimezone=Asia/Shanghai` URL 也会同步会话时区，避免数据库自动时间与 Java 写入时间产生 8 小时差异；不要在 URL 中显式关闭该选项。只调整当前连接，不修改 MySQL 全局时区。参见 [Connector/J 时间配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。

`REDIS_PORT` 默认 `6379`，`REDIS_DATABASE` 默认 `0`，按部署分配独立数据库编号；`SERVER_PORT` 默认 `8080`，修改时同步调整宿主端口映射。停止服务时支持 30 秒优雅退出，容器停止宽限期应至少 35 秒。生产启用 Tomcat 原生转发头处理；反向代理需覆盖客户端转发头，后端仅在可信内网监听，通过代理恢复真实 IP 供登录限流使用。

## 健康检查与 CI

[Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html) 只开放健康检查，响应仅含状态及探针分组名称，不暴露配置、连接信息或组件细节：

- `GET /actuator/health/liveness`：进程存活，不依赖数据库或 Redis。
- `GET /actuator/health/readiness`：应用就绪且 MySQL、Redis 可用；依赖故障返回 HTTP 503。
- `GET /actuator/health`：总体健康状态。以上端点无需登录，与业务 API 使用同一端口。

Docker 每 30 秒检查 readiness，启动宽限期 90 秒。普通 `./mvnw verify` 使用 `test` profile，不加载开发 `.env.local`，健康检查测试用 Redis 健康替身验证就绪失败及细节隐藏，不连接 NAS。

独立仓库的 `.github/workflows/ci.yml` 在 push、PR 或手动触发时运行 Java 21 `./mvnw verify` 和镜像构建，不需要数据库服务或仓库密钥，不推送镜像、不部署。


## 社区互动与运营

评论和回复、点赞/取消点赞、收藏/取消收藏、关注/取消关注、站内通知与私信采用 Flyway V2 新表，关系有数据库唯一约束。评论回复归属同一帖子，回复统一关联根评论；删除根评论同时删除回复。帖子删除同时清理评论、点赞、收藏和相关通知，更新标签计数。

`/me/**`、`/notifications/**`、`/messages/**`、个人关系状态与帖子互动状态的 GET 接口也需要有效 JWT。私信只能在当前登录用户参与的会话内读写；客户端传入的身份头不作为授权依据。新接口分页输入 `pageNo>=1`、`size=1..50`，输出沿用 `{content,totalElements,totalPages,number,size}`。评论每用户每分钟最多30次、关系变更60次、私信20次。接口字段见 [社会功能 API](docs/SOCIAL_API.md)。

运营接口位于 `/internal/admin/**`，仅供管理后端调用。`COMMUNITY_ADMIN_KEY` 默认空时关闭内部访问，启用需两个后端配置相同的至少32字符强密钥；使用 `X-Community-Admin-Key` 和 URL 编码的 `X-Community-Admin-Actor` 请求头，不向浏览器发送共享密钥。管理后端负责细分权限校验，社区后端使用常量时间密钥比较并为每次变更保存操作人和审计记录。见 [运营接口](docs/OPERATIONS_API.md)。

运营支持社区用户禁用/恢复，帖子审核/下架/置顶/加精，评论隐藏/恢复/删除，以及公告、每日提示、资源链接与标签维护。帖子状态为 `PUBLISHED`、`PENDING` 或 `HIDDEN`；公开列表、搜索、详情、标签、评论和收藏均过滤不可见帖子及禁用作者。新帖及所有作者修改（包括已发布文章）均进入待审核状态，审核通过后才公开。作者可通过 `/me/posts` 查看全部个人帖子。审核必须提交所查看内容的 `version`，旧版本返回 409 并要求重新查看；审核成功记录所审版本。Flyway V6 仅调整后续新帖的默认状态，已有公开文章保持原状态。账号禁用/恢复会递增认证版本，旧令牌持续失效；评论与互动统计随账号可见性调整。已被帖子引用的标签不能直接删除。

## 邮箱与图片

配置 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USERNAME`、`SMTP_PASSWORD` 和 `SMTP_FROM`；默认开启认证及 STARTTLS。已登录用户通过 `POST /ums/user/email/send` 和 `/ums/user/email/verify` 验证邮箱。公开的 `POST /ums/user/password/forgot`、`/ums/user/password/reset` 用于找回密码。验证码只保存 HMAC 摘要，有效期 10 分钟、发送间隔 60 秒、最多 5 次错误尝试且只能消费一次；主动改密或找回成功都会清除旧验证码并撤销旧会话。账号资料、密码与头像更新与邮件验证、重置、禁用共享用户行锁，防止并发写入覆盖账号安全状态。

`POST /uploads/image` 需要登录，以 multipart 字段 `file` 接收不超过 4 MB 的 PNG/JPEG，解码重编码为 PNG 后返回公开地址；单边不超过 8192 像素、总像素不超过 2000 万，处理后文件不超过 8 MB。`POST /ums/user/avatar` 只能使用当前用户上传的地址。`GET /uploads/{filename}` 公开读取已经登记的图片。

图片目录由 `UPLOAD_DIRECTORY` 指定，容器应持久挂载 `/app/data/uploads`，目录需要 UID/GID `10001:10001` 可写。数据库备份仅包含图片记录，备份恢复还需保存并恢复上传目录；SMTP 不参与 readiness，发送失败由对应接口返回 503。

## 文档与工作区工具

- [当前 NAS 数据库与 Redis](docs/INFRASTRUCTURE.md)
- [历史社区迁移及 ID 映射](docs/legacy-community-migration.md)
- [四项目部署、备份与恢复](deploy/README.md)
- [历史实施记录](docs/archive/WORKSPACE_HISTORY.md)

四个仓库保持同级时，可从共同父目录执行 `./liushao_community_backend/scripts/verify-workspace.sh` 完成两套前端构建与两套后端测试。部署私密配置位于 `deploy/.env`，运行报告和备份由本仓库忽略规则排除。
