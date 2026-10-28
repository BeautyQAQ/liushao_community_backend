# 容器部署与维护

本方案将四个应用与独立 MySQL、Redis 放入同一个 Compose 项目。需要 Docker Engine、Compose v2 和 Python 3；流式备份与恢复另需宿主机 `gzip`。默认仅在本机开放社区 `8088` 和管理端 `8089`；数据库和后端不映射宿主机端口。镜像内的前端通过同源 `/api` 访问各自后端。

## 首次启动

四个仓库保持同级。在 `liushao_community_backend/deploy/` 目录执行以下命令：

```bash
python3 scripts/init-deploy.py
./scripts/compose.sh config --quiet
./scripts/compose.sh up -d --build --wait --wait-timeout 600
python3 scripts/smoke-deploy.py
```

初始化脚本生成权限为 `0600` 的 `.env`，包含独立数据库密码、Redis 密码、两套 JWT 密钥和管理登录密码。已有文件不会被覆盖。管理用户名默认为 `admin`，密码在该文件的 `ADMIN_PASSWORD` 字段中；社区用户需要另行注册。首次成功启动后，将 `BOOTSTRAP_ADMIN` 改为 `false`，再执行 `./scripts/compose.sh up -d --wait`。改变环境中的管理员密码不会重置已有账号。

- 社区：<http://localhost:8088>
- 管理：<http://localhost:8089>
- 修改端口时同步修改 `COMMUNITY_ORIGIN`，例如 `http://localhost:9088`。
- 自定义配置文件：`DEPLOY_ENV_FILE=/absolute/path/environment ./scripts/compose.sh …`。该变量同样适用于备份脚本。
- `compose.sh config` 会展开秘密；检查配置请使用 `config --quiet`，不要将展开后的配置贴入日志或工单。

MySQL 初始化只在空数据卷首次执行，创建共享数据库 `liushao_community`，为 `admin_app`、`community_app` 分别创建账号并授予该库权限。Flyway 分别使用 `flyway_admin_schema_history`、`flyway_schema_history` 记录管理端和社区端迁移；首次启动从版本 0 建立缺少的历史，确保任一端先启动时另一端仍执行 V1。修改 `.env` 不会自动修改数据卷中的数据库账号密码或权限。Redis 使用 DB 12/13，与 NAS 上同编号逻辑库处于不同实例。

已有双库环境需先备份、停止写入，按 [数据库合并步骤](../../authority_management_backend/docs/database-consolidation.md) 迁移表和管理端历史，并为 `admin_app` 授予 `liushao_community.*` 权限，再更新应用。直接修改连接串不足以完成迁移。

社区运营需要两后端共享独立的 `COMMUNITY_ADMIN_KEY`；初始化脚本已自动生成，已有环境文件需补充至少 32 字符的随机密钥。图片存入 `community-uploads` 持久卷。邮箱验证和密码找回需配置 `SMTP_*`，具体字段及功能入口见 [FEATURES.md](../../liushao_community_frontend/docs/FEATURES.md)。

数据库连接默认使用 UTC，并强制数据库会话与驱动使用同一时区，避免审计、通知和私信时间偏移。历史环境若使用 `serverTimezone=Asia/Shanghai` 却未设置数据库会话时区，应先核查旧记录的实际时间；新配置不会自动修正过去写入的时间值，不应在未核实来源时整体平移历史数据。

此配置用于新环境，不读取后端已有 `.env.local`，也不会导入历史 SQL。已有数据库不能直接套用首次初始化流程。部署编排、脚本和本说明随社区后端仓库版本管理。分发时检出四个仓库的固定版本并保持同级；本地 `.env`、备份和运行产物不进入 Git。

前端默认从 npm 官方仓库安装。需要使用受信任的镜像源时：

```bash
./scripts/compose.sh build --build-arg NPM_REGISTRY=https://registry.npmmirror.com admin-web community-web
./scripts/compose.sh up -d --no-build --wait --wait-timeout 600
```

锁文件的版本与 SHA-512 校验仍然生效，参数只影响构建阶段。启动前需已构建两套后端镜像；首次部署可先执行 `./scripts/compose.sh build admin-api community-api`，或使用下面的 Maven 配置方式。

后端默认使用 Maven Central。需要企业镜像配置时，通过可选的 Compose 文件将设置作为构建秘密传入两套后端：

```bash
MAVEN_SETTINGS_FILE=/absolute/path/settings.xml \
  ./scripts/compose.sh -f compose.maven.yaml build admin-api community-api
./scripts/compose.sh up -d --no-build --wait --wait-timeout 600
```

设置文件仅在构建命令中挂载，不进入镜像或 Maven 缓存；生成的镜像直接使用当前 Compose 项目的名称。启动前需已构建两套前端镜像，可执行 `./scripts/compose.sh build admin-web community-web`，或使用前面的 npm 镜像源命令。选择任一自定义构建配置后，启动使用 `up --no-build`；升级时先用相同配置重新构建，再启动，避免 `up --build` 用默认来源重新构建。

## 健康、日志与入口

```bash
./scripts/compose.sh ps
./scripts/compose.sh logs --tail=100 admin-api community-api
./scripts/compose.sh exec -T admin-api curl -fsS http://localhost:8081/actuator/health/readiness
./scripts/compose.sh exec -T community-api curl -fsS http://localhost:8080/actuator/health/readiness
```

后端 readiness 检查数据库和 Redis，liveness 检查应用存活；端点不公开连接明细。前端 `/healthz` 检查静态服务，`smoke-deploy.py` 进一步验证页面深链接、资源、API 代理和未登录访问限制。Docker 为每个服务保留最多 3 个 10MB 日志文件。健康状态是检测信号，Compose 不会因单纯 unhealthy 自动重启容器；崩溃由重启策略处理。

Nginx 覆盖客户端转发头，后端根据可信内网代理恢复来源地址。部署到公网时，在入口配置 HTTPS、域名及管理端访问控制；默认本机绑定可供宿主机反向代理使用。增加外层代理时，需要按该代理的实际地址配置 Nginx `set_real_ip_from` 和 `real_ip_header`，再验证来源 IP 与限流；不要将任意来源配置为可信代理。当前 Compose 的数据库连接采用容器网络内明文连接，连接外部数据库时需另行配置 TLS。

## 备份与恢复演练

### 创建数据库与上传图片的联合备份

使用本机 Docker Unix socket 和本项目的独立 MySQL、Redis、上传命名卷。在维护窗口执行：

```bash
./scripts/backup.sh --stop-apps
# 指定仅当前用户可访问、容量充足的目录：
DEPLOY_ENV_FILE=/absolute/path/environment \
  ./scripts/backup.sh /absolute/path/private-backups --stop-apps
```

`--stop-apps` 会停止两套前端和 API，等待在途写入结束，同时导出共享业务库及上传卷，再启动原先正在运行的应用并等待健康。维护期间网站不可访问；若四个应用原本均已停止，可省略此参数。失败或可处理的取消同样恢复原运行状态；恢复服务失败会返回非零。机器断电、Docker 不可用或强制 SIGKILL 时需要人工检查运行状态。

备份期间不要运行数据库迁移、DDL、外部写入或手动改动上传卷；脚本拒绝有启用定时事件且调度器运行的 MySQL。同一工作区内，同一项目的备份和恢复使用互斥锁。数据库使用单事务导出，包含 `liushao_community` 中两端的业务表、两张 Flyway 历史表、触发器、存储过程及事件。Redis 会话、部署凭据和邮件收件内容不进入备份。

成功后输出 `backups/liushao-<时间>-<随机值>/`，目录 `0700`、文件 `0600`，四个文件一并完成校验后才原子发布：

| 文件 | 内容 |
| --- | --- |
| `database.sql.gz` | 共享业务库的完整 SQL 导出 |
| `uploads.tar.gz` | 对应备份点的上传图片 |
| `manifest.json` | 格式、时间、源项目、应用提交、六个镜像 ID、逐图大小/摘要及数据库上传引用 |
| `SHA256SUMS` | 以上三个文件的 SHA-256 |

校验会完整读取 gzip，拒绝缺图、大小不符、摘要错误及不安全的归档条目。合法的孤儿图片会随上传卷保留。只有四个文件齐全的完整目录可用于恢复；旧版独立 `*.sql.gz` 备份不包含图片，不适用于此联合恢复入口。

合库后的联合备份使用 `schemaVersion: 2`。旧双库联合备份（版本 1）仍可执行 `verify`，恢复时需使用合库前的匹配部署，完成合库后重新生成版本 2 备份；当前恢复入口会在创建资源前拒绝版本 1。

```bash
python3 scripts/backup.py verify /absolute/path/private-backups/liushao-RUN_ID
```

`verify` 不需要 Docker。备份可能包含私信、个人资料、密码哈希等业务数据，应复制完整目录到受控异机存储并设置保留周期；脚本不自动删除历史备份。SHA-256 用于检测损坏，恢复时应使用自己保管的可信备份。

### 恢复到全新项目

恢复使用 `manifest.json` 中记录的六个镜像 ID，确保应用与备份结构匹配。同机保留源镜像即可；异机需要先用 `docker image save` / `docker image load` 转移这些镜像。数据备份包不包含镜像，恢复不会自动拉取其他版本或构建当前代码。

```bash
python3 scripts/backup.py restore /absolute/path/private-backups/liushao-RUN_ID \
  --output-env /absolute/path/private-restore/restored.env \
  --project liushao-restore-check --community-port 9088 --admin-port 9089
```

恢复先校验整个备份，再拒绝与源项目同名、已有容器/网络/卷或已有输出配置的目标；不会覆盖运行中的项目。随后生成全新基础设施凭据、新 JWT 密钥及空 Redis，关闭首次管理员初始化，先导入图片和共享数据库并复核图片摘要及上传引用，最后启动应用并执行部署冒烟。图片权限恢复为 `10001:10001`，正式 SMTP 不自动启用。恢复项目的 MySQL 事件调度器固定关闭，避免导入的定时任务在恢复期间执行；有定时任务的环境需在切换前单独评估何时启用。

恢复后管理登录密码来自备份，生成文件中的 `ADMIN_PASSWORD` 不会覆盖已有账号。所有用户需要重新登录；不会恢复旧会话或对共享 Redis 清库。核验用户、帖子、权限、图片和 Flyway 版本后再安排切换入口。

恢复同时生成私密镜像覆盖文件 `restored.env.images.yaml`，后续查看、启停该项目需带上它：

```bash
DEPLOY_ENV_FILE=/absolute/path/private-restore/restored.env \
  ./scripts/compose.sh -f /absolute/path/private-restore/restored.env.images.yaml ps
```

恢复失败会停止新项目的应用，保留新项目与配置以便检查；先检查生成的环境及镜像覆盖文件，再定向处理该项目。不要复用有残留资源的项目名。数据恢复不自动切换任何现有入口。

### 自动隔离演练

```bash
python3 -m unittest discover -s scripts -p test_backup_bundle.py -v
python3 scripts/test-backup-restore.py --delete-backups
```

演练从当前四个工程构建生产镜像，在两个随机本机项目中实际完成上传、头像、中文/emoji 帖子、评论和私信造数、联合备份及恢复；比较图片 SHA-256、共享库的两张 Flyway 历史和业务记录，验证旧会话失效、原管理员密码保留、UID 10001 可继续上传、两个应用账号可访问共享库且无系统库权限、启用状态的定时事件保持不执行，以及损坏包、已有目标和数据库缺图的失败处理。

默认无论成功或失败都清理这两个项目的容器和卷、生成镜像及凭据。`--delete-backups` 同时删除本次测试备份；省略时备份保持私密，便于诊断。运行日志和摘要位于 `.artifacts/backup-restore/`，不要将整个产物目录公开发布。`--keep-environment` 仅用于本机调试，并保留本轮私密环境以供定向清理。可选 `--npm-registry` / `--maven-settings` 只用于镜像构建。

2026-10-19 验收：24 项校验测试、9 组实际检查全部通过，约 709 秒；测试环境和备份已清理。版本、图片摘要、逐调用源码哈希和清理核验见[脱敏记录](docs/backup-restore/latest.json)。本轮在同一主机上恢复，实际跨机传输、异机备份调度及保留周期尚未执行。

## 升级与回退

1. 记录四个仓库的提交和当前镜像 ID，执行完整验证及数据库/上传图片联合备份；先在独立恢复环境验证新版本。
2. 已执行的 Flyway `V1` 文件保持不变。新增结构使用 `V2`、`V3`，应用启动执行增量迁移。新共享库仅允许从版本 0 建立历史并完整执行 V1；已有表必须保留真实历史，不能跳过已有迁移，也不能执行 clean。
3. 关闭首次管理员初始化，执行 `./scripts/compose.sh up -d --build --wait --wait-timeout 600`，随后运行冒烟检查。
4. 若新版本迁移了数据库，不要假定旧镜像可以直接回退。使用升级前备份在独立项目恢复并验证旧版本，再切换入口；恢复点之后的新写入需要单独处理。

`./scripts/compose.sh stop` 停止服务，`down` 删除本项目容器和网络但保留命名数据卷。`down --volumes` 会删除本项目数据库，只用于明确可丢弃的测试项目。

四个仓库分别带有 GitHub Actions：前端执行锁文件安装、类型检查、生产构建与镜像构建；后端执行 JDK 21 测试/打包和镜像构建。CI 使用 H2 与内存认证替身，不依赖开发数据库，不自动发布镜像或部署。现有构建标签固定主要版本；正式发布时应记录并固定实测镜像 digest。

参考：[Compose 健康依赖与启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)、[MySQL 官方镜像初始化与已有卷行为](https://hub.docker.com/_/mysql)。
