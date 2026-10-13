# 流沙社区后端

JDK 21 + Spring Boot 3.5.16 + Spring Data JPA / Hibernate 6 + Springdoc OpenAPI。

## 测试与构建

```bash
./mvnw verify
```

Linux/macOS 包装脚本默认使用 `/opt/java/jdk21`，可用 `JAVA_HOME` 覆盖。第一次执行将下载 Maven 3.9.11 到用户缓存，校验 SHA-512；需要 curl、tar、sha512sum。测试使用 H2，不连接实际 MySQL/Redis/Nacos。所有测试资源只在测试 classpath 中。

## 本地启动

准备 MySQL 并将本项目的 `liushao_community.sql` 导入独立开发库。SQL 含 DROP TABLE 语句，请勿直接导入已有数据的库。

```bash
cp .env.example .env.local
# 编辑 .env.local，填入 DB_PASSWORD 和 JWT_SECRET 等配置
./mvnw spring-boot:run
```

Spring 自动读取当前项目目录的 `.env.local`（Java properties 格式，值无需加引号），环境变量优先。`JWT_SECRET` 必须至少 64 字节，可用 `openssl rand -hex 64` 生成；每个后端使用独立密钥。 默认端口 8080。

默认数据库地址为 localhost，移除了对历史远程服务器的自动连接。不再依赖旧 Nacos、Druid、Log4j 1.x 和 Springfox；使用本地/环境配置、HikariCP、Logback 和 /swagger-ui/index.html。

## 迁移说明

JWT 库升级到 0.12.6，旧硬编码密钥已移除，旧 token 需要重新登录。JPA/Servlet/Validation 使用 Jakarta 命名空间。数据库结构未自动迁移；默认关闭自动 DDL。

已修复 BCrypt 密码校验、受保护接口的身份头覆盖、发帖接口认证和 Hibernate 6 下帖子列表映射。测试覆盖正确/错误密码、禁用用户、令牌过期/签名、鉴权、上下文启动及联表分页。真实 MySQL 和跨应用联调仍需开发数据库。

详见工作区根目录 `MODERNIZATION.md`。
