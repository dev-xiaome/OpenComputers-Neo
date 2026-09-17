package li.cil.oc.integration

/**
 * 集成代理（对应 1.7.10 的 `li.cil.oc.integration.ModProxy.java`，改写理由见 [[Mod]]）。
 *
 * 每个被集成的模组提供一个 `object Xxx extends ModProxy`，
 * [[Mods]] 在初始化时遍历 [[Mods.Proxies]] 并调用 [[initialize]]。
 */
trait ModProxy {
  /** 本代理对应的模组描述（`Mods.Minecraft` 表示与具体模组无关的通用集成）。 */
  def getMod: Mod

  /** 执行驱动注册等初始化动作。 */
  def initialize(): Unit
}
