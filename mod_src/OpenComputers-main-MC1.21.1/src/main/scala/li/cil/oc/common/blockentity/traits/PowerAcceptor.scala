package li.cil.oc.common.blockentity.traits

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

trait PowerAcceptor
  extends power.Common {
  override protected def initialize(): Unit = {
    super.initialize()
    PowerAcceptorHooks.onInitialize(this)
  }

  override def updateEntity(): Unit = {
    super.updateEntity()
    PowerAcceptorHooks.onUpdate(this)
  }

  override def dispose(): Unit = {
    PowerAcceptorHooks.onDispose(this)
    super.dispose()
  }

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    PowerAcceptorHooks.onLoad(this, nbt, provider)
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    PowerAcceptorHooks.onSave(this, nbt, provider)
  }
}

trait PowerAcceptorIntegration {
  def initialize(host: PowerAcceptor): Unit

  def update(host: PowerAcceptor): Unit

  def dispose(host: PowerAcceptor): Unit

  def load(host: PowerAcceptor, nbt: CompoundTag, provider: HolderLookup.Provider): Unit

  def save(host: PowerAcceptor, nbt: CompoundTag, provider: HolderLookup.Provider): Unit
}

object PowerAcceptorHooks {
  @volatile private var integration: Option[PowerAcceptorIntegration] = None

  def install(value: PowerAcceptorIntegration): Unit = integration = Some(value)

  def onInitialize(host: PowerAcceptor): Unit = integration.foreach(_.initialize(host))

  def onUpdate(host: PowerAcceptor): Unit = integration.foreach(_.update(host))

  def onDispose(host: PowerAcceptor): Unit = integration.foreach(_.dispose(host))

  def onLoad(host: PowerAcceptor, nbt: CompoundTag, provider: HolderLookup.Provider): Unit =
    integration.foreach(_.load(host, nbt, provider))

  def onSave(host: PowerAcceptor, nbt: CompoundTag, provider: HolderLookup.Provider): Unit =
    integration.foreach(_.save(host, nbt, provider))
}
