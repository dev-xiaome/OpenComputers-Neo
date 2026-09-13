package li.cil.oc.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
  * `ItemStack.getTag()/hasTag()/setTag()` 扩展的提供者。
  *
  * [[li.cil.oc]] 包对象虽然混入了 [[ExtendedItemStack]]，但 Scala 2.13 不会把包对象
  * 从 trait 继承来的隐式类暴露给子包，因此 `li.cil.oc.util` 内的文件必须显式引入。
  * [[ExtendedItemStack]] 是普通 trait（没有伴生对象），`import ExtendedItemStack._`
  * 无法编译；所以这里用一个继承它的 object 作为引入入口，其它工具类
  * `import ItemStackNBTExtensions._` 即可。
  */
object ItemStackNBTExtensions extends ExtendedItemStack

import ItemStackNBTExtensions._

/**
  * @author asie, Vexatos
  *
  * 1.21.1 迁移要点：
  *  - 1.7.10 的 `ItemStack#hasTagCompound/getTagCompound/setTagCompound` 在 1.21.1 已不存在，
  *    由 [[ExtendedItemStack]] 的隐式类补回为 `hasTag()/getTag()/setTag(tag)`
  *  - `CompoundTag#getInteger` → `getInt`
  *  - 颜色本身仍存放在 `display.color`，与皮革盔甲的约定保持一致
  */
object ItemColorizer {
  /**
    * Return whether the specified armor ItemStack has a color.
    */
  def hasColor(stack: ItemStack): Boolean = {
    val tag = stack.getTag()
    tag != null && tag.contains("display") && tag.getCompound("display").contains("color")
  }

  /**
    * Return the color for the specified armor ItemStack.
    */
  def getColor(stack: ItemStack): Int = {
    val tag = stack.getTag()
    if (tag != null && tag.contains("display")) {
      val displayTag = tag.getCompound("display")
      if (displayTag.contains("color")) displayTag.getInt("color") else -1
    }
    else -1
  }

  def removeColor(stack: ItemStack): Unit = {
    val tag = stack.getTag()
    if (tag != null && tag.contains("display")) {
      val displayTag = tag.getCompound("display")
      if (displayTag.contains("color")) displayTag.remove("color")
    }
  }

  def setColor(stack: ItemStack, color: Int): Unit = {
    // `getTag()` 在不存在时返回 null，需要先创建再写回。
    var tag = stack.getTag()
    if (tag == null) {
      tag = new CompoundTag
      // 1.21.1 没有 `ItemStack#put`，隐式类里对应的是 `setTag`。
      stack.setTag(tag)
    }
    if (!tag.contains("display")) {
      tag.put("display", new CompoundTag)
    }
    tag.getCompound("display").putInt("color", color)
  }
}
