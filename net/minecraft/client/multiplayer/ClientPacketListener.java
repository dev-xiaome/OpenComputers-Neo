package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.time.Instant;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.DebugQueryHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DemoIntroScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.debug.BrainDebugRenderer;
import net.minecraft.client.renderer.debug.NeighborsUpdateRenderer;
import net.minecraft.client.renderer.debug.VillageSectionsDebugRenderer;
import net.minecraft.client.renderer.debug.WorldGenAttemptRenderer;
import net.minecraft.client.resources.sounds.BeeAggressiveSoundInstance;
import net.minecraft.client.resources.sounds.BeeFlyingSoundInstance;
import net.minecraft.client.resources.sounds.BeeSoundInstance;
import net.minecraft.client.resources.sounds.GuardianAttackSoundInstance;
import net.minecraft.client.resources.sounds.MinecartSoundInstance;
import net.minecraft.client.resources.sounds.SnifferSoundInstance;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.chat.SignedMessageLink;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.custom.BeeDebugPayload;
import net.minecraft.network.protocol.common.custom.BrainDebugPayload;
import net.minecraft.network.protocol.common.custom.BreezeDebugPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.GameEventDebugPayload;
import net.minecraft.network.protocol.common.custom.GameEventListenerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestAddMarkerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestClearMarkersDebugPayload;
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;
import net.minecraft.network.protocol.common.custom.HiveDebugPayload;
import net.minecraft.network.protocol.common.custom.NeighborUpdatesDebugPayload;
import net.minecraft.network.protocol.common.custom.PathfindingDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiAddedDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiRemovedDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiTicketCountDebugPayload;
import net.minecraft.network.protocol.common.custom.RaidsDebugPayload;
import net.minecraft.network.protocol.common.custom.StructuresDebugPayload;
import net.minecraft.network.protocol.common.custom.VillageSectionsDebugPayload;
import net.minecraft.network.protocol.common.custom.WorldGenAttemptDebugPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerLinks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Crypt;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientPacketListener extends ClientCommonPacketListenerImpl implements ClientGamePacketListener, TickablePacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component UNSECURE_SERVER_TOAST_TITLE = Component.translatable("multiplayer.unsecureserver.toast.title");
    private static final Component UNSERURE_SERVER_TOAST = Component.translatable("multiplayer.unsecureserver.toast");
    private static final Component INVALID_PACKET = Component.translatable("multiplayer.disconnect.invalid_packet");
    private static final Component CHAT_VALIDATION_FAILED_ERROR = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final Component RECONFIGURE_SCREEN_MESSAGE = Component.translatable("connect.reconfiguring");
    private static final int PENDING_OFFSET_THRESHOLD = 64;
    private final GameProfile localGameProfile;
    /**
     * Reference to the current ClientWorld instance, which many handler methods operate on
     */
    private ClientLevel level;
    private ClientLevel.ClientLevelData levelData;
    /**
     * A mapping from player names to their respective GuiPlayerInfo (specifies the clients response time to the server)
     */
    private final Map<UUID, PlayerInfo> playerInfoMap = Maps.newHashMap();
    private final Set<PlayerInfo> listedPlayers = new ReferenceOpenHashSet<>();
    private final ClientAdvancements advancements;
    private final ClientSuggestionProvider suggestionsProvider;
    private final DebugQueryHandler debugQueryHandler = new DebugQueryHandler(this);
    private int serverChunkRadius = 3;
    private int serverSimulationDistance = 3;
    /**
     * Just an ordinary random number generator, used to randomize audio pitch of item/orb pickup and randomize both particlespawn offset and velocity
     */
    private final RandomSource random = RandomSource.createThreadSafe();
    public CommandDispatcher<SharedSuggestionProvider> commands = new CommandDispatcher<>();
    private final RecipeManager recipeManager;
    private final UUID id = UUID.randomUUID();
    private Set<ResourceKey<Level>> levels;
    private final RegistryAccess.Frozen registryAccess;
    private final FeatureFlagSet enabledFeatures;
    private final PotionBrewing potionBrewing;
    @Nullable
    private LocalChatSession chatSession;
    private SignedMessageChain.Encoder signedMessageEncoder = SignedMessageChain.Encoder.UNSIGNED;
    private LastSeenMessagesTracker lastSeenMessages = new LastSeenMessagesTracker(20);
    private MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    private final ChunkBatchSizeCalculator chunkBatchSizeCalculator = new ChunkBatchSizeCalculator();
    private final PingDebugMonitor pingDebugMonitor;
    private final DebugSampleSubscriber debugSampleSubscriber;
    private net.neoforged.neoforge.network.connection.ConnectionType connectionType;
    @Nullable
    private LevelLoadStatusManager levelLoadStatusManager;
    private boolean serverEnforcesSecureChat;
    private boolean seenInsecureChatWarning = false;
    private volatile boolean closed;
    private final Scoreboard scoreboard = new Scoreboard();
    private final SessionSearchTrees searchTrees = new SessionSearchTrees();

    public ClientPacketListener(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
        this.localGameProfile = commonListenerCookie.localGameProfile();
        this.registryAccess = commonListenerCookie.receivedRegistries();
        this.enabledFeatures = commonListenerCookie.enabledFeatures();
        this.advancements = new ClientAdvancements(minecraft, this.telemetryManager);
        this.suggestionsProvider = new ClientSuggestionProvider(this, minecraft);
        this.pingDebugMonitor = new PingDebugMonitor(this, minecraft.getDebugOverlay().getPingLogger());
        this.recipeManager = new RecipeManager(this.registryAccess);
        this.debugSampleSubscriber = new DebugSampleSubscriber(this, minecraft.getDebugOverlay());
        if (commonListenerCookie.chatState() != null) {
            minecraft.gui.getChat().restoreState(commonListenerCookie.chatState());
        }

        this.connectionType = commonListenerCookie.connectionType();
        this.potionBrewing = PotionBrewing.bootstrap(this.enabledFeatures, this.registryAccess);
    }

    public ClientSuggestionProvider getSuggestionsProvider() {
        return this.suggestionsProvider;
    }

    public void close() {
        this.closed = true;
        this.clearLevel();
        this.telemetryManager.onDisconnect();
    }

    public void clearLevel() {
        this.level = null;
        this.levelLoadStatusManager = null;
    }

    public RecipeManager getRecipeManager() {
        return this.recipeManager;
    }

    /**
     * Registers some server properties (gametype, hardcore-mode, terraintype, difficulty, player limit), creates a new WorldClient and sets the player initial dimension.
     */
    @Override
    public void handleLogin(ClientboundLoginPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gameMode = new MultiPlayerGameMode(this.minecraft, this);
        CommonPlayerSpawnInfo commonplayerspawninfo = packet.commonPlayerSpawnInfo();
        List<ResourceKey<Level>> list = Lists.newArrayList(packet.levels());
        Collections.shuffle(list);
        this.levels = Sets.newLinkedHashSet(list);
        ResourceKey<Level> resourcekey = commonplayerspawninfo.dimension();
        Holder<DimensionType> holder = commonplayerspawninfo.dimensionType();
        this.serverChunkRadius = packet.chunkRadius();
        this.serverSimulationDistance = packet.simulationDistance();
        boolean flag = commonplayerspawninfo.isDebug();
        boolean flag1 = commonplayerspawninfo.isFlat();
        ClientLevel.ClientLevelData clientlevel$clientleveldata = new ClientLevel.ClientLevelData(Difficulty.NORMAL, packet.hardcore(), flag1);
        this.levelData = clientlevel$clientleveldata;
        this.level = new ClientLevel(
            this,
            clientlevel$clientleveldata,
            resourcekey,
            holder,
            this.serverChunkRadius,
            this.serverSimulationDistance,
            this.minecraft::getProfiler,
            this.minecraft.levelRenderer,
            flag,
            commonplayerspawninfo.seed()
        );
        this.minecraft.setLevel(this.level, ReceivingLevelScreen.Reason.OTHER);
        if (this.minecraft.player == null) {
            this.minecraft.player = this.minecraft.gameMode.createPlayer(this.level, new StatsCounter(), new ClientRecipeBook());
            this.minecraft.player.setYRot(-180.0F);
            if (this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().setUUID(this.minecraft.player.getUUID());
            }
        }

        this.minecraft.debugRenderer.clear();
        this.minecraft.player.resetPos();
        net.neoforged.neoforge.client.ClientHooks.firePlayerLogin(this.minecraft.gameMode, this.minecraft.player, this.minecraft.getConnection().connection);
        this.minecraft.player.setId(packet.playerId());
        this.level.addEntity(this.minecraft.player);
        this.minecraft.player.input = new KeyboardInput(this.minecraft.options);
        this.minecraft.gameMode.adjustPlayer(this.minecraft.player);
        this.minecraft.cameraEntity = this.minecraft.player;
        this.startWaitingForNewLevel(this.minecraft.player, this.level, ReceivingLevelScreen.Reason.OTHER, null, null);
        this.minecraft.player.setReducedDebugInfo(packet.reducedDebugInfo());
        this.minecraft.player.setShowDeathScreen(packet.showDeathScreen());
        this.minecraft.player.setDoLimitedCrafting(packet.doLimitedCrafting());
        this.minecraft.player.setLastDeathLocation(commonplayerspawninfo.lastDeathLocation());
        this.minecraft.player.setPortalCooldown(commonplayerspawninfo.portalCooldown());
        this.minecraft.gameMode.setLocalMode(commonplayerspawninfo.gameType(), commonplayerspawninfo.previousGameType());
        this.minecraft.options.setServerRenderDistance(packet.chunkRadius());
        this.chatSession = null;
        this.lastSeenMessages = new LastSeenMessagesTracker(20);
        this.messageSignatureCache = MessageSignatureCache.createDefault();
        if (this.connection.isEncrypted()) {
            this.minecraft.getProfileKeyPairManager().prepareKeyPair().thenAcceptAsync(p_253341_ -> p_253341_.ifPresent(this::setKeyPair), this.minecraft);
        }

        this.telemetryManager.onPlayerInfoReceived(commonplayerspawninfo.gameType(), packet.hardcore());
        this.minecraft.quickPlayLog().log(this.minecraft);
        this.serverEnforcesSecureChat = packet.enforcesSecureChat();
        if (this.serverData != null && !this.seenInsecureChatWarning && !this.enforcesSecureChat()) {
            SystemToast systemtoast = SystemToast.multiline(
                this.minecraft, SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, UNSECURE_SERVER_TOAST_TITLE, UNSERURE_SERVER_TOAST
            );
            this.minecraft.getToasts().addToast(systemtoast);
            this.seenInsecureChatWarning = true;
        }
    }

    /**
     * Spawns an instance of the objecttype indicated by the packet and sets its position and momentum
     */
    @Override
    public void handleAddEntity(ClientboundAddEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.createEntityFromPacket(packet);
        if (entity != null) {
            entity.recreateFromPacket(packet);
            this.level.addEntity(entity);
            this.postAddEntitySoundInstance(entity);
        } else {
            LOGGER.warn("Skipping Entity with id {}", packet.getType());
        }
    }

    @Nullable
    private Entity createEntityFromPacket(ClientboundAddEntityPacket packet) {
        EntityType<?> entitytype = packet.getType();
        if (entitytype == EntityType.PLAYER) {
            PlayerInfo playerinfo = this.getPlayerInfo(packet.getUUID());
            if (playerinfo == null) {
                LOGGER.warn("Server attempted to add player prior to sending player info (Player id {})", packet.getUUID());
                return null;
            } else {
                return new RemotePlayer(this.level, playerinfo.getProfile());
            }
        } else {
            return entitytype.create(this.level);
        }
    }

    private void postAddEntitySoundInstance(Entity entity) {
        if (entity instanceof AbstractMinecart abstractminecart) {
            this.minecraft.getSoundManager().play(new MinecartSoundInstance(abstractminecart));
        } else if (entity instanceof Bee bee) {
            boolean flag = bee.isAngry();
            BeeSoundInstance beesoundinstance;
            if (flag) {
                beesoundinstance = new BeeAggressiveSoundInstance(bee);
            } else {
                beesoundinstance = new BeeFlyingSoundInstance(bee);
            }

            this.minecraft.getSoundManager().queueTickingSound(beesoundinstance);
        }
    }

    /**
     * Spawns an experience orb and sets its value (amount of XP)
     */
    @Override
    public void handleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        Entity entity = new ExperienceOrb(this.level, d0, d1, d2, packet.getValue());
        entity.syncPacketPositionCodec(d0, d1, d2);
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        entity.setId(packet.getId());
        this.level.addEntity(entity);
    }

    /**
     * Sets the velocity of the specified entity to the specified value
     */
    @Override
    public void handleSetEntityMotion(ClientboundSetEntityMotionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            entity.lerpMotion(packet.getXa(), packet.getYa(), packet.getZa());
        }
    }

    /**
     * Invoked when the server registers new proximate objects in your watchlist or when objects in your watchlist have changed -> Registers any changes locally
     */
    @Override
    public void handleSetEntityData(ClientboundSetEntityDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.id());
        if (entity != null) {
            entity.getEntityData().assignValues(packet.packedItems());
        }
    }

    /**
     * Updates an entity's position and rotation as specified by the packet
     */
    @Override
    public void handleTeleportEntity(ClientboundTeleportEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            double d0 = packet.getX();
            double d1 = packet.getY();
            double d2 = packet.getZ();
            entity.syncPacketPositionCodec(d0, d1, d2);
            if (!entity.isControlledByLocalInstance()) {
                float f = (float)(packet.getyRot() * 360) / 256.0F;
                float f1 = (float)(packet.getxRot() * 360) / 256.0F;
                entity.lerpTo(d0, d1, d2, f, f1, 3);
                entity.setOnGround(packet.isOnGround());
            }
        }
    }

    @Override
    public void handleTickingState(ClientboundTickingStatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.minecraft.level != null) {
            TickRateManager tickratemanager = this.minecraft.level.tickRateManager();
            tickratemanager.setTickRate(packet.tickRate());
            tickratemanager.setFrozen(packet.isFrozen());
        }
    }

    @Override
    public void handleTickingStep(ClientboundTickingStepPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.minecraft.level != null) {
            TickRateManager tickratemanager = this.minecraft.level.tickRateManager();
            tickratemanager.setFrozenTicksToRun(packet.tickSteps());
        }
    }

    /**
     * Updates which hotbar slot of the player is currently selected
     */
    @Override
    public void handleSetCarriedItem(ClientboundSetCarriedItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (Inventory.isHotbarSlot(packet.getSlot())) {
            this.minecraft.player.getInventory().selected = packet.getSlot();
        }
    }

    /**
     * Updates the specified entity's position by the specified relative momentum and absolute rotation. Note that subclassing of the packet allows for the specification of a subset of this data (e.g. only rel. position, abs. rotation or both).
     */
    @Override
    public void handleMoveEntity(ClientboundMoveEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            if (!entity.isControlledByLocalInstance()) {
                if (packet.hasPosition()) {
                    VecDeltaCodec vecdeltacodec = entity.getPositionCodec();
                    Vec3 vec3 = vecdeltacodec.decode((long)packet.getXa(), (long)packet.getYa(), (long)packet.getZa());
                    vecdeltacodec.setBase(vec3);
                    float f = packet.hasRotation() ? (float)(packet.getyRot() * 360) / 256.0F : entity.lerpTargetYRot();
                    float f1 = packet.hasRotation() ? (float)(packet.getxRot() * 360) / 256.0F : entity.lerpTargetXRot();
                    entity.lerpTo(vec3.x(), vec3.y(), vec3.z(), f, f1, 3);
                } else if (packet.hasRotation()) {
                    float f2 = (float)(packet.getyRot() * 360) / 256.0F;
                    float f3 = (float)(packet.getxRot() * 360) / 256.0F;
                    entity.lerpTo(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(), f2, f3, 3);
                }

                entity.setOnGround(packet.isOnGround());
            }
        }
    }

    /**
     * Updates the direction in which the specified entity is looking, normally this head rotation is independent of the rotation of the entity itself
     */
    @Override
    public void handleRotateMob(ClientboundRotateHeadPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            float f = (float)(packet.getYHeadRot() * 360) / 256.0F;
            entity.lerpHeadTo(f, 3);
        }
    }

    @Override
    public void handleRemoveEntities(ClientboundRemoveEntitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        packet.getEntityIds().forEach(p_205521_ -> this.level.removeEntity(p_205521_, Entity.RemovalReason.DISCARDED));
    }

    @Override
    public void handleMovePlayer(ClientboundPlayerPositionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        Vec3 vec3 = player.getDeltaMovement();
        boolean flag = packet.getRelativeArguments().contains(RelativeMovement.X);
        boolean flag1 = packet.getRelativeArguments().contains(RelativeMovement.Y);
        boolean flag2 = packet.getRelativeArguments().contains(RelativeMovement.Z);
        double d0;
        double d1;
        if (flag) {
            d0 = vec3.x();
            d1 = player.getX() + packet.getX();
            player.xOld = player.xOld + packet.getX();
            player.xo = player.xo + packet.getX();
        } else {
            d0 = 0.0;
            d1 = packet.getX();
            player.xOld = d1;
            player.xo = d1;
        }

        double d2;
        double d3;
        if (flag1) {
            d2 = vec3.y();
            d3 = player.getY() + packet.getY();
            player.yOld = player.yOld + packet.getY();
            player.yo = player.yo + packet.getY();
        } else {
            d2 = 0.0;
            d3 = packet.getY();
            player.yOld = d3;
            player.yo = d3;
        }

        double d4;
        double d5;
        if (flag2) {
            d4 = vec3.z();
            d5 = player.getZ() + packet.getZ();
            player.zOld = player.zOld + packet.getZ();
            player.zo = player.zo + packet.getZ();
        } else {
            d4 = 0.0;
            d5 = packet.getZ();
            player.zOld = d5;
            player.zo = d5;
        }

        player.setPos(d1, d3, d5);
        player.setDeltaMovement(d0, d2, d4);
        float f = packet.getYRot();
        float f1 = packet.getXRot();
        if (packet.getRelativeArguments().contains(RelativeMovement.X_ROT)) {
            player.setXRot(player.getXRot() + f1);
            player.xRotO += f1;
        } else {
            player.setXRot(f1);
            player.xRotO = f1;
        }

        if (packet.getRelativeArguments().contains(RelativeMovement.Y_ROT)) {
            player.setYRot(player.getYRot() + f);
            player.yRotO += f;
        } else {
            player.setYRot(f);
            player.yRotO = f;
        }

        this.connection.send(new ServerboundAcceptTeleportationPacket(packet.getId()));
        this.connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), false));
    }

    /**
     * Received from the servers PlayerManager if between 1 and 64 blocks in a chunk are changed. If only one block requires an update, the server sends S23PacketBlockChange and if 64 or more blocks are changed, the server sends S21PacketChunkData
     */
    @Override
    public void handleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        packet.runUpdates((p_284633_, p_284634_) -> this.level.setServerVerifiedBlockState(p_284633_, p_284634_, 19));
    }

    @Override
    public void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        int i = packet.getX();
        int j = packet.getZ();
        this.updateLevelChunk(i, j, packet.getChunkData());
        ClientboundLightUpdatePacketData clientboundlightupdatepacketdata = packet.getLightData();
        this.level.queueLightUpdate(() -> {
            this.applyLightData(i, j, clientboundlightupdatepacketdata);
            LevelChunk levelchunk = this.level.getChunkSource().getChunk(i, j, false);
            if (levelchunk != null) {
                this.enableChunkLight(levelchunk, i, j);
            }
        });
    }

    @Override
    public void handleChunksBiomes(ClientboundChunksBiomesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (ClientboundChunksBiomesPacket.ChunkBiomeData clientboundchunksbiomespacket$chunkbiomedata : packet.chunkBiomeData()) {
            this.level
                .getChunkSource()
                .replaceBiomes(
                    clientboundchunksbiomespacket$chunkbiomedata.pos().x,
                    clientboundchunksbiomespacket$chunkbiomedata.pos().z,
                    clientboundchunksbiomespacket$chunkbiomedata.getReadBuffer()
                );
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData clientboundchunksbiomespacket$chunkbiomedata1 : packet.chunkBiomeData()) {
            this.level
                .onChunkLoaded(new ChunkPos(clientboundchunksbiomespacket$chunkbiomedata1.pos().x, clientboundchunksbiomespacket$chunkbiomedata1.pos().z));
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData clientboundchunksbiomespacket$chunkbiomedata2 : packet.chunkBiomeData()) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = this.level.getMinSection(); k < this.level.getMaxSection(); k++) {
                        this.minecraft
                            .levelRenderer
                            .setSectionDirty(
                                clientboundchunksbiomespacket$chunkbiomedata2.pos().x + i, k, clientboundchunksbiomespacket$chunkbiomedata2.pos().z + j
                            );
                    }
                }
            }
        }
    }

    private void updateLevelChunk(int x, int z, ClientboundLevelChunkPacketData data) {
        this.level
            .getChunkSource()
            .replaceWithPacketData(
                x, z, data.getReadBuffer(), data.getHeightmaps(), data.getBlockEntitiesTagsConsumer(x, z)
            );
    }

    private void enableChunkLight(LevelChunk chunk, int x, int z) {
        LevelLightEngine levellightengine = this.level.getChunkSource().getLightEngine();
        LevelChunkSection[] alevelchunksection = chunk.getSections();
        ChunkPos chunkpos = chunk.getPos();

        for (int i = 0; i < alevelchunksection.length; i++) {
            LevelChunkSection levelchunksection = alevelchunksection[i];
            int j = this.level.getSectionYFromSectionIndex(i);
            levellightengine.updateSectionStatus(SectionPos.of(chunkpos, j), levelchunksection.hasOnlyAir());
            this.level.setSectionDirtyWithNeighbors(x, j, z);
        }
    }

    @Override
    public void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getChunkSource().drop(packet.pos());
        this.queueLightRemoval(packet);
    }

    private void queueLightRemoval(ClientboundForgetLevelChunkPacket packet) {
        ChunkPos chunkpos = packet.pos();
        this.level.queueLightUpdate(() -> {
            LevelLightEngine levellightengine = this.level.getLightEngine();
            levellightengine.setLightEnabled(chunkpos, false);

            for (int i = levellightengine.getMinLightSection(); i < levellightengine.getMaxLightSection(); i++) {
                SectionPos sectionpos = SectionPos.of(chunkpos, i);
                levellightengine.queueSectionData(LightLayer.BLOCK, sectionpos, null);
                levellightengine.queueSectionData(LightLayer.SKY, sectionpos, null);
            }

            for (int j = this.level.getMinSection(); j < this.level.getMaxSection(); j++) {
                levellightengine.updateSectionStatus(SectionPos.of(chunkpos, j), true);
            }
        });
    }

    /**
     * Updates the block and metadata and generates a blockupdate (and notify the clients)
     */
    @Override
    public void handleBlockUpdate(ClientboundBlockUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.setServerVerifiedBlockState(packet.getPos(), packet.getBlockState(), 19);
    }

    @Override
    public void handleConfigurationStart(ClientboundStartConfigurationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().clearQueue();
        this.sendChatAcknowledgement();
        ChatComponent.State chatcomponent$state = this.minecraft.gui.getChat().storeState();
        this.minecraft.clearClientLevel(new ServerReconfigScreen(RECONFIGURE_SCREEN_MESSAGE, this.connection));
        this.connection
            .setupInboundProtocol(
                ConfigurationProtocols.CLIENTBOUND,
                new ClientConfigurationPacketListenerImpl(
                    this.minecraft,
                    this.connection,
                    new CommonListenerCookie(
                        this.localGameProfile,
                        this.telemetryManager,
                        this.registryAccess,
                        this.enabledFeatures,
                        this.serverBrand,
                        this.serverData,
                        this.postDisconnectScreen,
                        this.serverCookies,
                        chatcomponent$state,
                        this.strictErrorHandling,
                        this.customReportDetails,
                        this.serverLinks,
                        this.connectionType
                    )
                )
            );
        this.send(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
    }

    @Override
    public void handleTakeItemEntity(ClientboundTakeItemEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getItemId());
        LivingEntity livingentity = (LivingEntity)this.level.getEntity(packet.getPlayerId());
        if (livingentity == null) {
            livingentity = this.minecraft.player;
        }

        if (entity != null) {
            if (entity instanceof ExperienceOrb) {
                this.level
                    .playLocalSound(
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS,
                        0.1F,
                        (this.random.nextFloat() - this.random.nextFloat()) * 0.35F + 0.9F,
                        false
                    );
            } else {
                this.level
                    .playLocalSound(
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS,
                        0.2F,
                        (this.random.nextFloat() - this.random.nextFloat()) * 1.4F + 2.0F,
                        false
                    );
            }

            this.minecraft
                .particleEngine
                .add(new ItemPickupParticle(this.minecraft.getEntityRenderDispatcher(), this.minecraft.renderBuffers(), this.level, entity, livingentity));
            if (entity instanceof ItemEntity itementity) {
                ItemStack itemstack = itementity.getItem();
                if (!itemstack.isEmpty()) {
                    itemstack.shrink(packet.getAmount());
                }

                if (itemstack.isEmpty()) {
                    this.level.removeEntity(packet.getItemId(), Entity.RemovalReason.DISCARDED);
                }
            } else if (!(entity instanceof ExperienceOrb)) {
                this.level.removeEntity(packet.getItemId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }

    @Override
    public void handleSystemChat(ClientboundSystemChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().handleSystemMessage(packet.content(), packet.overlay());
    }

    @Override
    public void handlePlayerChat(ClientboundPlayerChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Optional<SignedMessageBody> optional = packet.body().unpack(this.messageSignatureCache);
        if (optional.isEmpty()) {
            this.connection.disconnect(INVALID_PACKET);
        } else {
            this.messageSignatureCache.push(optional.get(), packet.signature());
            UUID uuid = packet.sender();
            PlayerInfo playerinfo = this.getPlayerInfo(uuid);
            if (playerinfo == null) {
                LOGGER.error("Received player chat packet for unknown player with ID: {}", uuid);
                this.minecraft.getChatListener().handleChatMessageError(uuid, packet.chatType());
            } else {
                RemoteChatSession remotechatsession = playerinfo.getChatSession();
                SignedMessageLink signedmessagelink;
                if (remotechatsession != null) {
                    signedmessagelink = new SignedMessageLink(packet.index(), uuid, remotechatsession.sessionId());
                } else {
                    signedmessagelink = SignedMessageLink.unsigned(uuid);
                }

                PlayerChatMessage playerchatmessage = new PlayerChatMessage(
                    signedmessagelink, packet.signature(), optional.get(), packet.unsignedContent(), packet.filterMask()
                );
                playerchatmessage = playerinfo.getMessageValidator().updateAndValidate(playerchatmessage);
                if (playerchatmessage != null) {
                    this.minecraft.getChatListener().handlePlayerChatMessage(playerchatmessage, playerinfo.getProfile(), packet.chatType());
                } else {
                    this.minecraft.getChatListener().handleChatMessageError(uuid, packet.chatType());
                }
            }
        }
    }

    @Override
    public void handleDisguisedChat(ClientboundDisguisedChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().handleDisguisedChatMessage(packet.message(), packet.chatType());
    }

    @Override
    public void handleDeleteChat(ClientboundDeleteChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Optional<MessageSignature> optional = packet.messageSignature().unpack(this.messageSignatureCache);
        if (optional.isEmpty()) {
            this.connection.disconnect(INVALID_PACKET);
        } else {
            this.lastSeenMessages.ignorePending(optional.get());
            if (!this.minecraft.getChatListener().removeFromDelayedMessageQueue(optional.get())) {
                this.minecraft.gui.getChat().deleteMessage(optional.get());
            }
        }
    }

    /**
     * Renders a specified animation: Waking up a player, a living entity swinging its currently held item, being hurt or receiving a critical hit by normal or magical means
     */
    @Override
    public void handleAnimate(ClientboundAnimatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            if (packet.getAction() == 0) {
                LivingEntity livingentity = (LivingEntity)entity;
                livingentity.swing(InteractionHand.MAIN_HAND);
            } else if (packet.getAction() == 3) {
                LivingEntity livingentity1 = (LivingEntity)entity;
                livingentity1.swing(InteractionHand.OFF_HAND);
            } else if (packet.getAction() == 2) {
                Player player = (Player)entity;
                player.stopSleepInBed(false, false);
            } else if (packet.getAction() == 4) {
                this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
            } else if (packet.getAction() == 5) {
                this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    @Override
    public void handleHurtAnimation(ClientboundHurtAnimationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.id());
        if (entity != null) {
            entity.animateHurt(packet.yaw());
        }
    }

    @Override
    public void handleSetTime(ClientboundSetTimePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.setGameTime(packet.getGameTime());
        this.minecraft.level.setDayTime(packet.getDayTime());
        this.telemetryManager.setTime(packet.getGameTime());
    }

    @Override
    public void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.setDefaultSpawnPos(packet.getPos(), packet.getAngle());
    }

    @Override
    public void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getVehicle());
        if (entity == null) {
            LOGGER.warn("Received passengers for unknown entity");
        } else {
            boolean flag = entity.hasIndirectPassenger(this.minecraft.player);
            entity.ejectPassengers();

            for (int i : packet.getPassengers()) {
                Entity entity1 = this.level.getEntity(i);
                if (entity1 != null) {
                    entity1.startRiding(entity, true);
                    if (entity1 == this.minecraft.player && !flag) {
                        if (entity instanceof Boat) {
                            this.minecraft.player.yRotO = entity.getYRot();
                            this.minecraft.player.setYRot(entity.getYRot());
                            this.minecraft.player.setYHeadRot(entity.getYRot());
                        }

                        Component component = Component.translatable("mount.onboard", this.minecraft.options.keyShift.getTranslatedKeyMessage());
                        this.minecraft.gui.setOverlayMessage(component, false);
                        this.minecraft.getNarrator().sayNow(component);
                    }
                }
            }
        }
    }

    @Override
    public void handleEntityLinkPacket(ClientboundSetEntityLinkPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getSourceId()) instanceof Leashable leashable) {
            leashable.setDelayedLeashHolderId(packet.getDestId());
        }
    }

    private static ItemStack findTotem(Player player) {
        for (InteractionHand interactionhand : InteractionHand.values()) {
            ItemStack itemstack = player.getItemInHand(interactionhand);
            if (itemstack.is(Items.TOTEM_OF_UNDYING)) {
                return itemstack;
            }
        }

        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    /**
     * Invokes the entities' handleUpdateHealth method which is implemented in LivingBase (hurt/death), MinecartMobSpawner (spawn delay), FireworkRocket & MinecartTNT (explosion), IronGolem (throwing, ...), Witch (spawn particles), Zombie (villager transformation), Animal (breeding mode particles), Horse (breeding/smoke particles), Sheep (...), Tameable (...), Villager (particles for breeding mode, angry and happy), Wolf (...)
     */
    @Override
    public void handleEntityEvent(ClientboundEntityEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            switch (packet.getEventId()) {
                case 21:
                    this.minecraft.getSoundManager().play(new GuardianAttackSoundInstance((Guardian)entity));
                    break;
                case 35:
                    int i = 40;
                    this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
                    this.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F, false);
                    if (entity == this.minecraft.player) {
                        this.minecraft.gameRenderer.displayItemActivation(findTotem(this.minecraft.player));
                    }
                    break;
                case 63:
                    this.minecraft.getSoundManager().play(new SnifferSoundInstance((Sniffer)entity));
                    break;
                default:
                    entity.handleEntityEvent(packet.getEventId());
            }
        }
    }

    @Override
    public void handleDamageEvent(ClientboundDamageEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.entityId());
        if (entity != null) {
            entity.handleDamageEvent(packet.getSource(this.level));
        }
    }

    @Override
    public void handleSetHealth(ClientboundSetHealthPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.hurtTo(packet.getHealth());
        this.minecraft.player.getFoodData().setFoodLevel(packet.getFood());
        this.minecraft.player.getFoodData().setSaturation(packet.getSaturation());
    }

    @Override
    public void handleSetExperience(ClientboundSetExperiencePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.setExperienceValues(packet.getExperienceProgress(), packet.getTotalExperience(), packet.getExperienceLevel());
    }

    @Override
    public void handleRespawn(ClientboundRespawnPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        CommonPlayerSpawnInfo commonplayerspawninfo = packet.commonPlayerSpawnInfo();
        ResourceKey<Level> resourcekey = commonplayerspawninfo.dimension();
        Holder<DimensionType> holder = commonplayerspawninfo.dimensionType();
        LocalPlayer localplayer = this.minecraft.player;
        ResourceKey<Level> resourcekey1 = localplayer.level().dimension();
        boolean flag = resourcekey != resourcekey1;
        ReceivingLevelScreen.Reason receivinglevelscreen$reason = this.determineLevelLoadingReason(localplayer.isDeadOrDying(), resourcekey, resourcekey1);
        if (flag) {
            Map<MapId, MapItemSavedData> map = this.level.getAllMapData();
            boolean flag1 = commonplayerspawninfo.isDebug();
            boolean flag2 = commonplayerspawninfo.isFlat();
            ClientLevel.ClientLevelData clientlevel$clientleveldata = new ClientLevel.ClientLevelData(
                this.levelData.getDifficulty(), this.levelData.isHardcore(), flag2
            );
            this.levelData = clientlevel$clientleveldata;
            this.level = new ClientLevel(
                this,
                clientlevel$clientleveldata,
                resourcekey,
                holder,
                this.serverChunkRadius,
                this.serverSimulationDistance,
                this.minecraft::getProfiler,
                this.minecraft.levelRenderer,
                flag1,
                commonplayerspawninfo.seed()
            );
            this.level.addMapData(map);
            this.minecraft.setLevel(this.level, receivinglevelscreen$reason);
        }

        this.minecraft.cameraEntity = null;
        if (localplayer.hasContainerOpen()) {
            localplayer.closeContainer();
        }

        LocalPlayer localplayer1;
        if (packet.shouldKeep((byte)2)) {
            localplayer1 = this.minecraft
                .gameMode
                .createPlayer(this.level, localplayer.getStats(), localplayer.getRecipeBook(), localplayer.isShiftKeyDown(), localplayer.isSprinting());
        } else {
            localplayer1 = this.minecraft.gameMode.createPlayer(this.level, localplayer.getStats(), localplayer.getRecipeBook());
        }

        this.startWaitingForNewLevel(localplayer1, this.level, receivinglevelscreen$reason, localplayer.isDeadOrDying() ? null : resourcekey, localplayer.isDeadOrDying() ? null : resourcekey1);
        localplayer1.setId(localplayer.getId());
        this.minecraft.player = localplayer1;
        if (flag) {
            this.minecraft.getMusicManager().stopPlaying();
        }

        this.minecraft.cameraEntity = localplayer1;
        if (packet.shouldKeep((byte)2)) {
            List<SynchedEntityData.DataValue<?>> list = localplayer.getEntityData().getNonDefaultValues();
            if (list != null) {
                localplayer1.getEntityData().assignValues(list);
            }
        }

        if (packet.shouldKeep((byte)1)) {
            localplayer1.getAttributes().assignAllValues(localplayer.getAttributes());
        } else {
            localplayer1.getAttributes().assignBaseValues(localplayer.getAttributes());
        }

        localplayer1.resetPos();
        net.neoforged.neoforge.client.ClientHooks.firePlayerRespawn(this.minecraft.gameMode, localplayer, localplayer1, localplayer1.connection.connection);
        this.level.addEntity(localplayer1);
        localplayer1.setYRot(-180.0F);
        localplayer1.input = new KeyboardInput(this.minecraft.options);
        this.minecraft.gameMode.adjustPlayer(localplayer1);
        localplayer1.setReducedDebugInfo(localplayer.isReducedDebugInfo());
        localplayer1.setShowDeathScreen(localplayer.shouldShowDeathScreen());
        localplayer1.setLastDeathLocation(commonplayerspawninfo.lastDeathLocation());
        localplayer1.setPortalCooldown(commonplayerspawninfo.portalCooldown());
        localplayer1.spinningEffectIntensity = localplayer.spinningEffectIntensity;
        localplayer1.oSpinningEffectIntensity = localplayer.oSpinningEffectIntensity;
        if (this.minecraft.screen instanceof DeathScreen || this.minecraft.screen instanceof DeathScreen.TitleConfirmScreen) {
            this.minecraft.setScreen(null);
        }

        this.minecraft.gameMode.setLocalMode(commonplayerspawninfo.gameType(), commonplayerspawninfo.previousGameType());
    }

    private ReceivingLevelScreen.Reason determineLevelLoadingReason(boolean dying, ResourceKey<Level> spawnDimension, ResourceKey<Level> currentDimension) {
        ReceivingLevelScreen.Reason receivinglevelscreen$reason = ReceivingLevelScreen.Reason.OTHER;
        if (!dying) {
            if (spawnDimension == Level.NETHER || currentDimension == Level.NETHER) {
                receivinglevelscreen$reason = ReceivingLevelScreen.Reason.NETHER_PORTAL;
            } else if (spawnDimension == Level.END || currentDimension == Level.END) {
                receivinglevelscreen$reason = ReceivingLevelScreen.Reason.END_PORTAL;
            }
        }

        return receivinglevelscreen$reason;
    }

    /**
     * Initiates a new explosion (sound, particles, drop spawn) for the affected blocks indicated by the packet.
     */
    @Override
    public void handleExplosion(ClientboundExplodePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Explosion explosion = new Explosion(
            this.minecraft.level,
            null,
            packet.getX(),
            packet.getY(),
            packet.getZ(),
            packet.getPower(),
            packet.getToBlow(),
            packet.getBlockInteraction(),
            packet.getSmallExplosionParticles(),
            packet.getLargeExplosionParticles(),
            packet.getExplosionSound()
        );
        explosion.finalizeExplosion(true);
        this.minecraft
            .player
            .setDeltaMovement(
                this.minecraft
                    .player
                    .getDeltaMovement()
                    .add((double)packet.getKnockbackX(), (double)packet.getKnockbackY(), (double)packet.getKnockbackZ())
            );
    }

    @Override
    public void handleHorseScreenOpen(ClientboundHorseScreenOpenPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getEntityId()) instanceof AbstractHorse abstracthorse) {
            LocalPlayer localplayer = this.minecraft.player;
            int i = packet.getInventoryColumns();
            SimpleContainer simplecontainer = new SimpleContainer(AbstractHorse.getInventorySize(i));
            HorseInventoryMenu horseinventorymenu = new HorseInventoryMenu(
                packet.getContainerId(), localplayer.getInventory(), simplecontainer, abstracthorse, i
            );
            localplayer.containerMenu = horseinventorymenu;
            this.minecraft.setScreen(new HorseInventoryScreen(horseinventorymenu, localplayer.getInventory(), abstracthorse, i));
        }
    }

    @Override
    public void handleOpenScreen(ClientboundOpenScreenPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        MenuScreens.create(packet.getType(), this.minecraft, packet.getContainerId(), packet.getTitle());
    }

    /**
     * Handles picking up an ItemStack or dropping one in your inventory or an open (non-creative) container
     */
    @Override
    public void handleContainerSetSlot(ClientboundContainerSetSlotPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        ItemStack itemstack = packet.getItem();
        int i = packet.getSlot();
        this.minecraft.getTutorial().onGetItem(itemstack);
        if (packet.getContainerId() == -1) {
            if (!(this.minecraft.screen instanceof CreativeModeInventoryScreen)) {
                player.containerMenu.setCarried(itemstack);
            }
        } else if (packet.getContainerId() == -2) {
            player.getInventory().setItem(i, itemstack);
        } else {
            boolean flag = false;
            if (this.minecraft.screen instanceof CreativeModeInventoryScreen creativemodeinventoryscreen) {
                flag = !creativemodeinventoryscreen.isInventoryOpen();
            }

            if (packet.getContainerId() == 0 && InventoryMenu.isHotbarSlot(i)) {
                if (!itemstack.isEmpty()) {
                    ItemStack itemstack1 = player.inventoryMenu.getSlot(i).getItem();
                    if (itemstack1.isEmpty() || itemstack1.getCount() < itemstack.getCount()) {
                        itemstack.setPopTime(5);
                    }
                }

                player.inventoryMenu.setItem(i, packet.getStateId(), itemstack);
            } else if (packet.getContainerId() == player.containerMenu.containerId && (packet.getContainerId() != 0 || !flag)) {
                player.containerMenu.setItem(i, packet.getStateId(), itemstack);
            }
        }
    }

    /**
     * Handles the placement of a specified ItemStack in a specified container/inventory slot
     */
    @Override
    public void handleContainerContent(ClientboundContainerSetContentPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        if (packet.getContainerId() == 0) {
            player.inventoryMenu.initializeContents(packet.getStateId(), packet.getItems(), packet.getCarriedItem());
        } else if (packet.getContainerId() == player.containerMenu.containerId) {
            player.containerMenu.initializeContents(packet.getStateId(), packet.getItems(), packet.getCarriedItem());
        }
    }

    /**
     * Creates a sign in the specified location if it didn't exist and opens the GUI to edit its text
     */
    @Override
    public void handleOpenSignEditor(ClientboundOpenSignEditorPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        BlockPos blockpos = packet.getPos();
        if (this.level.getBlockEntity(blockpos) instanceof SignBlockEntity signblockentity) {
            this.minecraft.player.openTextEdit(signblockentity, packet.isFrontText());
        } else {
            BlockState blockstate = this.level.getBlockState(blockpos);
            SignBlockEntity signblockentity1 = new SignBlockEntity(blockpos, blockstate);
            signblockentity1.setLevel(this.level);
            this.minecraft.player.openTextEdit(signblockentity1, packet.isFrontText());
        }
    }

    /**
     * Updates the NBTTagCompound metadata of instances of the following entitytypes: Mob spawners, command blocks, beacons, skulls, flowerpot
     */
    @Override
    public void handleBlockEntityData(ClientboundBlockEntityDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        BlockPos blockpos = packet.getPos();
        this.minecraft.level.getBlockEntity(blockpos, packet.getType()).ifPresent(p_337415_ -> {
            p_337415_.onDataPacket(connection, packet, this.registryAccess);

            if (p_337415_ instanceof CommandBlockEntity && this.minecraft.screen instanceof CommandBlockEditScreen) {
                ((CommandBlockEditScreen)this.minecraft.screen).updateGui();
            }
        });
    }

    /**
     * Sets the progressbar of the opened window to the specified value
     */
    @Override
    public void handleContainerSetData(ClientboundContainerSetDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        if (player.containerMenu != null && player.containerMenu.containerId == packet.getContainerId()) {
            player.containerMenu.setData(packet.getId(), packet.getValue());
        }
    }

    @Override
    public void handleSetEquipment(ClientboundSetEquipmentPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getEntity()) instanceof LivingEntity livingentity) {
            packet.getSlots().forEach(p_323056_ -> livingentity.setItemSlot(p_323056_.getFirst(), p_323056_.getSecond()));
        }
    }

    /**
     * Resets the ItemStack held in hand and closes the window that is opened
     */
    @Override
    public void handleContainerClose(ClientboundContainerClosePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.clientSideCloseContainer();
    }

    /**
     * Triggers Block.onBlockEventReceived, which is implemented in BlockPistonBase for extension/retraction, BlockNote for setting the instrument (including audiovisual feedback) and in BlockContainer to set the number of players accessing a (Ender)Chest
     */
    @Override
    public void handleBlockEvent(ClientboundBlockEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.blockEvent(packet.getPos(), packet.getBlock(), packet.getB0(), packet.getB1());
    }

    /**
     * Updates all registered IWorldAccess instances with destroyBlockInWorldPartially
     */
    @Override
    public void handleBlockDestruction(ClientboundBlockDestructionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.destroyBlockProgress(packet.getId(), packet.getPos(), packet.getProgress());
    }

    @Override
    public void handleGameEvent(ClientboundGameEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        ClientboundGameEventPacket.Type clientboundgameeventpacket$type = packet.getEvent();
        float f = packet.getParam();
        int i = Mth.floor(f + 0.5F);
        if (clientboundgameeventpacket$type == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE) {
            player.displayClientMessage(Component.translatable("block.minecraft.spawn.not_valid"), false);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.START_RAINING) {
            this.level.getLevelData().setRaining(true);
            this.level.setRainLevel(0.0F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.STOP_RAINING) {
            this.level.getLevelData().setRaining(false);
            this.level.setRainLevel(1.0F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            this.minecraft.gameMode.setLocalMode(GameType.byId(i));
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.WIN_GAME) {
            this.minecraft.setScreen(new WinScreen(true, () -> {
                this.minecraft.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                this.minecraft.setScreen(null);
            }));
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.DEMO_EVENT) {
            Options options = this.minecraft.options;
            if (f == 0.0F) {
                this.minecraft.setScreen(new DemoIntroScreen());
            } else if (f == 101.0F) {
                this.minecraft
                    .gui
                    .getChat()
                    .addMessage(
                        Component.translatable(
                            "demo.help.movement",
                            options.keyUp.getTranslatedKeyMessage(),
                            options.keyLeft.getTranslatedKeyMessage(),
                            options.keyDown.getTranslatedKeyMessage(),
                            options.keyRight.getTranslatedKeyMessage()
                        )
                    );
            } else if (f == 102.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.help.jump", options.keyJump.getTranslatedKeyMessage()));
            } else if (f == 103.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.help.inventory", options.keyInventory.getTranslatedKeyMessage()));
            } else if (f == 104.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.day.6", options.keyScreenshot.getTranslatedKeyMessage()));
            }
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.ARROW_HIT_PLAYER) {
            this.level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.18F, 0.45F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            this.level.setRainLevel(f);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            this.level.setThunderLevel(f);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.PUFFER_FISH_STING) {
            this.level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_STING, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT) {
            this.level.addParticle(ParticleTypes.ELDER_GUARDIAN, player.getX(), player.getY(), player.getZ(), 0.0, 0.0, 0.0);
            if (i == 1) {
                this.level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) {
            this.minecraft.player.setShowDeathScreen(f == 0.0F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.LIMITED_CRAFTING) {
            this.minecraft.player.setDoLimitedCrafting(f == 1.0F);
        } else if (clientboundgameeventpacket$type == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START && this.levelLoadStatusManager != null) {
            this.levelLoadStatusManager.loadingPacketsReceived();
        }
    }

    /**
 * @deprecated Neo: use {@link #startWaitingForNewLevel(LocalPlayer, ClientLevel,
 *             ReceivingLevelScreen.Reason, ResourceKey, ResourceKey)} instead.
 */
    @Deprecated
    private void startWaitingForNewLevel(LocalPlayer player, ClientLevel level, ReceivingLevelScreen.Reason reason) {
        this.startWaitingForNewLevel(player, level, reason, null, null);
    }

    private void startWaitingForNewLevel(LocalPlayer player, ClientLevel level, ReceivingLevelScreen.Reason reason, @Nullable ResourceKey<Level> toDimension, @Nullable ResourceKey<Level> fromDimension) {
        this.levelLoadStatusManager = new LevelLoadStatusManager(player, level, this.minecraft.levelRenderer);
        this.minecraft.setScreen(net.neoforged.neoforge.client.DimensionTransitionScreenManager.getScreen(toDimension, fromDimension).create(this.levelLoadStatusManager::levelReady, reason));
    }

    /**
     * Updates the worlds MapStorage with the specified MapData for the specified map-identifier and invokes a MapItemRenderer for it
     */
    @Override
    public void handleMapItemData(ClientboundMapItemDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        MapRenderer maprenderer = this.minecraft.gameRenderer.getMapRenderer();
        MapId mapid = packet.mapId();
        MapItemSavedData mapitemsaveddata = this.minecraft.level.getMapData(mapid);
        if (mapitemsaveddata == null) {
            mapitemsaveddata = MapItemSavedData.createForClient(packet.scale(), packet.locked(), this.minecraft.level.dimension());
            this.minecraft.level.overrideMapData(mapid, mapitemsaveddata);
        }

        packet.applyToMap(mapitemsaveddata);
        maprenderer.update(mapid, mapitemsaveddata);
    }

    @Override
    public void handleLevelEvent(ClientboundLevelEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.isGlobalEvent()) {
            this.minecraft.level.globalLevelEvent(packet.getType(), packet.getPos(), packet.getData());
        } else {
            this.minecraft.level.levelEvent(packet.getType(), packet.getPos(), packet.getData());
        }
    }

    @Override
    public void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.advancements.update(packet);
    }

    @Override
    public void handleSelectAdvancementsTab(ClientboundSelectAdvancementsTabPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ResourceLocation resourcelocation = packet.getTab();
        if (resourcelocation == null) {
            this.advancements.setSelectedTab(null, false);
        } else {
            AdvancementHolder advancementholder = this.advancements.get(resourcelocation);
            this.advancements.setSelectedTab(advancementholder, false);
        }
    }

    @Override
    public void handleCommands(ClientboundCommandsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        var context = CommandBuildContext.simple(this.registryAccess, this.enabledFeatures);
        this.commands = new CommandDispatcher<>(packet.getRoot(context));
        this.commands = net.neoforged.neoforge.client.ClientCommandHandler.mergeServerCommands(this.commands, context);
    }

    @Override
    public void handleStopSoundEvent(ClientboundStopSoundPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getSoundManager().stop(packet.getName(), packet.getSource());
    }

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    @Override
    public void handleCommandSuggestions(ClientboundCommandSuggestionsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.suggestionsProvider.completeCustomSuggestions(packet.id(), packet.toSuggestions());
    }

    @Override
    public void handleUpdateRecipes(ClientboundUpdateRecipesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.recipeManager.replaceRecipes(packet.getRecipes());
        ClientRecipeBook clientrecipebook = this.minecraft.player.getRecipeBook();
        clientrecipebook.setupCollections(this.recipeManager.getOrderedRecipes(), this.minecraft.level.registryAccess());
        this.searchTrees.updateRecipes(clientrecipebook, this.registryAccess);
        net.neoforged.neoforge.client.ClientHooks.onRecipesUpdated(this.recipeManager);
    }

    @Override
    public void handleLookAt(ClientboundPlayerLookAtPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Vec3 vec3 = packet.getPosition(this.level);
        if (vec3 != null) {
            this.minecraft.player.lookAt(packet.getFromAnchor(), vec3);
        }
    }

    @Override
    public void handleTagQueryPacket(ClientboundTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (!this.debugQueryHandler.handleResponse(packet.getTransactionId(), packet.getTag())) {
            LOGGER.debug("Got unhandled response to tag query {}", packet.getTransactionId());
        }
    }

    /**
     * Updates the players statistics or achievements
     */
    @Override
    public void handleAwardStats(ClientboundAwardStatsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (Entry<Stat<?>> entry : packet.stats().object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            int i = entry.getIntValue();
            this.minecraft.player.getStats().setValue(this.minecraft.player, stat, i);
        }

        if (this.minecraft.screen instanceof StatsScreen statsscreen) {
            statsscreen.onStatsUpdated();
        }
    }

    @Override
    public void handleAddOrRemoveRecipes(ClientboundRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ClientRecipeBook clientrecipebook = this.minecraft.player.getRecipeBook();
        clientrecipebook.setBookSettings(packet.getBookSettings());
        ClientboundRecipePacket.State clientboundrecipepacket$state = packet.getState();
        switch (clientboundrecipepacket$state) {
            case REMOVE:
                for (ResourceLocation resourcelocation3 : packet.getRecipes()) {
                    this.recipeManager.byKey(resourcelocation3).ifPresent(clientrecipebook::remove);
                }
                break;
            case INIT:
                for (ResourceLocation resourcelocation1 : packet.getRecipes()) {
                    this.recipeManager.byKey(resourcelocation1).ifPresent(clientrecipebook::add);
                }

                for (ResourceLocation resourcelocation2 : packet.getHighlights()) {
                    this.recipeManager.byKey(resourcelocation2).ifPresent(clientrecipebook::addHighlight);
                }
                break;
            case ADD:
                for (ResourceLocation resourcelocation : packet.getRecipes()) {
                    this.recipeManager.byKey(resourcelocation).ifPresent(p_300677_ -> {
                        clientrecipebook.add((RecipeHolder<?>)p_300677_);
                        clientrecipebook.addHighlight((RecipeHolder<?>)p_300677_);
                        if (p_300677_.value().showNotification()) {
                            RecipeToast.addOrUpdate(this.minecraft.getToasts(), (RecipeHolder<?>)p_300677_);
                        }
                    });
                }
        }

        clientrecipebook.getCollections().forEach(p_205540_ -> p_205540_.updateKnownRecipes(clientrecipebook));
        if (this.minecraft.screen instanceof RecipeUpdateListener) {
            ((RecipeUpdateListener)this.minecraft.screen).recipesUpdated();
        }
    }

    @Override
    public void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getEntityId());
        if (entity instanceof LivingEntity) {
            Holder<MobEffect> holder = packet.getEffect();
            MobEffectInstance mobeffectinstance = new MobEffectInstance(
                holder,
                packet.getEffectDurationTicks(),
                packet.getEffectAmplifier(),
                packet.isEffectAmbient(),
                packet.isEffectVisible(),
                packet.effectShowsIcon(),
                null
            );
            if (!packet.shouldBlend()) {
                mobeffectinstance.skipBlending();
            }

            ((LivingEntity)entity).forceAddEffect(mobeffectinstance, null);
        }
    }

    @Override
    public void handleUpdateTags(ClientboundUpdateTagsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        TagCollector tagcollector = new TagCollector();
        packet.getTags().forEach(tagcollector::append);
        tagcollector.updateTags(this.registryAccess, this.connection.isMemoryConnection());
        CreativeModeTabs.allTabs().stream().filter(net.minecraft.world.item.CreativeModeTab::hasSearchBar).forEach(tab -> {
            List<ItemStack> list = List.copyOf(tab.getDisplayItems());
            this.searchTrees.updateCreativeTags(list, net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchKey(tab));
        });
    }

    @Override
    public void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket packet) {
    }

    @Override
    public void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket packet) {
    }

    @Override
    public void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.playerId());
        if (entity == this.minecraft.player) {
            if (this.minecraft.player.shouldShowDeathScreen()) {
                this.minecraft.setScreen(new DeathScreen(packet.message(), this.level.getLevelData().isHardcore()));
            } else {
                this.minecraft.player.respawn();
            }
        }
    }

    @Override
    public void handleChangeDifficulty(ClientboundChangeDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.levelData.setDifficulty(packet.getDifficulty());
        this.levelData.setDifficultyLocked(packet.isLocked());
    }

    @Override
    public void handleSetCamera(ClientboundSetCameraPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            this.minecraft.setCameraEntity(entity);
        }
    }

    @Override
    public void handleInitializeBorder(ClientboundInitializeBorderPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        WorldBorder worldborder = this.level.getWorldBorder();
        worldborder.setCenter(packet.getNewCenterX(), packet.getNewCenterZ());
        long i = packet.getLerpTime();
        if (i > 0L) {
            worldborder.lerpSizeBetween(packet.getOldSize(), packet.getNewSize(), i);
        } else {
            worldborder.setSize(packet.getNewSize());
        }

        worldborder.setAbsoluteMaxSize(packet.getNewAbsoluteMaxSize());
        worldborder.setWarningBlocks(packet.getWarningBlocks());
        worldborder.setWarningTime(packet.getWarningTime());
    }

    @Override
    public void handleSetBorderCenter(ClientboundSetBorderCenterPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setCenter(packet.getNewCenterX(), packet.getNewCenterZ());
    }

    @Override
    public void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().lerpSizeBetween(packet.getOldSize(), packet.getNewSize(), packet.getLerpTime());
    }

    @Override
    public void handleSetBorderSize(ClientboundSetBorderSizePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setSize(packet.getSize());
    }

    @Override
    public void handleSetBorderWarningDistance(ClientboundSetBorderWarningDistancePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setWarningBlocks(packet.getWarningBlocks());
    }

    @Override
    public void handleSetBorderWarningDelay(ClientboundSetBorderWarningDelayPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setWarningTime(packet.getWarningDelay());
    }

    @Override
    public void handleTitlesClear(ClientboundClearTitlesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.clear();
        if (packet.shouldResetTimes()) {
            this.minecraft.gui.resetTitleTimes();
        }
    }

    @Override
    public void handleServerData(ClientboundServerDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.serverData != null) {
            this.serverData.motd = packet.motd();
            packet.iconBytes().map(ServerData::validateIcon).ifPresent(this.serverData::setIconBytes);
            ServerList.saveSingleServer(this.serverData);
        }
    }

    @Override
    public void handleCustomChatCompletions(ClientboundCustomChatCompletionsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.suggestionsProvider.modifyCustomCompletions(packet.action(), packet.entries());
    }

    @Override
    public void setActionBarText(ClientboundSetActionBarTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setOverlayMessage(packet.text(), false);
    }

    @Override
    public void setTitleText(ClientboundSetTitleTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setTitle(packet.text());
    }

    @Override
    public void setSubtitleText(ClientboundSetSubtitleTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setSubtitle(packet.text());
    }

    @Override
    public void setTitlesAnimation(ClientboundSetTitlesAnimationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setTimes(packet.getFadeIn(), packet.getStay(), packet.getFadeOut());
    }

    @Override
    public void handleTabListCustomisation(ClientboundTabListPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.getTabList().setHeader(packet.header().getString().isEmpty() ? null : packet.header());
        this.minecraft.gui.getTabList().setFooter(packet.footer().getString().isEmpty() ? null : packet.footer());
    }

    @Override
    public void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.getEntity(this.level) instanceof LivingEntity livingentity) {
            livingentity.removeEffectNoUpdate(packet.effect());
        }
    }

    @Override
    public void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (UUID uuid : packet.profileIds()) {
            this.minecraft.getPlayerSocialManager().removePlayer(uuid);
            PlayerInfo playerinfo = this.playerInfoMap.remove(uuid);
            if (playerinfo != null) {
                this.listedPlayers.remove(playerinfo);
            }
        }
    }

    @Override
    public void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (ClientboundPlayerInfoUpdatePacket.Entry clientboundplayerinfoupdatepacket$entry : packet.newEntries()) {
            PlayerInfo playerinfo = new PlayerInfo(Objects.requireNonNull(clientboundplayerinfoupdatepacket$entry.profile()), this.enforcesSecureChat());
            if (this.playerInfoMap.putIfAbsent(clientboundplayerinfoupdatepacket$entry.profileId(), playerinfo) == null) {
                this.minecraft.getPlayerSocialManager().addPlayer(playerinfo);
            }
        }

        for (ClientboundPlayerInfoUpdatePacket.Entry clientboundplayerinfoupdatepacket$entry1 : packet.entries()) {
            PlayerInfo playerinfo1 = this.playerInfoMap.get(clientboundplayerinfoupdatepacket$entry1.profileId());
            if (playerinfo1 == null) {
                LOGGER.warn("Ignoring player info update for unknown player {} ({})", clientboundplayerinfoupdatepacket$entry1.profileId(), packet.actions());
            } else {
                for (ClientboundPlayerInfoUpdatePacket.Action clientboundplayerinfoupdatepacket$action : packet.actions()) {
                    this.applyPlayerInfoUpdate(clientboundplayerinfoupdatepacket$action, clientboundplayerinfoupdatepacket$entry1, playerinfo1);
                }
            }
        }
    }

    private void applyPlayerInfoUpdate(
        ClientboundPlayerInfoUpdatePacket.Action action, ClientboundPlayerInfoUpdatePacket.Entry entry, PlayerInfo playerInfo
    ) {
        switch (action) {
            case INITIALIZE_CHAT:
                this.initializeChatSession(entry, playerInfo);
                break;
            case UPDATE_GAME_MODE:
                if (playerInfo.getGameMode() != entry.gameMode()
                    && this.minecraft.player != null
                    && this.minecraft.player.getUUID().equals(entry.profileId())) {
                    this.minecraft.player.onGameModeChanged(entry.gameMode());
                }

                playerInfo.setGameMode(entry.gameMode());
                break;
            case UPDATE_LISTED:
                if (entry.listed()) {
                    this.listedPlayers.add(playerInfo);
                } else {
                    this.listedPlayers.remove(playerInfo);
                }
                break;
            case UPDATE_LATENCY:
                playerInfo.setLatency(entry.latency());
                break;
            case UPDATE_DISPLAY_NAME:
                playerInfo.setTabListDisplayName(entry.displayName());
        }
    }

    private void initializeChatSession(ClientboundPlayerInfoUpdatePacket.Entry entry, PlayerInfo playerInfo) {
        GameProfile gameprofile = playerInfo.getProfile();
        SignatureValidator signaturevalidator = this.minecraft.getProfileKeySignatureValidator();
        if (signaturevalidator == null) {
            LOGGER.warn("Ignoring chat session from {} due to missing Services public key", gameprofile.getName());
            playerInfo.clearChatSession(this.enforcesSecureChat());
        } else {
            RemoteChatSession.Data remotechatsession$data = entry.chatSession();
            if (remotechatsession$data != null) {
                try {
                    RemoteChatSession remotechatsession = remotechatsession$data.validate(gameprofile, signaturevalidator);
                    playerInfo.setChatSession(remotechatsession);
                } catch (ProfilePublicKey.ValidationException profilepublickey$validationexception) {
                    LOGGER.error("Failed to validate profile key for player: '{}'", gameprofile.getName(), profilepublickey$validationexception);
                    playerInfo.clearChatSession(this.enforcesSecureChat());
                }
            } else {
                playerInfo.clearChatSession(this.enforcesSecureChat());
            }
        }
    }

    private boolean enforcesSecureChat() {
        return this.minecraft.canValidateProfileKeys() && this.serverEnforcesSecureChat;
    }

    @Override
    public void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        player.getAbilities().flying = packet.isFlying();
        player.getAbilities().instabuild = packet.canInstabuild();
        player.getAbilities().invulnerable = packet.isInvulnerable();
        player.getAbilities().mayfly = packet.canFly();
        player.getAbilities().setFlyingSpeed(packet.getFlyingSpeed());
        player.getAbilities().setWalkingSpeed(packet.getWalkingSpeed());
    }

    @Override
    public void handleSoundEvent(ClientboundSoundPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft
            .level
            .playSeededSound(
                this.minecraft.player,
                packet.getX(),
                packet.getY(),
                packet.getZ(),
                packet.getSound(),
                packet.getSource(),
                packet.getVolume(),
                packet.getPitch(),
                packet.getSeed()
            );
    }

    @Override
    public void handleSoundEntityEvent(ClientboundSoundEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            this.minecraft
                .level
                .playSeededSound(
                    this.minecraft.player,
                    entity,
                    packet.getSound(),
                    packet.getSource(),
                    packet.getVolume(),
                    packet.getPitch(),
                    packet.getSeed()
                );
        }
    }

    @Override
    public void handleBossUpdate(ClientboundBossEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.getBossOverlay().update(packet);
    }

    @Override
    public void handleItemCooldown(ClientboundCooldownPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.duration() == 0) {
            this.minecraft.player.getCooldowns().removeCooldown(packet.item());
        } else {
            this.minecraft.player.getCooldowns().addCooldown(packet.item(), packet.duration());
        }
    }

    @Override
    public void handleMoveVehicle(ClientboundMoveVehiclePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.minecraft.player.getRootVehicle();
        if (entity != this.minecraft.player && entity.isControlledByLocalInstance()) {
            entity.absMoveTo(packet.getX(), packet.getY(), packet.getZ(), packet.getYRot(), packet.getXRot());
            this.connection.send(new ServerboundMoveVehiclePacket(entity));
        }
    }

    @Override
    public void handleOpenBook(ClientboundOpenBookPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ItemStack itemstack = this.minecraft.player.getItemInHand(packet.getHand());
        BookViewScreen.BookAccess bookviewscreen$bookaccess = BookViewScreen.BookAccess.fromItem(itemstack);
        if (bookviewscreen$bookaccess != null) {
            this.minecraft.setScreen(new BookViewScreen(bookviewscreen$bookaccess));
        }
    }

    @Override
    public void handleCustomPayload(CustomPacketPayload payload) {
        if (payload instanceof PathfindingDebugPayload pathfindingdebugpayload) {
            this.minecraft
                .debugRenderer
                .pathfindingRenderer
                .addPath(pathfindingdebugpayload.entityId(), pathfindingdebugpayload.path(), pathfindingdebugpayload.maxNodeDistance());
        } else if (payload instanceof NeighborUpdatesDebugPayload neighborupdatesdebugpayload) {
            ((NeighborsUpdateRenderer)this.minecraft.debugRenderer.neighborsUpdateRenderer)
                .addUpdate(neighborupdatesdebugpayload.time(), neighborupdatesdebugpayload.pos());
        } else if (payload instanceof StructuresDebugPayload structuresdebugpayload) {
            this.minecraft
                .debugRenderer
                .structureRenderer
                .addBoundingBox(structuresdebugpayload.mainBB(), structuresdebugpayload.pieces(), structuresdebugpayload.dimension());
        } else if (payload instanceof WorldGenAttemptDebugPayload worldgenattemptdebugpayload) {
            ((WorldGenAttemptRenderer)this.minecraft.debugRenderer.worldGenAttemptRenderer)
                .addPos(
                    worldgenattemptdebugpayload.pos(),
                    worldgenattemptdebugpayload.scale(),
                    worldgenattemptdebugpayload.red(),
                    worldgenattemptdebugpayload.green(),
                    worldgenattemptdebugpayload.blue(),
                    worldgenattemptdebugpayload.alpha()
                );
        } else if (payload instanceof PoiTicketCountDebugPayload poiticketcountdebugpayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.setFreeTicketCount(poiticketcountdebugpayload.pos(), poiticketcountdebugpayload.freeTicketCount());
        } else if (payload instanceof PoiAddedDebugPayload poiaddeddebugpayload) {
            BrainDebugRenderer.PoiInfo braindebugrenderer$poiinfo = new BrainDebugRenderer.PoiInfo(
                poiaddeddebugpayload.pos(), poiaddeddebugpayload.poiType(), poiaddeddebugpayload.freeTicketCount()
            );
            this.minecraft.debugRenderer.brainDebugRenderer.addPoi(braindebugrenderer$poiinfo);
        } else if (payload instanceof PoiRemovedDebugPayload poiremoveddebugpayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.removePoi(poiremoveddebugpayload.pos());
        } else if (payload instanceof VillageSectionsDebugPayload villagesectionsdebugpayload) {
            VillageSectionsDebugRenderer villagesectionsdebugrenderer = this.minecraft.debugRenderer.villageSectionsDebugRenderer;
            villagesectionsdebugpayload.villageChunks().forEach(villagesectionsdebugrenderer::setVillageSection);
            villagesectionsdebugpayload.notVillageChunks().forEach(villagesectionsdebugrenderer::setNotVillageSection);
        } else if (payload instanceof GoalDebugPayload goaldebugpayload) {
            this.minecraft.debugRenderer.goalSelectorRenderer.addGoalSelector(goaldebugpayload.entityId(), goaldebugpayload.pos(), goaldebugpayload.goals());
        } else if (payload instanceof BrainDebugPayload braindebugpayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.addOrUpdateBrainDump(braindebugpayload.brainDump());
        } else if (payload instanceof BeeDebugPayload beedebugpayload) {
            this.minecraft.debugRenderer.beeDebugRenderer.addOrUpdateBeeInfo(beedebugpayload.beeInfo());
        } else if (payload instanceof HiveDebugPayload hivedebugpayload) {
            this.minecraft.debugRenderer.beeDebugRenderer.addOrUpdateHiveInfo(hivedebugpayload.hiveInfo(), this.level.getGameTime());
        } else if (payload instanceof GameTestAddMarkerDebugPayload gametestaddmarkerdebugpayload) {
            this.minecraft
                .debugRenderer
                .gameTestDebugRenderer
                .addMarker(
                    gametestaddmarkerdebugpayload.pos(),
                    gametestaddmarkerdebugpayload.color(),
                    gametestaddmarkerdebugpayload.text(),
                    gametestaddmarkerdebugpayload.durationMs()
                );
        } else if (payload instanceof GameTestClearMarkersDebugPayload) {
            this.minecraft.debugRenderer.gameTestDebugRenderer.clear();
        } else if (payload instanceof RaidsDebugPayload raidsdebugpayload) {
            this.minecraft.debugRenderer.raidDebugRenderer.setRaidCenters(raidsdebugpayload.raidCenters());
        } else if (payload instanceof GameEventDebugPayload gameeventdebugpayload) {
            this.minecraft.debugRenderer.gameEventListenerRenderer.trackGameEvent(gameeventdebugpayload.gameEventType(), gameeventdebugpayload.pos());
        } else if (payload instanceof GameEventListenerDebugPayload gameeventlistenerdebugpayload) {
            this.minecraft
                .debugRenderer
                .gameEventListenerRenderer
                .trackListener(gameeventlistenerdebugpayload.listenerPos(), gameeventlistenerdebugpayload.listenerRange());
        } else if (payload instanceof BreezeDebugPayload breezedebugpayload) {
            this.minecraft.debugRenderer.breezeDebugRenderer.add(breezedebugpayload.breezeInfo());
        } else {
            this.handleUnknownCustomPayload(payload);
        }
    }

    private void handleUnknownCustomPayload(CustomPacketPayload packet) {
        LOGGER.warn("Unknown custom packet payload: {}", packet.type().id());
    }

    /**
     * May create a scoreboard objective, remove an objective from the scoreboard or update an objectives' displayname
     */
    @Override
    public void handleAddObjective(ClientboundSetObjectivePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String s = packet.getObjectiveName();
        if (packet.getMethod() == 0) {
            this.scoreboard
                .addObjective(
                    s, ObjectiveCriteria.DUMMY, packet.getDisplayName(), packet.getRenderType(), false, packet.getNumberFormat().orElse(null)
                );
        } else {
            Objective objective = this.scoreboard.getObjective(s);
            if (objective != null) {
                if (packet.getMethod() == 1) {
                    this.scoreboard.removeObjective(objective);
                } else if (packet.getMethod() == 2) {
                    objective.setRenderType(packet.getRenderType());
                    objective.setDisplayName(packet.getDisplayName());
                    objective.setNumberFormat(packet.getNumberFormat().orElse(null));
                }
            }
        }
    }

    /**
     * Either updates the score with a specified value or removes the score for an objective
     */
    @Override
    public void handleSetScore(ClientboundSetScorePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String s = packet.objectiveName();
        ScoreHolder scoreholder = ScoreHolder.forNameOnly(packet.owner());
        Objective objective = this.scoreboard.getObjective(s);
        if (objective != null) {
            ScoreAccess scoreaccess = this.scoreboard.getOrCreatePlayerScore(scoreholder, objective, true);
            scoreaccess.set(packet.score());
            scoreaccess.display(packet.display().orElse(null));
            scoreaccess.numberFormatOverride(packet.numberFormat().orElse(null));
        } else {
            LOGGER.warn("Received packet for unknown scoreboard objective: {}", s);
        }
    }

    @Override
    public void handleResetScore(ClientboundResetScorePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String s = packet.objectiveName();
        ScoreHolder scoreholder = ScoreHolder.forNameOnly(packet.owner());
        if (s == null) {
            this.scoreboard.resetAllPlayerScores(scoreholder);
        } else {
            Objective objective = this.scoreboard.getObjective(s);
            if (objective != null) {
                this.scoreboard.resetSinglePlayerScore(scoreholder, objective);
            } else {
                LOGGER.warn("Received packet for unknown scoreboard objective: {}", s);
            }
        }
    }

    /**
     * Removes or sets the ScoreObjective to be displayed at a particular scoreboard position (list, sidebar, below name)
     */
    @Override
    public void handleSetDisplayObjective(ClientboundSetDisplayObjectivePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String s = packet.getObjectiveName();
        Objective objective = s == null ? null : this.scoreboard.getObjective(s);
        this.scoreboard.setDisplayObjective(packet.getSlot(), objective);
    }

    /**
     * Updates a team managed by the scoreboard: Create/Remove the team registration, Register/Remove the player-team-memberships, Set team displayname/prefix/suffix and/or whether friendly fire is enabled
     */
    @Override
    public void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ClientboundSetPlayerTeamPacket.Action clientboundsetplayerteampacket$action = packet.getTeamAction();
        PlayerTeam playerteam;
        if (clientboundsetplayerteampacket$action == ClientboundSetPlayerTeamPacket.Action.ADD) {
            playerteam = this.scoreboard.addPlayerTeam(packet.getName());
        } else {
            playerteam = this.scoreboard.getPlayerTeam(packet.getName());
            if (playerteam == null) {
                LOGGER.warn(
                    "Received packet for unknown team {}: team action: {}, player action: {}",
                    packet.getName(),
                    packet.getTeamAction(),
                    packet.getPlayerAction()
                );
                return;
            }
        }

        Optional<ClientboundSetPlayerTeamPacket.Parameters> optional = packet.getParameters();
        optional.ifPresent(p_233670_ -> {
            playerteam.setDisplayName(p_233670_.getDisplayName());
            playerteam.setColor(p_233670_.getColor());
            playerteam.unpackOptions(p_233670_.getOptions());
            Team.Visibility team$visibility = Team.Visibility.byName(p_233670_.getNametagVisibility());
            if (team$visibility != null) {
                playerteam.setNameTagVisibility(team$visibility);
            }

            Team.CollisionRule team$collisionrule = Team.CollisionRule.byName(p_233670_.getCollisionRule());
            if (team$collisionrule != null) {
                playerteam.setCollisionRule(team$collisionrule);
            }

            playerteam.setPlayerPrefix(p_233670_.getPlayerPrefix());
            playerteam.setPlayerSuffix(p_233670_.getPlayerSuffix());
        });
        ClientboundSetPlayerTeamPacket.Action clientboundsetplayerteampacket$action1 = packet.getPlayerAction();
        if (clientboundsetplayerteampacket$action1 == ClientboundSetPlayerTeamPacket.Action.ADD) {
            for (String s : packet.getPlayers()) {
                this.scoreboard.addPlayerToTeam(s, playerteam);
            }
        } else if (clientboundsetplayerteampacket$action1 == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            for (String s1 : packet.getPlayers()) {
                this.scoreboard.removePlayerFromTeam(s1, playerteam);
            }
        }

        if (clientboundsetplayerteampacket$action == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            this.scoreboard.removePlayerTeam(playerteam);
        }
    }

    /**
     * Spawns a specified number of particles at the specified location with a randomized displacement according to specified bounds
     */
    @Override
    public void handleParticleEvent(ClientboundLevelParticlesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.getCount() == 0) {
            double d0 = (double)(packet.getMaxSpeed() * packet.getXDist());
            double d2 = (double)(packet.getMaxSpeed() * packet.getYDist());
            double d4 = (double)(packet.getMaxSpeed() * packet.getZDist());

            try {
                this.level
                    .addParticle(packet.getParticle(), packet.isOverrideLimiter(), packet.getX(), packet.getY(), packet.getZ(), d0, d2, d4);
            } catch (Throwable throwable1) {
                LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
            }
        } else {
            for (int i = 0; i < packet.getCount(); i++) {
                double d1 = this.random.nextGaussian() * (double)packet.getXDist();
                double d3 = this.random.nextGaussian() * (double)packet.getYDist();
                double d5 = this.random.nextGaussian() * (double)packet.getZDist();
                double d6 = this.random.nextGaussian() * (double)packet.getMaxSpeed();
                double d7 = this.random.nextGaussian() * (double)packet.getMaxSpeed();
                double d8 = this.random.nextGaussian() * (double)packet.getMaxSpeed();

                try {
                    this.level
                        .addParticle(
                            packet.getParticle(),
                            packet.isOverrideLimiter(),
                            packet.getX() + d1,
                            packet.getY() + d3,
                            packet.getZ() + d5,
                            d6,
                            d7,
                            d8
                        );
                } catch (Throwable throwable) {
                    LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
                    return;
                }
            }
        }
    }

    /**
     * Updates en entity's attributes and their respective modifiers, which are used for speed bonuses (player sprinting, animals fleeing, baby speed), weapon/tool attackDamage, hostiles followRange randomization, zombie maxHealth and knockback resistance as well as reinforcement spawning chance.
     */
    @Override
    public void handleUpdateAttributes(ClientboundUpdateAttributesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getEntityId());
        if (entity != null) {
            if (!(entity instanceof LivingEntity)) {
                throw new IllegalStateException("Server tried to update attributes of a non-living entity (actually: " + entity + ")");
            } else {
                AttributeMap attributemap = ((LivingEntity)entity).getAttributes();

                for (ClientboundUpdateAttributesPacket.AttributeSnapshot clientboundupdateattributespacket$attributesnapshot : packet.getValues()) {
                    AttributeInstance attributeinstance = attributemap.getInstance(clientboundupdateattributespacket$attributesnapshot.attribute());
                    if (attributeinstance == null) {
                        LOGGER.warn(
                            "Entity {} does not have attribute {}", entity, clientboundupdateattributespacket$attributesnapshot.attribute().getRegisteredName()
                        );
                    } else {
                        attributeinstance.setBaseValue(clientboundupdateattributespacket$attributesnapshot.base());
                        attributeinstance.removeModifiers();

                        for (AttributeModifier attributemodifier : clientboundupdateattributespacket$attributesnapshot.modifiers()) {
                            attributeinstance.addTransientModifier(attributemodifier);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        AbstractContainerMenu abstractcontainermenu = this.minecraft.player.containerMenu;
        if (abstractcontainermenu.containerId == packet.getContainerId()) {
            this.recipeManager.byKey(packet.getRecipe()).ifPresent(p_300679_ -> {
                if (this.minecraft.screen instanceof RecipeUpdateListener) {
                    RecipeBookComponent recipebookcomponent = ((RecipeUpdateListener)this.minecraft.screen).getRecipeBookComponent();
                    recipebookcomponent.setupGhostRecipe((RecipeHolder<?>)p_300679_, abstractcontainermenu.slots);
                }
            });
        }
    }

    @Override
    public void handleLightUpdatePacket(ClientboundLightUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        int i = packet.getX();
        int j = packet.getZ();
        ClientboundLightUpdatePacketData clientboundlightupdatepacketdata = packet.getLightData();
        this.level.queueLightUpdate(() -> this.applyLightData(i, j, clientboundlightupdatepacketdata));
    }

    private void applyLightData(int x, int z, ClientboundLightUpdatePacketData data) {
        LevelLightEngine levellightengine = this.level.getChunkSource().getLightEngine();
        BitSet bitset = data.getSkyYMask();
        BitSet bitset1 = data.getEmptySkyYMask();
        Iterator<byte[]> iterator = data.getSkyUpdates().iterator();
        this.readSectionList(x, z, levellightengine, LightLayer.SKY, bitset, bitset1, iterator);
        BitSet bitset2 = data.getBlockYMask();
        BitSet bitset3 = data.getEmptyBlockYMask();
        Iterator<byte[]> iterator1 = data.getBlockUpdates().iterator();
        this.readSectionList(x, z, levellightengine, LightLayer.BLOCK, bitset2, bitset3, iterator1);
        levellightengine.setLightEnabled(new ChunkPos(x, z), true);
    }

    @Override
    public void handleMerchantOffers(ClientboundMerchantOffersPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        AbstractContainerMenu abstractcontainermenu = this.minecraft.player.containerMenu;
        if (packet.getContainerId() == abstractcontainermenu.containerId && abstractcontainermenu instanceof MerchantMenu merchantmenu) {
            merchantmenu.setOffers(packet.getOffers());
            merchantmenu.setXp(packet.getVillagerXp());
            merchantmenu.setMerchantLevel(packet.getVillagerLevel());
            merchantmenu.setShowProgressBar(packet.showProgress());
            merchantmenu.setCanRestock(packet.canRestock());
        }
    }

    @Override
    public void handleSetChunkCacheRadius(ClientboundSetChunkCacheRadiusPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.serverChunkRadius = packet.getRadius();
        this.minecraft.options.setServerRenderDistance(this.serverChunkRadius);
        this.level.getChunkSource().updateViewRadius(packet.getRadius());
    }

    @Override
    public void handleSetSimulationDistance(ClientboundSetSimulationDistancePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.serverSimulationDistance = packet.simulationDistance();
        this.level.setServerSimulationDistance(this.serverSimulationDistance);
    }

    @Override
    public void handleSetChunkCacheCenter(ClientboundSetChunkCacheCenterPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getChunkSource().updateViewCenter(packet.getX(), packet.getZ());
    }

    @Override
    public void handleBlockChangedAck(ClientboundBlockChangedAckPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.handleBlockChangedAck(packet.sequence());
    }

    @Override
    public void handleBundlePacket(ClientboundBundlePacket p_packet) {
        PacketUtils.ensureRunningOnSameThread(p_packet, this, this.minecraft);

        for (Packet<? super ClientGamePacketListener> packet : p_packet.subPackets()) {
            packet.handle(this);
        }
    }

    @Override
    public void handleProjectilePowerPacket(ClientboundProjectilePowerPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getId()) instanceof AbstractHurtingProjectile abstracthurtingprojectile) {
            abstracthurtingprojectile.accelerationPower = packet.getAccelerationPower();
        }
    }

    @Override
    public void handleChunkBatchStart(ClientboundChunkBatchStartPacket packet) {
        this.chunkBatchSizeCalculator.onBatchStart();
    }

    @Override
    public void handleChunkBatchFinished(ClientboundChunkBatchFinishedPacket packet) {
        this.chunkBatchSizeCalculator.onBatchFinished(packet.batchSize());
        this.send(new ServerboundChunkBatchReceivedPacket(this.chunkBatchSizeCalculator.getDesiredChunksPerTick()));
    }

    @Override
    public void handleDebugSample(ClientboundDebugSamplePacket packet) {
        this.minecraft.getDebugOverlay().logRemoteSample(packet.sample(), packet.debugSampleType());
    }

    @Override
    public void handlePongResponse(ClientboundPongResponsePacket packet) {
        this.pingDebugMonitor.onPongReceived(packet);
    }

    private void readSectionList(
        int x, int z, LevelLightEngine lightEngine, LightLayer lightLayer, BitSet skyYMask, BitSet emptySkyYMask, Iterator<byte[]> skyUpdates
    ) {
        for (int i = 0; i < lightEngine.getLightSectionCount(); i++) {
            int j = lightEngine.getMinLightSection() + i;
            boolean flag = skyYMask.get(i);
            boolean flag1 = emptySkyYMask.get(i);
            if (flag || flag1) {
                lightEngine.queueSectionData(
                    lightLayer, SectionPos.of(x, j, z), flag ? new DataLayer((byte[])skyUpdates.next().clone()) : new DataLayer()
                );
                this.level.setSectionDirtyWithNeighbors(x, j, z);
            }
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.closed;
    }

    public Collection<PlayerInfo> getListedOnlinePlayers() {
        return this.listedPlayers;
    }

    public Collection<PlayerInfo> getOnlinePlayers() {
        return this.playerInfoMap.values();
    }

    public Collection<UUID> getOnlinePlayerIds() {
        return this.playerInfoMap.keySet();
    }

    @Nullable
    public PlayerInfo getPlayerInfo(UUID uniqueId) {
        return this.playerInfoMap.get(uniqueId);
    }

    /**
     * Gets the client's description information about another player on the server.
     */
    @Nullable
    public PlayerInfo getPlayerInfo(String name) {
        for (PlayerInfo playerinfo : this.playerInfoMap.values()) {
            if (playerinfo.getProfile().getName().equals(name)) {
                return playerinfo;
            }
        }

        return null;
    }

    public GameProfile getLocalGameProfile() {
        return this.localGameProfile;
    }

    public ClientAdvancements getAdvancements() {
        return this.advancements;
    }

    public CommandDispatcher<SharedSuggestionProvider> getCommands() {
        return this.commands;
    }

    public ClientLevel getLevel() {
        return this.level;
    }

    public DebugQueryHandler getDebugQueryHandler() {
        return this.debugQueryHandler;
    }

    public UUID getId() {
        return this.id;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registryAccess;
    }

    public void markMessageAsProcessed(PlayerChatMessage chatMessage, boolean acknowledged) {
        MessageSignature messagesignature = chatMessage.signature();
        if (messagesignature != null && this.lastSeenMessages.addPending(messagesignature, acknowledged) && this.lastSeenMessages.offset() > 64) {
            this.sendChatAcknowledgement();
        }
    }

    private void sendChatAcknowledgement() {
        int i = this.lastSeenMessages.getAndClearOffset();
        if (i > 0) {
            this.send(new ServerboundChatAckPacket(i));
        }
    }

    public void sendChat(String message) {
        message = net.neoforged.neoforge.client.ClientHooks.onClientSendMessage(message);
        if (message.isEmpty()) return;
        Instant instant = Instant.now();
        long i = Crypt.SaltSupplier.getLong();
        LastSeenMessagesTracker.Update lastseenmessagestracker$update = this.lastSeenMessages.generateAndApplyUpdate();
        MessageSignature messagesignature = this.signedMessageEncoder
            .pack(new SignedMessageBody(message, instant, i, lastseenmessagestracker$update.lastSeen()));
        this.send(new ServerboundChatPacket(message, instant, i, messagesignature, lastseenmessagestracker$update.update()));
    }

    public void sendCommand(String command) {
        if (net.neoforged.neoforge.client.ClientCommandHandler.runCommand(command)) return;
        SignableCommand<SharedSuggestionProvider> signablecommand = SignableCommand.of(this.parseCommand(command));
        if (signablecommand.arguments().isEmpty()) {
            this.send(new ServerboundChatCommandPacket(command));
        } else {
            Instant instant = Instant.now();
            long i = Crypt.SaltSupplier.getLong();
            LastSeenMessagesTracker.Update lastseenmessagestracker$update = this.lastSeenMessages.generateAndApplyUpdate();
            ArgumentSignatures argumentsignatures = ArgumentSignatures.signCommand(signablecommand, p_247875_ -> {
                SignedMessageBody signedmessagebody = new SignedMessageBody(p_247875_, instant, i, lastseenmessagestracker$update.lastSeen());
                return this.signedMessageEncoder.pack(signedmessagebody);
            });
            this.send(new ServerboundChatCommandSignedPacket(command, instant, i, argumentsignatures, lastseenmessagestracker$update.update()));
        }
    }

    public boolean sendUnsignedCommand(String command) {
        // Neo: Dispatch client commands for text component click actions.
        if (net.neoforged.neoforge.client.ClientCommandHandler.runCommand(command)) return true;
        if (!SignableCommand.hasSignableArguments(this.parseCommand(command))) {
            this.send(new ServerboundChatCommandPacket(command));
            return true;
        } else {
            return false;
        }
    }

    private ParseResults<SharedSuggestionProvider> parseCommand(String command) {
        return this.commands.parse(command, this.suggestionsProvider);
    }

    @Override
    public void tick() {
        if (this.connection.isEncrypted()) {
            ProfileKeyPairManager profilekeypairmanager = this.minecraft.getProfileKeyPairManager();
            if (profilekeypairmanager.shouldRefreshKeyPair()) {
                profilekeypairmanager.prepareKeyPair().thenAcceptAsync(p_253339_ -> p_253339_.ifPresent(this::setKeyPair), this.minecraft);
            }
        }

        this.sendDeferredPackets();
        if (this.minecraft.getDebugOverlay().showNetworkCharts()) {
            this.pingDebugMonitor.tick();
        }

        this.debugSampleSubscriber.tick();
        this.telemetryManager.tick();
        if (this.levelLoadStatusManager != null) {
            this.levelLoadStatusManager.tick();
        }
    }

    public void setKeyPair(ProfileKeyPair keyPair) {
        if (this.minecraft.isLocalPlayer(this.localGameProfile.getId())) {
            if (this.chatSession == null || !this.chatSession.keyPair().equals(keyPair)) {
                this.chatSession = LocalChatSession.create(keyPair);
                this.signedMessageEncoder = this.chatSession.createMessageEncoder(this.localGameProfile.getId());
                this.send(new ServerboundChatSessionUpdatePacket(this.chatSession.asRemote().asData()));
            }
        }
    }

    @Nullable
    public ServerData getServerData() {
        return this.serverData;
    }

    public FeatureFlagSet enabledFeatures() {
        return this.enabledFeatures;
    }

    public boolean isFeatureEnabled(FeatureFlagSet enabledFeatures) {
        return enabledFeatures.isSubsetOf(this.enabledFeatures());
    }

    public Scoreboard scoreboard() {
        return this.scoreboard;
    }

    public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
        return this.connectionType;
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public void updateSearchTrees() {
        this.searchTrees.rebuildAfterLanguageChange();
    }

    public SessionSearchTrees searchTrees() {
        return this.searchTrees;
    }

    public ServerLinks serverLinks() {
        return this.serverLinks;
    }
}
