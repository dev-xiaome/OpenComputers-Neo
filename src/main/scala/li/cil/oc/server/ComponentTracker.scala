package li.cil.oc.server

import li.cil.oc.common
import net.minecraft.world.level.Level

/**
 * 服务端组件跟踪表。
 *
 * 与客户端版本相反：只有在**非客户端**世界（即 `ServerLevel`）卸载时才清空缓存。
 * 1.21.1 用 `Level#isClientSide` 取代 1.7.10 的 `World#isRemote`。
 */
object ComponentTracker extends common.ComponentTracker {
  override protected def clear(world: Level): Unit = if (!world.isClientSide) super.clear(world)
}
