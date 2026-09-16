package li.cil.oc.common.init

import java.util.function.Consumer

import li.cil.oc.OpenComputersNeo
import net.minecraft.core.{BlockPos, RegistryAccess}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.{Item, ItemStack, TooltipFlag}
import net.minecraft.world.level.block.EntityBlock
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.{FMLEnvironment, FMLPaths}
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge

import scala.jdk.CollectionConverters._

/**
 * 临时的「启动自检」：只用于本次移植调试，验证完请直接删除本文件与主类里的调用。
 *
 * 背景：手工玩游戏才能覆盖到的两条路径（创造模式物品栏里逐个悬停物品、放下每个 OC 方块）
 * 在自动化验证里无法点击，因此这里在**客户端第一个 tick** 里把这两条路径各跑一遍，
 * 把异常按 `SELFCHECK` 前缀打进日志，方便一次性找齐剩余崩溃点。
 *
 * 开关：只有游戏目录下存在文件 `oc-selfcheck.on` 时才会启用（避免影响其他人的 runClient）。
 *
 * 覆盖范围：
 *  1. 每个已登记物品构造 1 个堆叠，并按普通/高级两种 `TooltipFlag` 构建 tooltip
 *     （等价于在创造模式物品栏里悬停该物品，包括 `Registry.get(stack)` 反查）；
 *  2. 每个 `opencomputers_neo` 命名空间下的方块：构造该方块的 `BlockEntity`
 *     （等价于在世界里放下该方块）、调用 `getUpdateTag`、构建方块物品 tooltip。
 */
object BootSelfCheck {

  private var done = false

  /** 由主类在 mod 构造期调用；未放置开关文件时什么都不做。 */
  def register(): Unit = {
    if (FMLEnvironment.dist != Dist.CLIENT) return
    if (!FMLPaths.GAMEDIR.get().resolve("oc-selfcheck.on").toFile.exists()) return
    OpenComputersNeo.log.info("[SELFCHECK] enabled; will run on the first client tick.")
    NeoForge.EVENT_BUS.addListener(classOf[ClientTickEvent.Post], new Consumer[ClientTickEvent.Post] {
      override def accept(event: ClientTickEvent.Post): Unit =
        if (!done) {
          done = true
          run()
        }
    })
  }

  private def run(): Unit = {
    var failures = 0
    var checked = 0

    // 1) 物品 tooltip。
    for (name <- Registry.creativeTabEntries) {
      checked += 1
      try {
        val stack = Registry.createItemStack(name, 1)
        if (stack != null && !stack.isEmpty) {
          tooltips(stack)
          Registry.get(stack)
        }
      }
      catch {
        case t: Throwable =>
          failures += 1
          OpenComputersNeo.log.error(s"[SELFCHECK] item failed: $name", t)
      }
    }

    // 2) 方块：方块实体构造 + 同步 tag + 方块物品 tooltip。
    for (entry <- BuiltInRegistries.BLOCK.entrySet().asScala
         if entry.getKey.location().getNamespace == OpenComputersNeo.MODID) {
      val block = entry.getValue
      val name = entry.getKey.location().getPath
      checked += 1
      try {
        val state = block.defaultBlockState()
        block match {
          case entityBlock: EntityBlock =>
            val blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state)
            if (blockEntity != null) {
              blockEntity.getUpdateTag(RegistryAccess.EMPTY)
            }
          case _ =>
        }
        val stack = new ItemStack(block)
        tooltips(stack)
        Registry.get(stack)
        block match {
          case hooks: li.cil.oc.common.block.SimpleBlockHooks =>
            val lines = new java.util.ArrayList[String]()
            hooks.addInformation(stack, null, lines, advanced = false)
            hooks.addInformation(stack, null, lines, advanced = true)
          case _ =>
        }
      }
      catch {
        case t: Throwable =>
          failures += 1
          OpenComputersNeo.log.error(s"[SELFCHECK] block failed: $name", t)
      }
    }

    OpenComputersNeo.log.info(s"[SELFCHECK] done; checked = $checked, failures = $failures.")
  }

  private def tooltips(stack: ItemStack): Unit = {
    // 与创造模式物品栏里悬停时一致：普通、高级、高级+创造三种标志各跑一遍。
    stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL)
    stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.ADVANCED)
    stack.getTooltipLines(Item.TooltipContext.EMPTY, null, new TooltipFlag.Default(true, true))
  }
}
