package li.cil.oc.common.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack

import scala.collection.mutable

/**
 * OpenComputers 命令基类（1.21.1 版）。
 *
 * 1.7.10 用的是 `net.minecraft.command.CommandBase` + `ICommand`；
 * 1.21.1 改成 Brigadier（`com.mojang.brigadier`），命令树由
 * [[LiteralArgumentBuilder]] 组装，根节点注册到 `CommandDispatcher`。
 *
 * 子类只需实现 [[build]]，在其中用 `builder.then(...)`、`builder.executes(...)`
 * 挂上参数与执行体；别名会各自生成一个独立的根节点（Brigadier 没有内建别名机制）。
 */
abstract class SimpleCommand(val name: String) {
  protected val aliases = mutable.ListBuffer.empty[String]

  /** 子类在此挂上参数与执行体。 */
  def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit

  /** 该命令的所有根名称：主名 + 别名。 */
  def rootNames: Seq[String] = name +: aliases.toSeq

  /** 为每个根名称生成一个命令节点，供 `CommandDispatcher#register` 使用。 */
  def builders: Seq[LiteralArgumentBuilder[CommandSourceStack]] = rootNames.map { root =>
    val builder = LiteralArgumentBuilder.literal[CommandSourceStack](root)
    build(builder)
    builder
  }
}
