package li.cil.oc.server.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import li.cil.oc.Settings
import li.cil.oc.common.command.SimpleCommand
// 注意：`li.cil.oc` 包对象里的 NBT 隐式类对子包不可见，必须显式导入。
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_preventDisassembling [<boolean>]`（别名 `/oc_nodis`、`/oc_prevdis`）：
 * 给手上的物品打/去「禁止拆解」标记。
 *
 * 1.21.1 迁移要点：
 *  - `player.getHeldItem` → `player.getMainHandItem`；
 *  - `stack.hasTagCompound` / `getTagCompound` / `setTagCompound` → 由 `li.cil.oc` 包对象的
 *    隐式类提供的 `hasTag()` / `getTag()` / `setTag()`（底层是自定义数据组件）；
 *  - `stack.stackSize == 0`（空手）→ `stack.isEmpty`；
 *  - `CommandBase#parseBoolean` → Brigadier 的 [[BoolArgumentType]]。
 */
object NonDisassemblyAgreementCommand extends SimpleCommand("oc_preventDisassembling") {
  aliases += "oc_nodis"
  aliases += "oc_prevdis"

  private final val RequiredPermissionLevel = 2

  /** 标记键（与 1.7.10 一致，沿用 `oc:` 命名空间）。 */
  private val Key = Settings.namespace + "undisassemblable"

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.executes(context => {
      toggle(context.getSource)
      1
    })
    builder.then(
      RequiredArgumentBuilder.argument[CommandSourceStack, java.lang.Boolean]("value", BoolArgumentType.bool())
        .executes(context => {
          set(context.getSource, BoolArgumentType.getBool(context, "value"))
          1
        }))
  }

  private def toggle(source: CommandSourceStack): Unit = {
    val stack = heldStack(source)
    if (stack == null) return
    val nbt = stack.getTag()
    set(source, !(nbt != null && nbt.getBoolean(Key)))
  }

  private def set(source: CommandSourceStack, preventDisassembly: Boolean): Unit = {
    val stack = heldStack(source)
    if (stack == null) return
    // 1.21.1 的物品数据在数据组件里，空 NBT 应当写回 null 而不是留一个空 CompoundTag。
    val nbt = stack.getTag()
    if (preventDisassembly) {
      val tag = if (nbt != null) nbt else new net.minecraft.nbt.CompoundTag()
      tag.putBoolean(Key, true)
      stack.setTag(tag)
    }
    else if (nbt != null) {
      nbt.remove(Key)
      if (nbt.isEmpty) stack.setTag(null) else stack.setTag(nbt)
    }
  }

  /** 取命令执行者手上的物品；空手或非玩家时发提示并返回 `null`。 */
  private def heldStack(source: CommandSourceStack): net.minecraft.world.item.ItemStack = {
    val player = source.getPlayer
    if (player == null) {
      source.sendSystemMessage(Component.literal("Can only be used by players."))
      return null
    }
    val stack = player.getMainHandItem
    if (stack == null || stack.isEmpty) {
      source.sendSystemMessage(Component.literal("You must hold an item in your main hand."))
      return null
    }
    stack
  }
}
