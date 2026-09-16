# Setting Up a Development Environment

OpenComputers: Rebooted for Minecraft 1.21.1 uses **Java 21**, **NeoForge**, **Gradle**, Java, and Scala.

You do **not** need to install Gradle separately. The repository includes the Gradle wrapper.

## 1. Install Java 21

Install a **Java 21 JDK**.

Good options include:

- Eclipse Temurin 21
- Microsoft OpenJDK 21
- Oracle JDK 21

Make sure you're installing a **JDK**, not just a JRE.

You can check it from a command prompt:

```text
java -version
```

You should see Java 21 somewhere in the output.

## 2. Install IntelliJ IDEA

The easiest IDE for working on OpenComputers: Rebooted is **IntelliJ IDEA**.

The free **Community Edition** is fine.

Once installed, also install the **Scala** plugin:

1. Open IntelliJ.
2. Go to **Settings → Plugins**.
3. Search for `Scala`.
4. Install the official JetBrains Scala plugin.
5. Restart IntelliJ if prompted.

OpenComputers: Rebooted contains both Java and Scala code, so this plugin is important.

## 3. Get the Source Code

Either clone the repository with Git:

```bash
git clone https://github.com/CaitlynMainer/OpenComputers.git
```

Or download the repository as a ZIP from GitHub and extract it somewhere.

If you plan to make changes or submit pull requests, cloning with Git is strongly recommended.

## 4. Open the Project in IntelliJ

In IntelliJ:

1. Choose **Open**.
2. Select the OpenComputers: Rebooted repository folder.
3. Open the **folder itself**, not an individual file.
4. IntelliJ should detect `build.gradle` and import the project as a **Gradle project**.

Gradle will then download Minecraft, NeoForge, mappings, Scala, and the other development dependencies.

If IntelliJ asks which JDK Gradle should use, select **Java 21**.

You can verify this under:

```text
Settings
→ Build, Execution, Deployment
→ Build Tools
→ Gradle
→ Gradle JVM
```

Set it to a Java 21 JDK.

## 5. Make Sure the Project Builds

On Windows, open a terminal in the repository and run:

```powershell
.\gradlew.bat build
```

On Linux/macOS:

```bash
./gradlew build
```

If everything is set up correctly, the build should eventually finish with:

```text
BUILD SUCCESSFUL
```

Built OpenComputers: Rebooted JARs will appear under:

```text
build/libs/
```

## 6. Launch Minecraft

You can launch the development client through IntelliJ using the generated Minecraft client run configuration.

You can also launch it directly through Gradle.

### Windows

```powershell
.\gradlew.bat runClient
```

### Linux/macOS

```bash
./gradlew runClient
```

Do not manually launch NeoForge's internal dev launcher class. Let Gradle prepare and start the development environment.

## 7. If the Client Run Files Are Missing

If IntelliJ complains about a missing file such as:

```text
build/moddev/clientRunVmArgs.txt
```

run:

```powershell
.\gradlew.bat prepareClientRun
```

Then try launching the client again.

Normally `runClient` handles this automatically.

## Where Things Are

The project is fairly large, but these are the main directories you'll probably care about.

### Java code

```text
src/main/java/
```

### Scala code

```text
src/main/scala/
```

Much of the original OpenComputers implementation is written in Scala.

### Resources

```text
src/main/resources/
```

This contains things such as:

- Models
- Textures
- Recipes
- Tags
- Language files
- Loot tables
- OpenOS files
- In-game documentation

### OpenComputers: Rebooted API

```text
src/main/java/li/cil/oc/api/
```

This contains much of the public API used by addons and integrations.

## Basic Development Loop

For simple changes:

1. Change something in IntelliJ.
2. Launch the Minecraft development client.
3. Test the change in-game.
4. Stop Minecraft.
5. Make another change.
6. Repeat.

Before committing a substantial change, run:

```powershell
.\gradlew.bat build
```

This catches many Java, Scala, resource, and packaging problems.

## Don't Panic

OpenComputers is an old and fairly large codebase that mixes Java and Scala.

You do **not** need to understand the whole project before contributing.

Find the part you want to change, look at nearby code for examples, make a small change, build it, and test it in the development client.
