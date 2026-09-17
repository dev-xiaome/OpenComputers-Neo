package li.cil.oc.common.block.traits

import li.cil.oc.common.block.SimpleBlockHooks

/**
 * 非正常实心方块（对应 1.7.10 的 `block.traits.SpecialBlock`）。
 *
 * 1.7.10 通过 `isOpaqueCube = false` / `renderAsNormalBlock = false` 让渲染器不把方块当实心；
 * 1.21.1 这两个标志都不存在，等价语义是**构造属性**里的 `noOcclusion()`
 * （见 [[li.cil.oc.common.block.SimpleBlock.nonOccluding()]]）与自定义 [[SimpleBlockHooks.blockShape]]。
 *
 * 另外 1.7.10 的 `isBlockSolid` 在 1.21.1 已被「碰撞形状 + 面坚固判定」取代
 * （`BlockState#isFaceSturdy` / `getCollisionShape`），不再需要覆写，
 * 因此本 trait 现在只是语义标记，保留它可以让各方块的继承列表与原代码一致。
 */
trait SpecialBlock extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>
}
