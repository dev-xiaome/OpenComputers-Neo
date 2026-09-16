package li.cil.oc.client.renderer.item

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.{Quaternionf, Vector3f}
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.{UpgradeRenderer => DriverUpgradeRenderer}
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.{Dist, OnlyIn}

@OnlyIn(Dist.CLIENT)
object ItemUpgradeRenderer {
  lazy val craftingUpgrade   = api.Items.get(Constants.ItemName.CraftingUpgrade)
  lazy val generatorUpgrade  = api.Items.get(Constants.ItemName.GeneratorUpgrade)
  lazy val inventoryUpgrade  = api.Items.get(Constants.ItemName.InventoryUpgrade)

  def preferredMountPoint(stack: ItemStack, availableMountPoints: java.util.Set[String]): String = {
    val descriptor = api.Items.get(stack)
    if (descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade) {
      if (descriptor == generatorUpgrade && availableMountPoints.contains(DriverUpgradeRenderer.MountPointName.BottomBack))  DriverUpgradeRenderer.MountPointName.BottomBack
      else if (descriptor == inventoryUpgrade && availableMountPoints.contains(DriverUpgradeRenderer.MountPointName.TopBack)) DriverUpgradeRenderer.MountPointName.TopBack
      else DriverUpgradeRenderer.MountPointName.Any
    } else DriverUpgradeRenderer.MountPointName.None
  }

  def canRender(stack: ItemStack): Boolean = {
    val descriptor = api.Items.get(stack)
    descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade
  }

  def render(matrix: PoseStack, buffer: MultiBufferSource, light: Int, stack: ItemStack, mountPoint: MountPoint): Unit = {
    val descriptor = api.Items.get(stack)

    if (descriptor == api.Items.get(Constants.ItemName.CraftingUpgrade)) {
      drawSimpleBlock(matrix, buffer.getBuffer(RenderTypes.UPGRADE_CRAFTING), light, mountPoint)
      RenderState.checkError(getClass.getName + ".renderItem: crafting upgrade")
    }
    else if (descriptor == api.Items.get(Constants.ItemName.GeneratorUpgrade)) {
      drawSimpleBlock(matrix, buffer.getBuffer(RenderTypes.UPGRADE_GENERATOR), light, mountPoint,
        if (Item.dataTag(stack).getInt("remainingTicks") > 0) 0.5f else 0)
      RenderState.checkError(getClass.getName + ".renderItem: generator upgrade")
    }
    else if (descriptor == api.Items.get(Constants.ItemName.InventoryUpgrade)) {
      drawSimpleBlock(matrix, buffer.getBuffer(RenderTypes.UPGRADE_INVENTORY), light, mountPoint)
      RenderState.checkError(getClass.getName + ".renderItem: inventory upgrade")
    }
  }

  private val (minX, minY, minZ) = (-0.1f, -0.1f, -0.1f)
  private val (maxX, maxY, maxZ) = ( 0.1f,  0.1f,  0.1f)

  @inline private def lu(light: Int): Int = light & 0xFFFF
  @inline private def lv(light: Int): Int = (light >> 16) & 0xFFFF

  private def drawSimpleBlock(stack: PoseStack, r: VertexConsumer, light: Int, mountPoint: MountPoint, frontOffset: Float = 0): Unit = {
    stack.mulPose(new Quaternionf().rotationAxis(
      mountPoint.rotation.w * (Math.PI.toFloat / 180f),
      mountPoint.rotation.x,
      mountPoint.rotation.y,
      mountPoint.rotation.z
    ))
    stack.translate(mountPoint.offset.x, mountPoint.offset.y, mountPoint.offset.z)

    // Front.
    r.addVertex(stack.last.pose(), minX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(frontOffset,        0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 0, 1)
    r.addVertex(stack.last.pose(), maxX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(frontOffset + 0.5f, 0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 0, 1)
    r.addVertex(stack.last.pose(), maxX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(frontOffset + 0.5f, 0   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 0, 1)
    r.addVertex(stack.last.pose(), minX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(frontOffset,        0   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 0, 1)

    // Top.
    r.addVertex(stack.last.pose(), maxX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(1,    0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 1, 0)
    r.addVertex(stack.last.pose(), maxX, maxY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(1,    1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 1, 0)
    r.addVertex(stack.last.pose(), minX, maxY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 1, 0)
    r.addVertex(stack.last.pose(), minX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, 1, 0)

    // Bottom.
    r.addVertex(stack.last.pose(), minX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, -1, 0)
    r.addVertex(stack.last.pose(), minX, minY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, -1, 0)
    r.addVertex(stack.last.pose(), maxX, minY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(1,    1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, -1, 0)
    r.addVertex(stack.last.pose(), maxX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(1,    0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 0, -1, 0)

    // Left.
    r.addVertex(stack.last.pose(), maxX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0,    0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 1, 0, 0)
    r.addVertex(stack.last.pose(), maxX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0,    1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 1, 0, 0)
    r.addVertex(stack.last.pose(), maxX, minY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, 1, 0, 0)
    r.addVertex(stack.last.pose(), maxX, maxY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, 1, 0, 0)

    // Right.
    r.addVertex(stack.last.pose(), minX, minY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0,    1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, -1, 0, 0)
    r.addVertex(stack.last.pose(), minX, maxY, maxZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0,    0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, -1, 0, 0)
    r.addVertex(stack.last.pose(), minX, maxY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 0.5f).setUv2(lu(light), lv(light)).setNormal(stack.last, -1, 0, 0)
    r.addVertex(stack.last.pose(), minX, minY, minZ).setColor(0xFF, 0xFF, 0xFF, 0xFF).setUv(0.5f, 1   ).setUv2(lu(light), lv(light)).setNormal(stack.last, -1, 0, 0)
  }
}