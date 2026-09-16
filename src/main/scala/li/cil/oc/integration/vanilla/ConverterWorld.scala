package li.cil.oc.integration.vanilla

import li.cil.oc.api
import net.minecraft.world.level.Level

import java.util

/**
 * `Level` → Lua 表的转换器。
 *
 * 1.7.10 通过 `oc:flatten` 把 `World` 折叠成它的 `WorldProvider`，
 * 再由 [[ConverterWorldProvider]] 展开。1.21.1 已没有 `WorldProvider`，
 * 改为折叠成 `Level#dimension()`（`ResourceKey[Level]`）。
 */
object ConverterWorld extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case level: Level => output.put("oc:flatten", level.dimension())
    case _ => // 忽略其它类型。
  }
}
