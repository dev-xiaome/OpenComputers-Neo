package li.cil.oc.server.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.arguments.StringArgumentType
import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

/**
 * `/oc_debugWhitelist`：管理 debug 卡白名单。
 *
 * 子命令（与 1.7.10 版一致）：
 *  - `revoke`                       —— 让自己手上的 debug 卡失效（任何玩家都可用）
 *  - `revoke <player>`              —— 让指定玩家的卡失效（需要 2 级权限）
 *  - `list`                         —— 列出白名单（需要 2 级权限）
 *  - `add <player>` / `remove <player>` —— 增删白名单（需要 2 级权限）
 *
 * 1.21.1 迁移要点：
 *  - `ICommandSender#getCommandSenderName` → `CommandSourceStack#getTextName`；
 *  - `getOpLevel(sender) >= 2` → `source.hasPermission(2)`；
 *  - 命令本身的权限要求是 0（任意玩家可 revoke 自己），细粒度权限在各子命令里判定。
 */
object DebugWhitelistCommand extends SimpleCommand("oc_debugWhitelist") {

  /** 与 1.7.10 一致：命令整体不设权限门槛，靠后续分支判定。 */
  private final val RequiredPermissionLevel = 0

  /** 白名单操作所需的 OP 等级。 */
  private final val WhitelistPermissionLevel = 2

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))

    // 任何人都能撤销自己的卡。
    builder.then(
      LiteralArgumentBuilder.literal[CommandSourceStack]("revoke")
        .executes(context => {
          revokeUser(context.getSource, context.getSource.getTextName)
          1
        }))

    // 以下三个子命令都需要 2 级权限。
    builder.then(
      LiteralArgumentBuilder.literal[CommandSourceStack]("revoke")
        .requires(source => hasOpLevel(source, WhitelistPermissionLevel))
        .then(RequiredArgumentBuilder.argument[CommandSourceStack, String]("player", StringArgumentType.word())
          .executes(context => {
            revokeUser(context.getSource, StringArgumentType.getString(context, "player"))
            1
          })))

    builder.then(
      LiteralArgumentBuilder.literal[CommandSourceStack]("list")
        .requires(source => hasOpLevel(source, WhitelistPermissionLevel))
        .executes(context => {
          listWhitelist(context.getSource)
          1
        }))

    builder.then(
      LiteralArgumentBuilder.literal[CommandSourceStack]("add")
        .requires(source => hasOpLevel(source, WhitelistPermissionLevel))
        .then(RequiredArgumentBuilder.argument[CommandSourceStack, String]("player", StringArgumentType.word())
          .executes(context => {
            whitelistOf(context.getSource) match {
              case Some(wl) =>
                wl.add(StringArgumentType.getString(context, "player"))
                context.getSource.sendSystemMessage(Component.literal("§aPlayer was added to whitelist."))
              case _ => // 已在 whitelistOf 里发过提示。
            }
            1
          })))

    builder.then(
      LiteralArgumentBuilder.literal[CommandSourceStack]("remove")
        .requires(source => hasOpLevel(source, WhitelistPermissionLevel))
        .then(RequiredArgumentBuilder.argument[CommandSourceStack, String]("player", StringArgumentType.word())
          .executes(context => {
            whitelistOf(context.getSource) match {
              case Some(wl) =>
                wl.remove(StringArgumentType.getString(context, "player"))
                context.getSource.sendSystemMessage(Component.literal("§aPlayer was removed from whitelist"))
              case _ => // 已在 whitelistOf 里发过提示。
            }
            1
          })))

    // 不带参数（或参数不认识）时打印用法，与 1.7.10 的 `case _` 分支一致。
    builder.executes(context => {
      context.getSource.sendSystemMessage(Component.literal("§e" + usage(context.getSource)))
      1
    })
  }

  private def usage(source: CommandSourceStack): String =
    if (hasOpLevel(source, WhitelistPermissionLevel))
      name + " [revoke|add|remove] <player> OR " + name + " [revoke|list]"
    else name + " revoke"

  /** 取出白名单；未启用白名单模式时给命令源发提示并返回 `None`。 */
  private def whitelistOf(source: CommandSourceStack): Option[DebugCardAccess.Whitelist] =
    Settings.get.debugCardAccess match {
      case wl: DebugCardAccess.Whitelist => Some(wl)
      case _ =>
        source.sendSystemMessage(Component.literal("§cDebug card whitelisting is not enabled."))
        None
    }

  private def revokeUser(source: CommandSourceStack, player: String): Unit = whitelistOf(source).foreach { wl =>
    if (wl.isWhitelisted(player)) {
      wl.invalidate(player)
      source.sendSystemMessage(Component.literal("§aAll your debug cards were invalidated."))
    }
    else source.sendSystemMessage(Component.literal("§cYou are not whitelisted to use debug card."))
  }

  private def listWhitelist(source: CommandSourceStack): Unit = whitelistOf(source).foreach { wl =>
    val players = wl.whitelist
    if (players.nonEmpty)
      source.sendSystemMessage(Component.literal("§aCurrently whitelisted players: §e" + players.mkString(", ")))
    else
      source.sendSystemMessage(Component.literal("§cThere is no currently whitelisted players."))
  }
}
