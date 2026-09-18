package li.cil.oc

import li.cil.oc.common.init.Items
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.{CreativeModeTab, CreativeModeTabs}
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.registries.{DeferredHolder, DeferredRegister}
import li.cil.oc.integration.opencomputers.ModOpenComputers

object CreativeTab {
  val CREATIVE_TABS: DeferredRegister[CreativeModeTab] =
    DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OpenComputers.ID)

  val MAIN: DeferredHolder[CreativeModeTab, CreativeModeTab] = CREATIVE_TABS.register("main", () =>
    CreativeModeTab.builder()
      .title(Component.translatable(s"itemGroup.${OpenComputers.Name}"))
      .icon(() => api.Items.get(Constants.BlockName.CaseTier1).createItemStack(1))
      .build()
  )

  @SubscribeEvent
  def onBuildContents(event: BuildCreativeModeTabContentsEvent): Unit = {
    if (event.getTabKey == MAIN.getKey) {
      Items.decorateCreativeTab(event, ModOpenComputers.hasRedstoneCardT2)
    } else if (event.getTabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) {
      // 同 decorateCreativeTab：标签页会反复重建，NeoForge 的 accept 命中已存在条目会直接抛异常。
      val hoverBoots = Items.createChargedHoverBoots()
      if (!hoverBoots.isEmpty
        && !event.getParentEntries.contains(hoverBoots)
        && !event.getSearchEntries.contains(hoverBoots)) {
        event.accept(hoverBoots)
      }
    }
  }
}