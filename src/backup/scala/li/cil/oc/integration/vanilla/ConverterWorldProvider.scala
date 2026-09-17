package li.cil.oc.integration.vanilla

import com.google.common.hash.Hashing
import li.cil.oc.api
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

import java.nio.charset.StandardCharsets
import java.util
import java.util.UUID

/**
 * 维度标识 → Lua 表的转换器。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 的 `net.minecraft.world.WorldProvider` 已随维度系统重写而移除，
 *    维度的稳定标识改为 `ResourceKey[Level]`（见 [[ConverterWorld]]）。
 *  - 原实现用 `(seed, dimensionId)` 的 MD5 生成稳定 UUID；
 *    1.21.1 已无法直接拿到维度种子（`WorldProvider#getSeed`），
 *    因此这里退化为「用维度 ResourceLocation 生成稳定 UUID」。
 *
 * TODO(port): 若确实需要「同一个维度名在不同世界/存档下 id 不同」的旧语义，
 * 需要从 `ServerLevel#getSeed` 取值后一并参与哈希，但目前 OC 侧只把 id 当作稳定键使用。
 */
object ConverterWorldProvider extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case key: ResourceKey[_] =>
      val location = key.location()
      output.put("id", UUID.nameUUIDFromBytes(Hashing.md5().newHasher().
        putString(location.toString, StandardCharsets.UTF_8).
        hash().asBytes()).toString)
      output.put("name", location.toString)
    case _ => // 忽略其它类型。
  }
}
