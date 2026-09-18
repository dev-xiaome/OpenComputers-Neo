package li.cil.oc.common.blockentity.traits

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.internal
import li.cil.oc.api.network.Node
import li.cil.oc.common.datacomponents.CompoundStorage
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.nbt.{CompoundTag, NbtOps}
import net.minecraft.world.level.Level

trait TextBuffer extends Environment with Tickable {
  private final val ClientBufferComponentsTag = Settings.namespace + "clientBufferComponents"
  private var pendingServerBufferData: CompoundTag = _
  private var movingClientLevel: Level = _
  lazy val buffer: internal.TextBuffer = {
    val screenItem = api.Items.get(Constants.BlockName.ScreenTier1).createItemStack(1)
    val buffer = api.Driver.driverFor(screenItem, getClass).createEnvironment(screenItem, this).asInstanceOf[api.internal.TextBuffer]
    val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
    buffer.setMaximumResolution(maxWidth, maxHeight)
    buffer.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
    buffer
  }

  override def node: Node = buffer.node

  def tier: Int

  override def updateEntity(): Unit = {
    ensureServerBufferLoaded()
    super.updateEntity()
    if (isClient || isConnected) {
      buffer.update()
    }
  }

  /** Create renders captured screens in a virtual level, but packets use the real client level. */
  def registerMovingClientBuffer(level: Level): Unit = buffer match {
    case moving: li.cil.oc.common.component.TextBuffer if movingClientLevel ne level =>
      // The captured client block entity can be constructed before its
      // component snapshot supplies the node address. Do not remember a
      // failed registration, or the real-world tracker will never receive
      // this screen's updates after the address arrives.
      if (moving.registerClientBufferOnLevel(level)) {
        movingClientLevel = level
      }
    case _ =>
  }

  def unregisterMovingClientBuffer(): Unit = buffer match {
    case moving: li.cil.oc.common.component.TextBuffer if movingClientLevel != null =>
      moving.unregisterClientBufferOnLevel(movingClientLevel)
      movingClientLevel = null
    case _ =>
  }

  // ----------------------------------------------------------------------- //

  private def reapplyTierToBuffer(): Unit = {
    // Re-apply tier-based limits before loading data. This guards against the
    // case where the `buffer` lazy val was forced before `load(nbt)` ran
    // (e.g. during network join scheduled by initialize()), which would leave
    // it initialised with the default tier-0 (OneBit) depth even for a
    // higher-tier screen.  setMaximumColorDepth only updates the `maxDepth`
    // field; it does NOT touch the already-constructed data buffer, so calling
    // it again here is safe and idempotent.
    val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
    buffer.setMaximumResolution(maxWidth, maxHeight)
    buffer.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
  }

  override def loadComponentsForClient(holder: DataComponentHolder): Unit = {
    super.loadComponentsForClient(holder)
    // BaseBlockEntity loads the legacy/client NBT before it loads OC's
    // components, so tier still has the constructor default during loadForClient.
    // Do this again after Screen.loadComponentsCommon has supplied the real tier.
    reapplyTierToBuffer()
  }

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)
    // The moving Screen is reconstructed from Create's NBT just like a client
    // render Screen: component loading supplies its real tier after the buffer
    // may already have been forced by loadForServer.
    reapplyTierToBuffer()
  }

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    // BlockEntity.loadStatic calls loadWithComponents/loadAdditional before it
    // assigns the level. Loading the external screen contents needs the
    // dimension and position, so postpone it until clearRemoved/initialize.
    if (getLevel == null) pendingServerBufferData = nbt.copy()
    else loadServerBufferData(nbt, provider)
  }

  private def loadServerBufferData(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    reapplyTierToBuffer()
    buffer.loadData(nbt, provider)
    // loadData also restores MAX_VIDEO_MODE when it is present. A stale save
    // can contain the constructor's tier-one limits, so enforce the physical
    // screen tier again after loading persisted data as well.
    reapplyTierToBuffer()
  }

  /**
    * Consume deferred external buffer data as soon as this screen has a level.
    *
    * Block entity lifecycle ordering is not identical for every host around a
    * screen (rack-mounted servers are reconstructed through the rack's item
    * inventory). Do not rely on initialize() being the only opportunity to
    * restore the buffer: exposing or saving the freshly constructed default
    * buffer first would overwrite the real contents with a blank T1 buffer.
    */
  private[oc] def ensureServerBufferLoaded(): Unit = {
    if (isServer && getLevel != null && pendingServerBufferData != null) {
      val data = pendingServerBufferData
      loadServerBufferData(data, getLevel.registryAccess())
      pendingServerBufferData = null
    }
  }

  override def onLoad(): Unit = {
    super.onLoad()
    ensureServerBufferLoaded()
  }

  override protected def initialize(): Unit = {
    super.initialize()
    ensureServerBufferLoaded()
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    ensureServerBufferLoaded()
    super.saveForServer(nbt, provider)
    // If the level is still unavailable, retain the existing auxiliary file
    // instead of replacing it with the lazy buffer's blank default state.
    if (pendingServerBufferData == null) buffer.saveData(nbt, provider)
  }

  override def loadForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForClient(nbt, provider)
    reapplyTierToBuffer()
    if (nbt.contains(ClientBufferComponentsTag)) {
      val storage = CompoundStorage.CODEC.parse(NbtOps.INSTANCE, nbt.get(ClientBufferComponentsTag)).getOrThrow()
      buffer.loadData(storage)
    }
    else buffer.loadData(nbt, provider)
    // The saved MAX_VIDEO_MODE may be stale or may have been produced while
    // this block entity still had its constructor-default tier.
    reapplyTierToBuffer()
  }

  override def saveForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForClient(nbt, provider)
    val storage = new CompoundStorage()
    buffer.saveData(storage)
    nbt.put(ClientBufferComponentsTag, CompoundStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).getOrThrow())
  }
}
