package net.minecraft.network.protocol.game;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.ping.ClientPongPacketListener;

/**
 * PacketListener for the client side of the PLAY protocol.
 */
public interface ClientGamePacketListener extends ClientPongPacketListener, ClientCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    /**
     * Spawns an instance of the objecttype indicated by the packet and sets its position and momentum
     */
    void handleAddEntity(ClientboundAddEntityPacket packet);

    /**
     * Spawns an experience orb and sets its value (amount of XP)
     */
    void handleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet);

    /**
     * May create a scoreboard objective, remove an objective from the scoreboard or update an objectives' displayname
     */
    void handleAddObjective(ClientboundSetObjectivePacket packet);

    /**
     * Renders a specified animation: Waking up a player, a living entity swinging its currently held item, being hurt or receiving a critical hit by normal or magical means
     */
    void handleAnimate(ClientboundAnimatePacket packet);

    void handleHurtAnimation(ClientboundHurtAnimationPacket packet);

    /**
     * Updates the players statistics or achievements
     */
    void handleAwardStats(ClientboundAwardStatsPacket packet);

    void handleAddOrRemoveRecipes(ClientboundRecipePacket packet);

    /**
     * Updates all registered IWorldAccess instances with destroyBlockInWorldPartially
     */
    void handleBlockDestruction(ClientboundBlockDestructionPacket packet);

    /**
     * Creates a sign in the specified location if it didn't exist and opens the GUI to edit its text
     */
    void handleOpenSignEditor(ClientboundOpenSignEditorPacket packet);

    /**
     * Updates the NBTTagCompound metadata of instances of the following entitytypes: Mob spawners, command blocks, beacons, skulls, flowerpot
     */
    void handleBlockEntityData(ClientboundBlockEntityDataPacket packet);

    /**
     * Triggers Block.onBlockEventReceived, which is implemented in BlockPistonBase for extension/retraction, BlockNote for setting the instrument (including audiovisual feedback) and in BlockContainer to set the number of players accessing a (Ender)Chest
     */
    void handleBlockEvent(ClientboundBlockEventPacket packet);

    /**
     * Updates the block and metadata and generates a blockupdate (and notify the clients)
     */
    void handleBlockUpdate(ClientboundBlockUpdatePacket packet);

    void handleSystemChat(ClientboundSystemChatPacket packet);

    void handlePlayerChat(ClientboundPlayerChatPacket packet);

    void handleDisguisedChat(ClientboundDisguisedChatPacket packet);

    void handleDeleteChat(ClientboundDeleteChatPacket packet);

    /**
     * Received from the servers PlayerManager if between 1 and 64 blocks in a chunk are changed. If only one block requires an update, the server sends S23PacketBlockChange and if 64 or more blocks are changed, the server sends S21PacketChunkData
     */
    void handleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet);

    /**
     * Updates the worlds MapStorage with the specified MapData for the specified map-identifier and invokes a MapItemRenderer for it
     */
    void handleMapItemData(ClientboundMapItemDataPacket packet);

    /**
     * Resets the ItemStack held in hand and closes the window that is opened
     */
    void handleContainerClose(ClientboundContainerClosePacket packet);

    /**
     * Handles the placement of a specified ItemStack in a specified container/inventory slot
     */
    void handleContainerContent(ClientboundContainerSetContentPacket packet);

    void handleHorseScreenOpen(ClientboundHorseScreenOpenPacket packet);

    /**
     * Sets the progressbar of the opened window to the specified value
     */
    void handleContainerSetData(ClientboundContainerSetDataPacket packet);

    /**
     * Handles picking up an ItemStack or dropping one in your inventory or an open (non-creative) container
     */
    void handleContainerSetSlot(ClientboundContainerSetSlotPacket packet);

    /**
     * Invokes the entities' handleUpdateHealth method which is implemented in LivingBase (hurt/death), MinecartMobSpawner (spawn delay), FireworkRocket & MinecartTNT (explosion), IronGolem (throwing,...), Witch (spawn particles), Zombie (villager transformation), Animal (breeding mode particles), Horse (breeding/smoke particles), Sheep (...), Tameable (...), Villager (particles for breeding mode, angry and happy), Wolf (...)
     */
    void handleEntityEvent(ClientboundEntityEventPacket packet);

    void handleEntityLinkPacket(ClientboundSetEntityLinkPacket packet);

    void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket packet);

    /**
     * Initiates a new explosion (sound, particles, drop spawn) for the affected blocks indicated by the packet.
     */
    void handleExplosion(ClientboundExplodePacket packet);

    void handleGameEvent(ClientboundGameEventPacket packet);

    void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet);

    void handleChunksBiomes(ClientboundChunksBiomesPacket packet);

    void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet);

    void handleLevelEvent(ClientboundLevelEventPacket packet);

    /**
     * Registers some server properties (gametype,hardcore-mode,terraintype,difficulty,player limit), creates a new WorldClient and sets the player initial dimension
     */
    void handleLogin(ClientboundLoginPacket packet);

    /**
     * Updates the specified entity's position by the specified relative moment and absolute rotation. Note that subclassing of the packet allows for the specification of a subset of this data (e.g. only rel. position, abs. rotation or both).
     */
    void handleMoveEntity(ClientboundMoveEntityPacket packet);

    void handleMovePlayer(ClientboundPlayerPositionPacket packet);

    /**
     * Spawns a specified number of particles at the specified location with a randomized displacement according to specified bounds
     */
    void handleParticleEvent(ClientboundLevelParticlesPacket packet);

    void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket packet);

    void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet);

    void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet);

    void handleRemoveEntities(ClientboundRemoveEntitiesPacket packet);

    void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket packet);

    void handleRespawn(ClientboundRespawnPacket packet);

    /**
     * Updates the direction in which the specified entity is looking, normally this head rotation is independent of the rotation of the entity itself
     */
    void handleRotateMob(ClientboundRotateHeadPacket packet);

    /**
     * Updates which hotbar slot of the player is currently selected
     */
    void handleSetCarriedItem(ClientboundSetCarriedItemPacket packet);

    /**
     * Removes or sets the ScoreObjective to be displayed at a particular scoreboard position (list, sidebar, below name)
     */
    void handleSetDisplayObjective(ClientboundSetDisplayObjectivePacket packet);

    /**
     * Invoked when the server registers new proximate objects in your watchlist or when objects in your watchlist have changed -> Registers any changes locally
     */
    void handleSetEntityData(ClientboundSetEntityDataPacket packet);

    /**
     * Sets the velocity of the specified entity to the specified value
     */
    void handleSetEntityMotion(ClientboundSetEntityMotionPacket packet);

    void handleSetEquipment(ClientboundSetEquipmentPacket packet);

    void handleSetExperience(ClientboundSetExperiencePacket packet);

    void handleSetHealth(ClientboundSetHealthPacket packet);

    /**
     * Updates a team managed by the scoreboard: Create/Remove the team registration, Register/Remove the player-team-memberships, Set team displayname/prefix/suffix and/or whether friendly fire is enabled
     */
    void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket packet);

    /**
     * Either updates the score with a specified value or removes the score for an objective
     */
    void handleSetScore(ClientboundSetScorePacket packet);

    void handleResetScore(ClientboundResetScorePacket packet);

    void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket packet);

    void handleSetTime(ClientboundSetTimePacket packet);

    void handleSoundEvent(ClientboundSoundPacket packet);

    void handleSoundEntityEvent(ClientboundSoundEntityPacket packet);

    void handleTakeItemEntity(ClientboundTakeItemEntityPacket packet);

    /**
     * Updates an entity's position and rotation as specified by the packet
     */
    void handleTeleportEntity(ClientboundTeleportEntityPacket packet);

    void handleTickingState(ClientboundTickingStatePacket packet);

    void handleTickingStep(ClientboundTickingStepPacket packet);

    /**
     * Updates en entity's attributes and their respective modifiers, which are used for speed bonuses (player sprinting, animals fleeing, baby speed), weapon/tool attackDamage, hostiles followRange randomization, zombie maxHealth and knockback resistance as well as reinforcement spawning chance.
     */
    void handleUpdateAttributes(ClientboundUpdateAttributesPacket packet);

    void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket packet);

    void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket packet);

    void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket packet);

    void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket packet);

    void handleChangeDifficulty(ClientboundChangeDifficultyPacket packet);

    void handleSetCamera(ClientboundSetCameraPacket packet);

    void handleInitializeBorder(ClientboundInitializeBorderPacket packet);

    void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket packet);

    void handleSetBorderSize(ClientboundSetBorderSizePacket packet);

    void handleSetBorderWarningDelay(ClientboundSetBorderWarningDelayPacket packet);

    void handleSetBorderWarningDistance(ClientboundSetBorderWarningDistancePacket packet);

    void handleSetBorderCenter(ClientboundSetBorderCenterPacket packet);

    void handleTabListCustomisation(ClientboundTabListPacket packet);

    void handleBossUpdate(ClientboundBossEventPacket packet);

    void handleItemCooldown(ClientboundCooldownPacket packet);

    void handleMoveVehicle(ClientboundMoveVehiclePacket packet);

    void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket packet);

    void handleSelectAdvancementsTab(ClientboundSelectAdvancementsTabPacket packet);

    void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket packet);

    void handleCommands(ClientboundCommandsPacket packet);

    void handleStopSoundEvent(ClientboundStopSoundPacket packet);

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    void handleCommandSuggestions(ClientboundCommandSuggestionsPacket packet);

    void handleUpdateRecipes(ClientboundUpdateRecipesPacket packet);

    void handleLookAt(ClientboundPlayerLookAtPacket packet);

    void handleTagQueryPacket(ClientboundTagQueryPacket packet);

    void handleLightUpdatePacket(ClientboundLightUpdatePacket packet);

    void handleOpenBook(ClientboundOpenBookPacket packet);

    void handleOpenScreen(ClientboundOpenScreenPacket packet);

    void handleMerchantOffers(ClientboundMerchantOffersPacket packet);

    void handleSetChunkCacheRadius(ClientboundSetChunkCacheRadiusPacket packet);

    void handleSetSimulationDistance(ClientboundSetSimulationDistancePacket packet);

    void handleSetChunkCacheCenter(ClientboundSetChunkCacheCenterPacket packet);

    void handleBlockChangedAck(ClientboundBlockChangedAckPacket packet);

    void setActionBarText(ClientboundSetActionBarTextPacket packet);

    void setSubtitleText(ClientboundSetSubtitleTextPacket packet);

    void setTitleText(ClientboundSetTitleTextPacket packet);

    void setTitlesAnimation(ClientboundSetTitlesAnimationPacket packet);

    void handleTitlesClear(ClientboundClearTitlesPacket packet);

    void handleServerData(ClientboundServerDataPacket packet);

    void handleCustomChatCompletions(ClientboundCustomChatCompletionsPacket packet);

    void handleBundlePacket(ClientboundBundlePacket packet);

    void handleDamageEvent(ClientboundDamageEventPacket packet);

    void handleConfigurationStart(ClientboundStartConfigurationPacket packet);

    void handleChunkBatchStart(ClientboundChunkBatchStartPacket packet);

    void handleChunkBatchFinished(ClientboundChunkBatchFinishedPacket packet);

    void handleDebugSample(ClientboundDebugSamplePacket packet);

    void handleProjectilePowerPacket(ClientboundProjectilePowerPacket packet);
}
