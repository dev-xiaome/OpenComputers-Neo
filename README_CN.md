# OpenComputers Neo

### [English](README.md) | **简体中文**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Minecraft 1.21.1** 的 **NeoForge** 版 [OpenComputers](https://github.com/MightyPirates/OpenComputers) 移植。

> ⚠️ **早期开发**  
> 本项目处于非常早期的阶段。许多功能尚不可用。  
> 我们预计在一周内让模组达到可用状态。  
> 请不要在生产环境中使用。

---

## 📖 关于

OpenComputers 是一个 Minecraft 模组，添加了可编程的计算机、机器人和其他设备。  
本仓库是使用 NeoForge 模组加载器向 Minecraft 1.21.1 的**非官方移植**。

目标是保留原有的玩法和 API，将完整的 OpenComputers 体验带到现代 Minecraft 版本。

---

## 🚧 当前状态

移植工作正在积极进行中。以下是大致概览：

| 功能                 | 状态           |
|----------------------|----------------|
| 项目搭建             | ✅ 完成        |
| 基础方块与物品       | ✅ 完成        |
| 计算机系统           | ✅ 完成        |
| 机器人               | ✅ 完成        |
| 网络                 | ✅ 完成        |
| 模组集成             | ✅ 完成        |

**预计可用版本：一周内。**

**未来可能会加入更多物品。**

---

## 📦 安装

1. 安装 **Minecraft 1.21.1** 对应的 **NeoForge**。
2. 从 [Releases](https://github.com/dev-xiaome/OpenComputers-Neo/releases) 页面下载最新构建  
   *（或从源码构建，见下文）*。
3. 将 `.jar` 文件放入 `mods` 文件夹。
4. 启动游戏。

> **注意：** 本模组需要 NeoForge。它不适用于 Forge 或 Fabric。

---

## 🔨 从源码构建

### 要求
- **JDK 21**
- **Git**

### 步骤

```bash
git clone https://github.com/dev-xiaome/OpenComputers-Neo.git
cd OpenComputers-Neo
./gradlew build
```

编译后的模组位于 `build/libs/`。

开发时，可以运行：

```bash
./gradlew runClient
```

---

## 📜 许可证

本项目采用 **MIT 许可证**。  
详情请参阅 [LICENSE](LICENSE) 文件。

原 **OpenComputers** 模组由 [MightyPirates](https://github.com/MightyPirates) 开发，同样采用 MIT 许可证。  
除非另有说明，其资产属于**公共领域**。

---

## 🙏 致谢

- **原版 OpenComputers**：[MightyPirates](https://github.com/MightyPirates) 及所有贡献者。
- **NeoForge 移植**：[dev-xiaome](https://github.com/dev-xiaome) 及贡献者。

这是非官方移植，**不隶属于**原作者，也未获得其认可。

---

## 🤝 贡献

欢迎贡献！由于项目处于早期开发阶段，请先开 Issue 讨论你想要修改的内容。

1. Fork 本仓库。
2. 创建新分支 (`git checkout -b feature/your-feature`)。
3. 提交更改。
4. 推送到分支。
5. 打开 Pull Request。

---

*祝计算愉快！*
