package li.cil.oc

import li.cil.oc.common.init.Items
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.{CreativeModeTab, CreativeModeTabs}
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.{DeferredRegister, RegistryObject}
import li.cil.oc.integration.opencomputers.ModOpenComputers

object CreativeTab {
  val CREATIVE_TABS: DeferredRegister[CreativeModeTab] =
    DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OpenComputers.ID)

  val MAIN: RegistryObject[CreativeModeTab] = CREATIVE_TABS.register("main", () =>
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
      event.accept(Items.createChargedHoverBoots())
    }
  }
}