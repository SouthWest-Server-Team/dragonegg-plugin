# DragonEggGuard — 全服唯一龙蛋守卫

Leaf 1.21.11 轻量插件。保证全服同时只存在**一个龙蛋**。

## 功能

- **单蛋规则**：放置第二个龙蛋被阻止，掉落物重复生成被吞
- **实时追踪**：龙蛋位置变化（放置→掉落→拾取→容器→展示框）自动更新追踪器
- **定期扫描**：每 5 分钟全服扫描，清理重复龙蛋
- **快速验证**：99% 情况下通过已知位置快速验证，跳过全量扫描
- **指令系统**：`/dragon locate/info/give/remove/scan/help`
- **持久化**：`data.yml` 保存追踪状态，重启恢复

## 指令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/dragon locate` | 所有人 | 定位龙蛋当前位置 |
| `/dragon info` | 所有人 | 查看龙蛋详细信息 |
| `/dragon give <玩家>` | OP | 发放官蛋（覆盖旧的） |
| `/dragon remove` | OP | 删除当前官蛋 |
| `/dragon scan` | OP | 手动全服扫描 |
| `/dragon help` | 所有人 | 查看帮助 |

## 架构

```
DragonEggGuard (主插件)
  ├── DragonEggTracker     — 状态持有者 + YAML 持久化
  ├── EggScanner           — 定期全服扫描 + 去重
  ├── DragonEggListener    — 16 个 Bukkit 事件实时追踪
  └── DragonCommand        — 6 个子命令

EggLocation (sealed interface) ← 多态化 (ADR-0001)
  ├── BlockLocation         — 方块
  ├── ItemEntityLocation    — 掉落物
  ├── PlayerInventoryLocation — 玩家背包
  ├── ContainerLocation     — 容器
  └── ItemFrameLocation     — 物品展示框
```

## 构建

```bash
# 需要 JDK 21 和 JAVA_HOME
mvnw clean package
# 输出: target/DragonEggGuard-1.0.0.jar
```

## 技术栈

- Java 21
- Leaf API 1.21.11
- JUnit 5（25 个单元测试）
