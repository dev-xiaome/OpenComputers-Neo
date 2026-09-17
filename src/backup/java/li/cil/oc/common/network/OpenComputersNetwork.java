package li.cil.oc.common.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenComputers 的网络层：把 {@link OpenComputersPayload} 注册到 NeoForge 的
 * payload 体系，并把收到的负载转交给 Scala 侧注册的 {@link NetworkDispatcher}。
 * <p>
 * 用法（由 Scala 侧 {@code li.cil.oc.common.PacketHandler.initialize(modBus)} 调用）：
 * <pre>{@code
 * OpenComputersNetwork.setServerDispatcher(...);   // 服务端分发器
 * OpenComputersNetwork.setClientDispatcher(...);   // 客户端分发器
 * OpenComputersNetwork.register(modBus);           // 注册 RegisterPayloadHandlersEvent 监听
 * }</pre>
 * <p>
 * <b>为什么用 playBidirectional 而不是 playToClient + playToServer：</b>
 * {@code NetworkRegistry.register} 以「payload id」为键存放在 protocol（PLAY）下，
 * 同一个 id 注册第二次会直接抛
 * {@code UnsupportedOperationException: Cannot register payload ... as it is already registered.}
 * （已用 javap 在 21.1.244 的 {@code NetworkRegistry#register} 中核实）。
 * 因此双向负载只能用 {@code playBidirectional} + {@link DirectionalPayloadHandler}，
 * 由它按 {@code IPayloadContext#flow()} 分派到客户端侧 / 服务端侧处理器——效果等价于
 * 「同时 playToClient 与 playToServer」。
 * <p>
 * 处理器使用默认的 {@code HandlerThread.MAIN}（{@code PayloadRegistrar} 默认值），
 * NeoForge 会用 {@code MainThreadPayloadHandler} 包装，所以处理器在主线程执行，
 * 内部不需要（也不应该）再调用 {@code context.enqueueWork}。
 */
public final class OpenComputersNetwork {
    /** 网络协议版本：编解码或处理逻辑变化时递增；Neo-Neo 连接版本不一致会被拒绝。 */
    public static final String NETWORK_VERSION = "1";

    private static final Logger LOGGER = LogManager.getLogger("OpenComputers Neo-Network");

    private static volatile NetworkDispatcher serverDispatcher;
    private static volatile NetworkDispatcher clientDispatcher;
    private static volatile boolean registered;

    private OpenComputersNetwork() {
    }

    /** 由 Scala 侧调用：注册 mod 总线监听，真正注册发生在 {@link RegisterPayloadHandlersEvent}。 */
    public static void register(IEventBus modBus) {
        if (registered) {
            LOGGER.warn("OpenComputers network layer was already registered; ignoring the duplicate call.");
            return;
        }
        registered = true;
        modBus.addListener(OpenComputersNetwork::onRegisterPayloadHandlers);
    }

    /** 服务端侧分发器（收到客户端发来的包时调用）。 */
    public static void setServerDispatcher(NetworkDispatcher dispatcher) {
        serverDispatcher = dispatcher;
    }

    /** 客户端侧分发器（收到服务端发来的包时调用）。 */
    public static void setClientDispatcher(NetworkDispatcher dispatcher) {
        clientDispatcher = dispatcher;
    }

    /**
     * 注册负载。必须以 {@code @SubscribeEvent} 式的清单方式挂在 mod 事件总线上，
     * 这里由 {@link #register(IEventBus)} 完成挂载。
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playBidirectional(OpenComputersPayload.TYPE, OpenComputersPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(CLIENT_HANDLER, SERVER_HANDLER));
        LOGGER.debug("Registered payload {} (version {}).", OpenComputersPayload.ID, NETWORK_VERSION);
    }

    /** 客户端侧处理器：只在客户端线程/主线程执行，上下文里的 player 是本机玩家。 */
    private static final IPayloadHandler<OpenComputersPayload> CLIENT_HANDLER =
            (payload, context) -> dispatch(payload, context, false);

    /** 服务端侧处理器：上下文里的 player 是发来该包的 {@code ServerPlayer}。 */
    private static final IPayloadHandler<OpenComputersPayload> SERVER_HANDLER =
            (payload, context) -> dispatch(payload, context, true);

    private static void dispatch(OpenComputersPayload payload, IPayloadContext context, boolean serverSide) {
        final NetworkDispatcher dispatcher = serverSide ? serverDispatcher : clientDispatcher;
        if (dispatcher == null) {
            // server/client 包尚未移植完成时不能崩服：只记录并丢弃。
            LOGGER.warn("Received payload {} ({} bytes) on the {} side, but no dispatcher is registered yet; ignoring.",
                    payload.type().id(), payload.data().length, serverSide ? "server" : "client");
            return;
        }
        try {
            dispatcher.dispatch(payload.data(), context.player());
        } catch (Throwable t) {
            LOGGER.warn("Failed to dispatch payload {}.", payload.type().id(), t);
        }
    }
}
