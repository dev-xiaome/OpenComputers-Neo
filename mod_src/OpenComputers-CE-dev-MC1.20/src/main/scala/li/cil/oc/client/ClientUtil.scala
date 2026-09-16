package li.cil.oc.client

import net.minecraft.client.Minecraft

object ClientUtil {
  def isPaused: Boolean = Minecraft.getInstance.isPaused
}
