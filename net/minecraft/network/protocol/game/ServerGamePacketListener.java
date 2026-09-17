package net.minecraft.network.protocol.game;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.ping.ServerPingPacketListener;

/**
 * PacketListener for the server side of the PLAY protocol.
 */
public interface ServerGamePacketListener extends ServerPingPacketListener, ServerCommonPacketListener, net.neoforged.neoforge.common.extensions.IServerGamePacketListenerExtension {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    void handleAnimate(ServerboundSwingPacket packet);

    /**
     * Process chat messages (broadcast back to clients) and commands (executes)
     */
    void handleChat(ServerboundChatPacket packet);

    void handleChatCommand(ServerboundChatCommandPacket packet);

    void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet);

    void handleChatAck(ServerboundChatAckPacket packet);

    /**
     * Processes the client status updates: respawn attempt from player, opening statistics or achievements, or acquiring 'open inventory' achievement
     */
    void handleClientCommand(ServerboundClientCommandPacket packet);

    /**
     * Enchants the item identified by the packet given some convoluted conditions (matching window, which should/shouldn't be in use?)
     */
    void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet);

    /**
     * Executes a container/inventory slot manipulation as indicated by the packet. Sends the serverside result if they didn't match the indicated result and prevents further manipulation by the player until he confirms that it has the same open container/inventory
     */
    void handleContainerClick(ServerboundContainerClickPacket packet);

    void handlePlaceRecipe(ServerboundPlaceRecipePacket packet);

    /**
     * Processes the client closing windows (container)
     */
    void handleContainerClose(ServerboundContainerClosePacket packet);

    /**
     * Processes left and right clicks on entities
     */
    void handleInteract(ServerboundInteractPacket packet);

    /**
     * Processes clients perspective on player positioning and/or orientation
     */
    void handleMovePlayer(ServerboundMovePlayerPacket packet);

    /**
     * Processes a player starting/stopping flying
     */
    void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet);

    /**
     * Processes the player initiating/stopping digging on a particular spot, as well as a player dropping items
     */
    void handlePlayerAction(ServerboundPlayerActionPacket packet);

    /**
     * Processes a range of action-types: sneaking, sprinting, waking from sleep, opening the inventory or setting jump height of the horse the player is riding
     */
    void handlePlayerCommand(ServerboundPlayerCommandPacket packet);

    /**
     * Processes player movement input. Includes walking, strafing, jumping, and sneaking. Excludes riding and toggling flying/sprinting.
     */
    void handlePlayerInput(ServerboundPlayerInputPacket packet);

    /**
     * Updates which quickbar slot is selected
     */
    void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet);

    /**
     * Update the server with an ItemStack in a slot.
     */
    void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet);

    void handleSignUpdate(ServerboundSignUpdatePacket packet);

    void handleUseItemOn(ServerboundUseItemOnPacket packet);

    /**
     * Called when a client is using an item while not pointing at a block, but simply using an item
     */
    void handleUseItem(ServerboundUseItemPacket packet);

    void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet);

    void handlePaddleBoat(ServerboundPaddleBoatPacket packet);

    void handleMoveVehicle(ServerboundMoveVehiclePacket packet);

    void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet);

    void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet);

    void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet);

    void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet);

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet);

    void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet);

    void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet);

    void handlePickItem(ServerboundPickItemPacket packet);

    void handleRenameItem(ServerboundRenameItemPacket packet);

    void handleSetBeaconPacket(ServerboundSetBeaconPacket packet);

    void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet);

    void handleSelectTrade(ServerboundSelectTradePacket packet);

    void handleEditBook(ServerboundEditBookPacket packet);

    void handleEntityTagQuery(ServerboundEntityTagQueryPacket packet);

    void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket packet);

    void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket packet);

    void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet);

    void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet);

    void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet);

    void handleLockDifficulty(ServerboundLockDifficultyPacket packet);

    void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet);

    void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet);

    void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet);

    void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket packet);
}
