package li.cil.oc.common.tileentity.traits.power

import net.neoforged.fml.common.Optional
import factorization.api.Charge
import factorization.api.Coord
import factorization.api.IChargeConductor
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.CompoundTag

@Injectable.Interface(value = "factorization.api.IChargeConductor", modid = Mods.IDs.Factorization)
trait Factorization extends Common {
  private def useFactorizationPower() = isServer && Mods.Factorization.isAvailable

  @Optional.Method(modid = Mods.IDs.Factorization)
  private lazy val charge: AnyRef = this match {
    case conductor: IChargeConductor => new Charge(conductor)
    case _ =>
      OpenComputers.log.warn("Failed setting up Factorization power, which most likely means the class transformer did not run. You're probably running in an incorrectly configured development environment. Try adding `-Dfml.coreMods.load=li.cil.oc.common.launch.TransformerLoader` to the VM options of your run configuration.")
      null
  }

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    if (useFactorizationPower()) updateEnergy()
    super.updateEntity()
  }

  @Optional.Method(modid = Mods.IDs.Factorization)
  private def updateEnergy(): Unit = {
    getCharge.update()
    if (world.getTotalWorldTime % Settings.get.tickFrequency == 0) {
      tryAllSides((demand, _) => getCharge.deplete(demand.toInt), Power.fromCharge, Power.toCharge)
    }
  }

  override def invalidate(): Unit = {
    if (useFactorizationPower()) invalidateCharge()
    super.invalidate()
  }

  @Optional.Method(modid = Mods.IDs.Factorization)
  private def invalidateCharge(): Unit = {
    getCharge.invalidate()
  }

  override def onChunkUnload(): Unit = {
    if (useFactorizationPower()) removeCharge()
    super.onChunkUnload()
  }

  @Optional.Method(modid = Mods.IDs.Factorization)
  private def removeCharge(): Unit = {
    if (!isInvalid) getCharge.remove()
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (useFactorizationPower()) loadCharge(nbt)
  }

  @Optional.Method(modid = Mods.IDs.Factorization)
  private def loadCharge(nbt: CompoundTag): Unit = {
    getCharge.readFromNBT(nbt, "fzpower")
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    if (useFactorizationPower()) saveCharge(nbt)
  }

  @Optional.Method(modid = Mods.IDs.Factorization)
  private def saveCharge(nbt: CompoundTag): Unit = {
    getCharge.writeToNBT(nbt, "fzpower")
  }

  // ----------------------------------------------------------------------- //

  @Optional.Method(modid = Mods.IDs.Factorization)
  def getCharge = if (Mods.Factorization.isAvailable) charge.asInstanceOf[Charge] else null

  @Optional.Method(modid = Mods.IDs.Factorization)
  def getInfo = ""

  @Optional.Method(modid = Mods.IDs.Factorization)
  def getCoord = new Coord(this)
}
