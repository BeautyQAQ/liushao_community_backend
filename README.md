# 流沙社区后端

JDK 21 + Spring Boot 3.5.16 + Spring Data JPA/Hibernate 6 + Flyway + MySQL + Redis。

## 启动

准备独立的空 MySQL 8 数据库和 Redis，然后在本项目目录执行：

```bash
cp .env.example .env.local
# 填入 MySQL/Redis 连接配置及 JWT_SECRET
./mvnw spring-boot:run
```

默认端口 8080。包装脚本使用 `/opt/java/jdk21`，支持 `JAVA_HOME` 覆盖；首次下载并校验 Maven 3.9.11。Spring 读取 `.env.local`（Java properties，值不用引号），环境变量优先。JWT 密钥至少 64 字节，可以用 `openssl rand -hex 64` 生成。

Flyway V1 自动创建 7 张业务表和版本历史表，初始化欢迎公告和提示语。Hibernate 使用 `validate` 检查实体与表结构。新库不要导入旧 `liushao_community.sql`：历史 SQL 使用 varchar 雪花 ID，与当前整数自增模型不兼容；已有数据需另行迁移，不能直接 baseline 或清空。

NAS 开发环境使用 `liushao_community` 数据库、Redis DB 13，与管理端隔离。实际凭据只在被 Git 忽略的本地配置中。社区账号通过注册页面创建，不复用后台管理员。

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
| `GET /post/list` | tab=latest/hot、pageNo、size、query、tag、author |
| `GET /post/{id}` | 详情，浏览计数原子增加 |
| `POST /post/add` | 发布 title/content/tags |
| `PUT /post/{id}` | 作者编辑，额外传入详情返回的 version |
| `DELETE /post/{id}` | 作者删除，事务清理标签关联与计数 |
| `GET /tag/list` | 最多 50 个有帖子的标签，按使用量排序 |
| `GET /billboard/show,/tip/today,/promotion/all` | 首页公告、提示语和资源链接；空数据可正常返回 |

账号为 2–32 位字母、数字或下划线，登录标识统一为小写；密码至少 8 个字符且最多 72 个 UTF-8 字节，使用 BCrypt。邮箱是注册联系信息，执行格式和唯一性检查；当前未配置 SMTP，不发送验证或找回邮件。

标题最多 160 个字符，正文最多 20000 个字符，最多 5 个标签，每个最多 32 字符。标签统一小写并去重。正文原样保存，包括 Unicode 和 Markdown，前端负责安全渲染。编辑版本冲突返回 409，防止旧编辑页面覆盖新内容；非作者修改/删除返回 403。

分页 `pageNo>=1`、`size=1..50`；query 最长 100 字符，支持标题/正文/标签/作者搜索，SQL 使用绑定参数并转义通配符。热门范围为近七天。分页 JSON 使用固定 DTO，标签通过批量查询装配。

Redis 使用 `liushao:community:` 前缀保存登录会话与原子限流计数。每 IP 每分钟最多 10 次注册、20 次登录。默认 JWT 24 小时；每次受保护请求检查账号状态、认证版本和 Redis 会话。禁用账号、改密或退出会使相关旧登录失效。只允许配置的 CORS 来源；默认支持本机 localhost/127.0.0.1:5173。

## 验证

```bash
./mvnw verify
```

当前 21 项测试全部通过。测试使用 Flyway 初始化的 H2 与认证状态替身，不连接 NAS。覆盖登录/JWT/过滤器、联表分页、注册冲突、帖子全流程、越权、标签计数、旧版本冲突、搜索边界、公开资料隐私、改密/退出、限流和 CORS。

真实浏览器联调使用 NAS MySQL 和 Redis，覆盖注册 → 登录 → 发帖 → 详情 → 编辑 → 搜索/标签/个人主页 → 删除，以及跨账号越权拦截。临时测试记录完成后清理。
