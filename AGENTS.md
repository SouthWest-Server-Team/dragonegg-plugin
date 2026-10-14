# AGENTS.md — dragonegg-plugin（DragonEggGuard）

> 本文件是本仓的项目级记忆：**会话工作目录在本仓内时**会被自动读取。
> 结论**绑定到集成分支 `debug` 的快照 `c930f1f6713869a8e1bb15b4b9de7379aeccb2a0`（2026-08-30）**；引用本仓结论一律用该 sha。
> 采集方式：浅克隆 `debug` → 读 `plugin.yml` / `pom.xml` / README / 源码 → **本机实跑 `mvn -B -o test`**。
> **没做**：插件加载冒烟、游戏内命令实测、上实服验证。

## 1. 本仓是什么

**全服唯一龙蛋守卫**（Leaf 1.21.11 轻量插件）：保证全服同时只存在**一个龙蛋**。放置第二个龙蛋会被阻止、重复掉落物被吞、每 5 分钟全服扫描清理重复，位置变化（方块→掉落物→背包→容器→展示框）被实时追踪并持久化。

- 一句话职责：**"龙蛋只有一个，且它现在在哪"**。
- 团队定位：服务器规则/生命周期类小插件（GitHub description：*一个确保全服只有一个龙蛋的轻量插件*）。**不依赖任何第三方插件**。
- `plugin.yml`：name `DragonEggGuard`，main `com.xinantown.dragon.DragonEggGuard`，`api-version: '1.21.11'`，无 `depend`/`softdepend`。
- 命令：`/dragon`（别名 `/deg`）`locate|give|remove|scan|info`（+ 代码里的 `help`）；权限 6 个：`dragonegg.command.locate|info|give|remove|scan`、`dragonegg.admin`（`admin` 是前五者的 children）。
- 规模：11 个主源文件 / 1 个测试文件 / 约 1659 行（Java）。

## 2. 边界（本仓不做什么）

- **不依赖任何第三方插件**：`pom.xml` 只有 `leaf-api`(provided) + junit + adventure + bungeecord-chat。不碰 Towny、不碰经济、不碰别的团队仓。
- **不跨仓引用内部类**：无任何 `com.xinantown.*`（除自身）编译期依赖。
- **不做通用容器/背包管理**：只在龙蛋这一个物品上做"追踪 + 去重"，`EggLocation` 的 5 个实现各自只负责"这一处怎么定位/判等"，不做通用库存框架。
- **不重分发第三方 jar**：仓内无 jar，`.gitignore` 忽略 `*.jar`。

## 3. 分支与提交口径

`git ls-remote --heads origin` 实测：

| 分支 | sha | 说明 |
|---|---|---|
| `debug` | `c930f1f` | **集成分支**——引用结论用它的 sha |
| `dev_GINGSHAN` | `c930f1f` | 个人分支（与 debug 同点） |
| `dev_wanzhang` | （无） | 本仓无此分支 |
| `main` | `9828a33` | **落后于 debug**，等 V2 收口统一合并 |

约定：改动先在 `dev_*` / `chore/*` 上做；`debug` 是集成分支；`main` 不追；不要手改 `debug`/`main`。

## 4. 构建与测试（实测）

环境：JDK 21（Temurin 21.0.12.1）+ Maven 3.9.16。

```bash
export JAVA_HOME="C:/tools/jdk-21.0.12.1+1"
MVN="C:/tools/apache-maven-3.9.16/bin/mvn.cmd"
"$MVN" -B -o test      # 离线；依赖已在本地 .m2
```

**实测结果（2026-10-14，干净 `debug` 克隆，命令：`mvn -B -o test`）**：

```
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（`EggLocationTest` 26 例。产物名 `DragonEggGuard-1.0.0.jar`。）

`pom.xml` 里 `leaf-local-api` 文件仓库指向 `file:///D:/SST/server/XiNanTown/libraries/`，该路径在编写机器上**不存在**，靠 `.m2` 已 install 的 `leaf-api:1.21.11-R0.1-SNAPSHOT` 离线解析。

## 5. 权威路径与知识库

- 仓：https://github.com/SouthWest-Server-Team/dragonegg-plugin
- 本文件结论所属快照（`debug`）：https://github.com/SouthWest-Server-Team/dragonegg-plugin/tree/c930f1f
- 插件声明：https://github.com/SouthWest-Server-Team/dragonegg-plugin/blob/c930f1f/src/main/resources/plugin.yml
- 构建：https://github.com/SouthWest-Server-Team/dragonegg-plugin/blob/c930f1f/pom.xml
- 位置多态（sealed interface）：https://github.com/SouthWest-Server-Team/dragonegg-plugin/blob/c930f1f/src/main/java/com/xinantown/dragon/EggLocation.java
- **KB 状态页（已存在）**：https://github.com/SouthWest-Server-Team/XiNanTown-KB/blob/main/status/dragonegg-plugin.md
- **KB facts 页**：本仓在 `XiNanTown-KB/facts/` 下**暂无**目录。要新增请按 KB `AGENTS.md`/`SCHEMA.md` 写 `facts/dragonegg-plugin/<页>.md` 并更新 `index.md`。

## 6. 红线

- **密钥 / Token / 密码 / SSH 私钥 / 玩家皮肤 `value`+`signature` / 玩家个人数据 / 公网 IP 不入仓**（内网 RFC1918 允许）。
- **玩家数据不落库**：只持久化龙蛋的追踪状态（`data.yml`），不写玩家个人数据。
- **不改生成物**：`target/**` 与 `*.jar` 被 `.gitignore` 忽略；不要手改或提交构建产物。
- **不动 `debug` / `main`**，不修改本文件以外的既有代码（改动走分支）。

## 7. 已知缺口 / 待确认

- **`plugin.yml` usage 未列 `help`**：usage 串是 `locate|give|remove|scan|info`，但 `DragonCommand` 的 `switch` **确有 `help` 分支**（README 也写 `/dragon help`）——usage 串漏了 `help`。
- **README 测试数不符**：README 写"JUnit 5（25 个单元测试）"，实测 **26**。
- **README 架构描述**：`DragonEggListener` 号称"16 个 Bukkit 事件实时追踪"——未逐条核对，属**待确认**（本文件只确认测试与命令层面）。
- 本机化路径：`pom.xml` 的 `leaf-local-api` 文件仓库写死 `D:/SST/...`；换机器需改或先 `install:install-file`。
- README 要求"JDK 21 和 `JAVA_HOME`"（与实测一致）。
