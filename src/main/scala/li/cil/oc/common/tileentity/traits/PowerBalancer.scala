package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.SidedEnvironment
import net.minecraft.core.Direction

/**
 * 在多个网络之间均衡能量缓冲的方块实体（配电箱 PowerDistributor）
 * （对应 1.7.10 的 `common.tileentity.traits.PowerBalancer`）。
 *
 * 1.21.1 迁移要点：
 *  - `updateEntity()` → [[TileEntity.tick]]（覆写时先调 `super.tick()`）。
 *  - `world.getTotalWorldTime` → `world.getGameTime`。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`（1.21.1 没有 `UNKNOWN`）。
 */
trait PowerBalancer extends PowerInformation with SidedEnvironment {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  var globalBuffer, globalBufferSize = 0.0

  def isConnected: Boolean

  override def tick(): Unit = {
    super.tick()
    if (isServer && isConnected && world.getGameTime % Settings.get.tickFrequency == 0) {
      val nodes = connectors
      def network(connector: Connector) = if (connector != null && connector.network != null) connector.network else this
      // Yeeeeah, so that just happened... it's not a beauty, but it works. This
      // is necessary because power in networks can be updated asynchronously,
      // i.e. in separate threads (e.g. to allow screens to consume energy when
      // they change, which usually happens in a computers executor thread).
      // This multi-lock only happens in the main server thread, though, so we
      // don't have to fear deadlocks. I think.
      network(nodes(0)).synchronized {
        network(nodes(1)).synchronized {
          network(nodes(2)).synchronized {
            network(nodes(3)).synchronized {
              network(nodes(4)).synchronized {
                network(nodes(5)).synchronized {
                  val (sumBuffer, sumSize) = distribute()
                  if (sumSize > 0) {
                    val ratio = sumBuffer / sumSize
                    for (node <- connectors if isPrimary(node)) {
                      node.changeBuffer(node.globalBufferSize * ratio - node.globalBuffer)
                    }
                  }
                  globalBuffer = sumBuffer
                  globalBufferSize = sumSize
                }
              }
            }
          }
        }
      }
      updatePowerInformation()
    }
  }

  protected def distribute(): (Double, Double) = {
    var sumBuffer, sumSize = 0.0
    for (node <- connectors if isPrimary(node)) {
      sumBuffer += node.globalBuffer
      sumSize += node.globalBufferSize
    }
    (sumBuffer, sumSize)
  }

  private def connectors: IndexedSeq[Connector] = Direction.values().toIndexedSeq.map(sidedNode(_) match {
    case connector: Connector => connector
    case _ => null
  })

  private def isPrimary(connector: Connector): Boolean = {
    val nodes = connectors
    connector != null && nodes(nodes.indexWhere(node => node != null && node.network == connector.network)) == connector
  }
}
