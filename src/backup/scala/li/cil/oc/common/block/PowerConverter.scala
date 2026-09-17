package li.cil.oc.common.block

import java.text.DecimalFormat
import java.util

import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 能量转换器（原 1.7.10 `PowerConverter`，把其它模组的能量转换成 OC 内部能量）。
 *
 * 1.21.1 迁移要点：
 *  - [[traits.PowerAcceptor]] 提供 `energyThroughput`；`createTileEntity` →
 *    [[SimpleBlockHooks.createBlockEntity]]，构造为 `new tileentity.PowerConverter(pos, state)`；
 *  - 原构造里的 `if (Settings.get.ignorePower) setCreativeTab(null)`：1.21.1 没有创造模式
 *    标签页字段，隐藏物品由注册层的 `Registry` 决定，这里只保留标记，
 *    TODO(common.init.Registry): 调度方在注册方块物品时按 `Settings.get.ignorePower`
 *    决定是否加入创造模式标签页（原 `NEI.hide(this)` 随 NEI 集成一起删除）。
 *  - 整套图标系统删除（`customTextures` 面序语义见下）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定（沿用 `GenericTop`），北 / 南 / 西 / 东 = `PowerConverterSide`。
 *
 * ==降级说明==
 * TODO(integration): 原 tooltip 尾部按 `Mods.Factorization` / `Mods.IndustrialCraft2` /
 * `Mods.Mekanism` / `Mods.CoFHEnergy` 是否可用，逐个显示换算比率
 * （`Settings.get.ratioFactorization` 等）。这些模组集成在 1.21.1 都不再移植，因此这里
 * **不再冒用**它们的本地化键（`oa:tooltip.PowerConverter.Factorization` 等尚未生成，
 * 直接调用只会得到空行），只保留 `addExtension` / `addRatio` 两个换算与格式化帮助方法，
 * 等对应驱动移植后恢复调用即可。
 */
class PowerConverter(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.PowerAcceptor {

  /** 原 `Settings.get.ignorePower` 时该方块不出现在物品栏（见类注释里的 TODO）。 */
  private val hidden = Settings.get.ignorePower

  private val formatter = new DecimalFormat("#.#")

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.powerConverterRate

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.PowerConverter(pos, state)

  // ----------------------------------------------------------------------- //

  /** 原 `addExtension`：把换算比率格式化成 `K` / `M` / `G` 结尾的短字符串。 */
  private def addExtension(x: Double): String =
    if (x >= 1e9) formatter.format(x / 1e9) + "G"
    else if (x >= 1e6) formatter.format(x / 1e6) + "M"
    else if (x >= 1e3) formatter.format(x / 1e3) + "K"
    else formatter.format(x)

  /**
   * 原 `addRatio`：以「A:B」形式显示 1 单位内部能量与 `ratio` 单位外部能量的换算。
   *
   * 目前没有调用方（见类注释里的降级说明），保留以维持与原实现同名的私有成员。
   */
  private def addRatio(tooltip: util.List[String], name: String, ratio: Double): Unit = {
    val (a, b) =
      if (ratio > 1) (1.0, ratio)
      else (1.0 / ratio, 1.0)
    tooltip.addAll(Tooltip.get(getClass.getSimpleName + "." + name, addExtension(a), addExtension(b)))
  }
}
