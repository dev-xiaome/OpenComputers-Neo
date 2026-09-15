package li.cil.oc.integration

/**
 * 模组集成代理的通用描述（对应 1.7.10 的 `li.cil.oc.integration.Mod.java`）。
 *
 * 该接口原本是放在 `src/main/scala` 下的 Java 文件，但本工程只把
 * `src/main/java` 交给 `compileJava`（Scala 包的 `setIncludes` 只匹配 `*.scala`），
 * 因此那几个 `.java` 文件根本不会进入编译，引用它们的 Scala 代码会报
 * "not found: type Mod"。为保证 `compileScala` 能编译 `integration` 包，
 * 这里按原接口语义改写成 Scala trait，并删除对应的 `.java` 文件。
 */
trait Mod {
  /** 模组 id（NeoForge 的 `ModList` 标识）。 */
  def id: String

  /** 该模组当前是否加载。 */
  def isModAvailable: Boolean
}
