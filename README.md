# OpenComputers Neo

### **English** | [简体中文](README_CN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A **NeoForge** port of [OpenComputers](https://github.com/MightyPirates/OpenComputers) for **Minecraft 1.21.1**.

> ⚠️ **Early Development**  
> This project is in a very early stage. Many features are not yet available.  
> We expect to make the mod usable within **one week**.  
> Please do not use it in production worlds yet.

---

## 📖 About

OpenComputers is a Minecraft mod that adds programmable computers, robots, and other devices.  
This repository is an **unofficial port** to Minecraft 1.21.1 using the NeoForge mod loader.

The goal is to bring the full OpenComputers experience to modern Minecraft versions while keeping the original gameplay and API intact.

---

## 🚧 Current Status

The port is actively being worked on. Here is a rough overview:

| Feature              | Status         |
|----------------------|----------------|
| Project setup        | ✅ Done        |
| Basic blocks & items | ✅ Done        |
| Computer system      | ✅ Done        |
| Robots               | ✅ Done        |
| Networking           | ✅ Done        |
| Mod integrations     | ✅ Done        |

**Expected usable version: within 1 week.**

**More items may be added in the future.**

---

## 📦 Installation

1. Install **NeoForge** for **Minecraft 1.21.1**.
2. Download the latest build from the [Releases](https://github.com/dev-xiaome/OpenComputers-Neo/releases) page  
   *(or build from source, see below)*.
3. Put the `.jar` file into your `mods` folder.
4. Launch the game.

> **Note:** This mod requires NeoForge. It will not work with Forge or Fabric.

---

## 🔨 Building from Source

### Requirements
- **JDK 21**
- **Git**

### Steps

```bash
git clone https://github.com/dev-xiaome/OpenComputers-Neo.git
cd OpenComputers-Neo
./gradlew build
```

The compiled mod will be in `build/libs/`.

For development, you can run:

```bash
./gradlew runClient
```

---

## 📜 License

This project is licensed under the **MIT License**.  
See the [LICENSE](LICENSE) file for details.

The original **OpenComputers** mod by [MightyPirates](https://github.com/MightyPirates) is also licensed under the MIT License.  
Its assets are in the **public domain** unless otherwise noted.

---

## 🙏 Credits

- **Original OpenComputers**: [MightyPirates](https://github.com/MightyPirates) and all contributors.
- **NeoForge Port**: [dev-xiaome](https://github.com/dev-xiaome) and contributors.

This is an unofficial port and is **not affiliated with or endorsed by** the original authors.

---

## 🤝 Contributing

Contributions are welcome! Since the project is in early development, please open an issue first to discuss what you would like to change.

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/your-feature`).
3. Commit your changes.
4. Push to the branch.
5. Open a Pull Request.

---

*Happy computing!*
