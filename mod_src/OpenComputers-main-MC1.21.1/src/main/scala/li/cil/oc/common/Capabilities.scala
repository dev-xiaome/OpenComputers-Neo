package li.cil.oc.common

import li.cil.oc.api.network.{Environment, SidedEnvironment}
import li.cil.oc.api.internal.Colored
import li.cil.oc.integration.Mods
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Direction
import net.neoforged.neoforge.capabilities.{BlockCapability, ItemCapability}
import net.neoforged.neoforge.energy.IEnergyStorage

object Capabilities {
  private def loc(path: String) = ResourceLocation.fromNamespaceAndPath(Mods.IDs.OpenComputers, path)

  val EnvironmentCapability: BlockCapability[Environment, Direction] =
    BlockCapability.createSided(loc("environment"), classOf[Environment])

  val SidedEnvironmentCapability: BlockCapability[SidedEnvironment, Direction] =
    BlockCapability.createSided(loc("sided_environment"), classOf[SidedEnvironment])

  val ColoredCapability: BlockCapability[Colored, Direction] =
    BlockCapability.createSided(loc("colored"), classOf[Colored])

  val EnergyItemCapability: ItemCapability[IEnergyStorage, java.lang.Void] =
    ItemCapability.createVoid(ResourceLocation.withDefaultNamespace("energy"), classOf[IEnergyStorage])
}
