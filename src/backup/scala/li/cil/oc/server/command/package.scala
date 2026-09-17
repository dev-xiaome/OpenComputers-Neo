package li.cil.oc.server

/**
 * `server/command` 包对象。
 *
 * 1.7.10 版提供两样东西，这里都按 1.21.1 的形态重写：
 *  - `string2text` 隐式转换（`String` → `IChatComponent`）：1.21.1 统一用
 *    `Component.literal(...)`，已无必要，删除；需要发消息的地方直接构造 `Component`。
 *  - `getOpLevel(sender)`：1.7.10 通过 `MinecraftServer#getConfigurationManager` 里的
 *    `UserListOpsEntry` 手工取 OP 等级；1.21.1 的 Brigadier 给的是
 *    `CommandSourceStack`，直接用它自带的权限判定（`hasPermission(level)`）。
 *
 * 因此现在只剩一个薄封装 [[hasOpLevel]]。
 */
package object command {

  /**
   * 判断命令源是否拥有至少 `level` 级权限（对应 1.7.10 的 `getOpLevel(sender) >= level`）。
   *
   * 1.21.1 的权限来源比 1.7.10 丰富（单人房主、专用服务器 OP、命令方块、数据包函数等），
   * `CommandSourceStack#hasPermission` 已经把它们统一处理掉了，无需再手工查 OP 名单。
   */
  def hasOpLevel(source: net.minecraft.commands.CommandSourceStack, level: Int): Boolean =
    source.hasPermission(level)
}
