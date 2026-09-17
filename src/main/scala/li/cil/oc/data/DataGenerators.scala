package li.cil.oc.data

import java.util
import java.util.function.Consumer

import net.minecraft.advancements.Advancement
import net.minecraft.core.HolderLookup
import net.minecraft.data.advancements.AdvancementProvider
import net.minecraft.data.advancements.AdvancementSubProvider
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object DataGenerators {
  @SubscribeEvent
  def gatherData(event: GatherDataEvent): Unit = {
    val generator = event.getGenerator

    generator.addProvider(
      event.includeServer(),
      new AdvancementProvider(
        generator.getPackOutput,
        event.getLookupProvider,
        util.List.of(new AdvancementSubProvider {
          override def generate(registries: HolderLookup.Provider, writer: Consumer[Advancement]): Unit = {
            Advancements.generate(registries, writer)
          }
        })
      )
    )
  }
}
