package li.cil.oc.integration.util

import li.cil.oc.integration.Mods
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction

import scala.collection.mutable

object BundledRedstone {
  val providers = mutable.Buffer.empty[RedstoneProvider]

  def addProvider(provider: RedstoneProvider): Unit = providers += provider

  def isAvailable = Mods.MineFactoryReloaded.isAvailable || providers.nonEmpty

  def computeInput(pos: BlockPosition, side: Direction): Int = {
    if (pos.world.get.blockExists(pos.offset(side)))
      providers.map(_.computeInput(pos, side)).padTo(1, 0).max
    else 0
  }

  def computeBundledInput(pos: BlockPosition, side: Direction): Array[Int] = {
    if (pos.world.get.blockExists(pos.offset(side))) {
      val inputs = providers.map(_.computeBundledInput(pos, side)).filter(_ != null)
      if (inputs.isEmpty) null
      // 1.21.1：`(a, b).zipped` 依赖 2.13 已移除的 `Tuple2Zipped` 隐式转换，
      // 改为两个数组 `zip` 之后逐元素取最大值（与原语义一致）。
      else inputs.reduce((a, b) => a.zip(b).map { case (l, r) => math.max(l, r) })
    }
    else null
  }

  trait RedstoneProvider {
    def computeInput(pos: BlockPosition, side: Direction): Int

    def computeBundledInput(pos: BlockPosition, side: Direction): Array[Int]
  }

}
