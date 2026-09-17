package li.cil.oc.data

import java.util
import java.util.function.Consumer

import net.minecraft.advancements.AdvancementHolder
import net.minecraft.core.HolderLookup
import net.minecraft.data.advancements.AdvancementProvider
import net.minecraft.data.advancements.AdvancementSubProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.bus.api.SubscribeEvent

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
          // 1.21.1：AdvancementSubProvider 回调的第二个参数类型是 Consumer[AdvancementHolder]。
          override def generate(registries: HolderLookup.Provider, writer: Consumer[AdvancementHolder]): Unit = {
            Advancements.generate(registries, writer)
          }
        })
      )
    )
  }
}
