# PhysMC

PhysMC 是一个基于 Paper API 的 Minecraft 物理插件，用 `BlockDisplay` 等显示实体在游戏内模拟刚体、碰撞、约束、弹簧、绳索、浮力和实体交互。

项目当前面向 Paper `1.21.4` 开发，构建产物为可直接放入服务器 `plugins` 目录的插件 Jar。

## 功能概览

- 基于 Bullet/JBullet 的刚体物理模拟。
- 支持方块刚体、轻杆、轻绳、弹簧和调试线段。
- 支持点约束、距离约束、弹簧约束、铰链、滑轨、固定、6DoF、锥形约束。
- 支持选区构建，将世界中的方块转换为复合物理刚体。
- 支持水和岩浆浮力、流体阻力、材质摩擦和反弹系数。
- 支持玩家和实体碰撞、攻击冲量、运行时物理参数调整。
- 支持物理对象持久化，重启后恢复保存的刚体组。
- 提供调试可视化和物理量读写命令。

## 环境要求

- Java 16 或更高版本。
- Maven 3.8 或更高版本。
- Paper 服务端 `1.21.4` 或兼容版本。
- 可选依赖：ProtocolLib `5.4.0`。

> `pom.xml` 中 `java.version` 为 `1.8`，但 Maven 编译插件实际配置为 `source/target 16`，请以 Java 16 编译环境为准。

## 构建

在项目根目录执行：

```bash
mvn clean package
```

构建完成后，插件位于：

```text
target/PhysMC-5.0.0.jar
```

## 安装

1. 将构建出的 `target/PhysMC-5.0.0.jar` 放入 Paper 服务端的 `plugins` 目录。
2. 启动或重启服务器。
3. 首次启动后会生成默认配置文件：

```text
plugins/PhysMC/config.yml
```

插件启动时还会加载已保存的持久化物理组：

```text
plugins/PhysMC/persistent-groups.yml
```

## 命令

主命令：

```text
/physmc
```

基础命令：

| 命令 | 说明 |
| --- | --- |
| `/physmc help` | 查看插件命令帮助 |
| `/physmc get <键名>` | 查看配置项说明、当前值和默认值 |
| `/physmc set <键名> <值>` | 运行时修改配置并保存 |
| `/physmc query [组id]` | 查看物理系统摘要或指定组信息 |
| `/physmc pause` | 暂停物理模拟 |
| `/physmc resume` | 继续物理模拟 |
| `/physmc step <tick数>` | 在暂停状态下步进指定 tick |
| `/physmc fast <秒数>` | 快速模拟指定秒数 |
| `/physmc remove <组id> [名称]` | 删除物理组或组内对象 |

创建物理对象：

```text
/physmc spawn block <组id> <名称> <大小X> <大小Y> <大小Z> <质量> [vx vy vz] [wx wy wz]
/physmc spawn line <组id> <名称> <x1 y1 z1> <x2 y2 z2>
/physmc spawn rod <组id> <名称> <长度>
/physmc spawn rope <组id> <名称> <长度> [段数]
/physmc spawn spring <组id> <名称> <长度> [刚度] [阻尼] [圈数] [端点质量] [端点大小] [半径]
```

示例：

```text
/physmc spawn block test box 1 1 1 5
/physmc spawn rope test rope 8 12
/physmc spawn spring test spring 5 25 2 6
```

约束命令：

```text
/physmc constraint create <point|distance|spring|hinge|slider|fixed|6dof|cone> [...]
```

创建约束前，使用木棍左键选择两个约束点。两个点至少有一个需要落在物理对象上。

选区命令：

```text
/physmc selection tool <on|off>
/physmc selection clear
/physmc selection build <组id>
```

开启选区工具后，使用下界之星设置选区，再通过 `selection build` 将选区内方块转换为复合刚体。选区构建会删除原方块并生成 Display 实体，请先在测试环境确认效果。

持久化命令：

```text
/physmc persist save <组id>
/physmc persist remove <组id>
```

`save` 会将指定组写入 `persistent-groups.yml`，服务器重启后自动恢复；`remove` 仅取消持久化记录，不会删除当前场景中的实体。

调试和物理量：

```text
/physmc debug <组id|*|^> <名称|*> <show|hide> <类型|*>
/physmc prop <组id> <名称> <get|set|add> <物理量> [坐标...] [值...]
```

常用物理量包括：

```text
mass, invmass, position, velocity, rotation, force, torque, acceleration, orientation, inertia, size
```

## 配置

配置文件使用中文键名，主要分组如下：

| 分组 | 说明 |
| --- | --- |
| `物理` | 重力、步长、摩擦、反弹、阻尼、休眠阈值、碰撞边距 |
| `性能` | 空间分区、模拟半径、降频模拟、选区构建速率 |
| `显示` | Display Entity 插值延迟、插值时长和传送时长 |
| `材质` | 不同方块材质的摩擦、反弹等覆盖参数 |
| `流体` | 水/岩浆浮力、密度、阻力和流速 |
| `实体` | 实体碰撞、扫描半径、实体质量和同步系数 |
| `游戏作用` | 玩家攻击物理刚体时施加的冲量 |

可以使用命令查看单个配置项：

```text
/physmc get 物理.重力_Y
```

也可以运行时修改：

```text
/physmc set 物理.重力_Y -15
/physmc set 流体.启用浮力 true
/physmc set 材质.摩擦系数_ICE 0.03
```

实体和材质支持按类型覆盖：

```text
/physmc set 实体.默认质量_类型_MINECART 40
/physmc set 材质.反弹系数_SLIME_BLOCK 0.8
```

## API

PhysMC 对外 API 位于：

```text
com.bxwbb.physmc.api
com.bxwbb.physmc.api.event
```

外部插件只应依赖这些公开包，不应直接依赖 `com.bxwbb.phys`、`com.bxwbb.phy`、`com.bxwbb.obj`、`com.bxwbb.util` 等内部实现包。

### Maven 坐标

PhysMC API 发布到 Maven Central 时使用以下坐标：

```xml
<groupId>io.github.bxwbb</groupId>
<artifactId>physmc-api</artifactId>
<version>1.0.0</version>
```

外部插件依赖示例：

```xml
<dependency>
    <groupId>io.github.bxwbb</groupId>
    <artifactId>physmc-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### API 边界

当前公开 API 包含：

- `PhysMCApi`：获取 API 入口。
- `PhysWorld`：查询、创建、删除刚体，创建约束，暂停/恢复/步进模拟。
- `PhysBody`：读取和修改单个物理刚体的稳定状态。
- `PhysBlockRequest`：创建方块刚体的参数对象。
- `PointConstraintRequest`、`DistanceConstraintRequest`、`TypedConstraintRequest`：约束创建参数。
- `PhysContactEvent` 及其子类：刚体与方块、实体、流体、其他刚体接触时触发的 Bukkit 事件。

当前不作为公开 API 承诺的内容：

- `BulletPhysicsEngine` 等 Bullet 适配实现。
- `PhysCommand` 命令处理。
- `ObjectUtil` 对象注册表。
- `World`、`RigidBody`、`Box` 等可变内部数据结构。
- `com.bxwbb.physmc.api.internal` 内部适配包。

### 使用示例

查询刚体：

```java
import com.bxwbb.physmc.api.PhysBody;
import com.bxwbb.physmc.api.PhysMCApi;

PhysMCApi.world()
        .findBody("test", "box")
        .ifPresent(PhysBody::sync);
```

创建方块刚体：

```java
import com.bxwbb.physmc.api.PhysBlockRequest;
import com.bxwbb.physmc.api.PhysBody;
import com.bxwbb.physmc.api.PhysMCApi;
import org.bukkit.Material;
import org.joml.Vector3d;

PhysBody body = PhysMCApi.world().spawnBlock(
        PhysBlockRequest.builder("demo", "box", location)
                .material(Material.STONE)
                .size(new Vector3d(1.0d, 1.0d, 1.0d))
                .mass(5.0d)
                .build()
);
```

监听接触事件：

```java
import com.bxwbb.physmc.api.event.PhysBlockContactEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ContactListener implements Listener {
    @EventHandler
    public void onContact(PhysBlockContactEvent event) {
        event.getPhysBody().setAwake(true);
    }
}
```

## 项目结构

```text
src/main/java/com/bxwbb
├── PhysMC.java                 # 插件入口
├── PhysCommand.java            # /physmc 命令和补全
├── PhysConfig.java             # 配置键、默认值和说明
├── phys/                       # Bullet 物理引擎适配
├── phy/                        # 刚体、世界和数学辅助
├── cd/                         # 碰撞检测与接触解析
├── obj/                        # 物理显示对象
├── force/                      # 力生成器
├── constraint/                 # 约束选择状态
├── event/                      # 玩家交互监听
├── persistence/                # 持久化组读写
├── physmc/api/                 # 对外稳定 API
└── util/                       # 工具类和调试显示

src/main/resources
├── plugin.yml                  # Bukkit/Paper 插件描述
├── config.yml                  # 默认配置
└── logo.txt                    # 启动 Logo
```

## 开发说明

- 不要直接提交 `target/` 下的构建产物。
- 命令和配置当前以中文为主，新增注释和用户可见文本应保持同一语言风格。
- 物理参数改动建议优先通过 `PhysConfig` 增加默认值和说明，再在 `config.yml` 中补充对应配置。
- 大规模实体或选区构建相关改动需要重点验证 TPS、Display 数量和未加载区块行为。

## 许可证

本项目使用 MIT 许可证，详见 [LICENSE](LICENSE)。
