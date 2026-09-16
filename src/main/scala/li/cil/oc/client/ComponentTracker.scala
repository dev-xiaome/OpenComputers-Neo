package li.cil.oc.client

import li.cil.oc.common
import net.minecraft.world.level.Level

/**
 * 客户端组件缓存（对应 1.7.10 的 `li.cil.oc.client.ComponentTracker`）。
 *
 * 1.7.10 里客户端 / 服务端各有一份 [[common.ComponentTracker]] 子类，唯一差别是
 * [[clear]] 的清空条件：客户端只在客户端世界卸载时清空（集成服务器里服务端世界的
 * 卸载事件也会经过同一个 object，因此必须把非客户端世界挡掉）。
 *
 * ==1.21.1 迁移要点==
 *  - `World#isRemote` → `Level#isClientSide`。
 *  - 父类的缓存键已由「数字维度 id」改为 `Level#dimension()`，本子类无需再处理维度。
 *
 * 监听注册：由主类调用一次继承来的 `li.cil.oc.client.ComponentTracker.initialize()`，
 * 它会把 `LevelEvent.Unload` 接到本对象的 `onWorldUnload` 上。
 */
object ComponentTracker extends common.ComponentTracker {
  override protected def clear(world: Level): Unit =
    if (world != null && world.isClientSide) super.clear(world)
}
