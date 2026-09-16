package li.cil.oc.integration.computercraft;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import li.cil.oc.OpenComputers;
import li.cil.oc.Settings;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.util.Reflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class DriverPeripheral implements DriverBlock {
    private static Set<Class<?>> blacklist;

    private boolean isBlacklisted(final Object o) {
        if (o instanceof BlacklistedPeripheral) {
            return ((BlacklistedPeripheral) o).isPeripheralBlacklisted();
        }

        if (blacklist == null) {
            blacklist = new HashSet<>();
            for (String name : Settings.get().peripheralBlacklist()) {
                final Class<?> clazz = Reflection.getClass(name);
                if (clazz != null) {
                    blacklist.add(clazz);
                }
            }
        }

        for (Class<?> clazz : blacklist) {
            if (clazz.isInstance(o)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static Capability<IPeripheral> getPeripheralCapability() {
        try {
            Class<?> clazz = Class.forName("dan200.computercraft.shared.Capabilities");
            return (Capability<IPeripheral>) clazz.getField("CAPABILITY_PERIPHERAL").get(null);
        } catch (Exception e) {
            OpenComputers.log().warn("Could not access ComputerCraft Capabilities via reflection.", e);
            return null;
        }
    }

    private static final Capability<IPeripheral> PERIPHERAL_CAP = getPeripheralCapability();

    private IPeripheral findPeripheral(final Level world, final BlockPos pos, final Direction side) {
        final BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return null;

        if (PERIPHERAL_CAP != null) {
            final IPeripheral p = be.getCapability(PERIPHERAL_CAP, side).orElse(null);
            if (!isBlacklisted(p)) return p;
        }

        final IPeripheral p2 = be.getCapability(
                PeripheralProvider.CAPABILITY_PERIPHERAL(), side).orElse(null);
        if (!isBlacklisted(p2)) return p2;

        return null;
    }

    @Override
    public boolean worksWith(final Level world, final BlockPos pos, final Direction side) {
        final BlockEntity tileEntity = world.getBlockEntity(pos);

        return tileEntity != null
                && !li.cil.oc.api.network.Environment.class.isAssignableFrom(tileEntity.getClass())
                && !isBlacklisted(tileEntity)
                && findPeripheral(world, pos, side) != null;
    }

    @Override
    public ManagedEnvironment createEnvironment(final Level world, final BlockPos pos, final Direction side) {
        return new Environment(findPeripheral(world, pos, side));
    }

    public static class Environment extends AbstractManagedEnvironment implements ManagedPeripheral, NamedBlock {
        protected final IPeripheral peripheral;
        protected final String[] methodNames;
        protected final Map<String, FakeComputerAccess> accesses = new HashMap<>();
        protected final Map<String, Method> reflectedMethods = new HashMap<>();

        public Environment(final IPeripheral peripheral) {
            this.peripheral = peripheral;

            if (peripheral instanceof IDynamicPeripheral dynamic) {
                methodNames = dynamic.getMethodNames();
            } else {
                final List<String> names = new ArrayList<>();

                for (Method method : peripheral.getClass().getMethods()) {
                    if (method.isAnnotationPresent(LuaFunction.class)) {
                        reflectedMethods.put(method.getName(), method);
                        names.add(method.getName());
                    }
                }

                methodNames = names.toArray(new String[0]);
            }

            setNode(Network.newNode(this, Visibility.Network).create());
        }

        @Override
        public String[] methods() {
            return methodNames;
        }

        @Override
        public Object[] invoke(final String name, final Context context, final Arguments args) throws Exception {
            final FakeComputerAccess access;

            if (accesses.containsKey(context.node().address())) {
                access = accesses.get(context.node().address());
            } else {
                access = new FakeComputerAccess(this, context);
            }

            final Object[] argArray = CallableHelper.convertArguments(args);

            if (peripheral instanceof IDynamicPeripheral dynamic) {
                final String[] names = dynamic.getMethodNames();

                int index = -1;

                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(name)) {
                        index = i;
                        break;
                    }
                }

                if (index == -1) {
                    throw new NoSuchMethodException();
                }

                return resolveMethodResult(dynamic.callMethod(
                        access,
                        new OCLuaContext(),
                        index,
                        new ObjectArguments(argArray)
                ));
            }

            final Method method = reflectedMethods.get(name);

            if (method == null) {
                throw new NoSuchMethodException();
            }

            final Object[] invokeArgs = buildInvokeArguments(method, argArray, access);

            final Object result = method.invoke(peripheral, invokeArgs);

            if (result instanceof MethodResult mr) {
                return resolveMethodResult(mr);
            }

            return wrapResult(result);
        }

        private Object[] buildInvokeArguments(final Method method, final Object[] args, final IComputerAccess access) {
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Type[] genericParameterTypes = method.getGenericParameterTypes();
            final Object[] invokeArgs = new Object[parameterTypes.length];

            int argIndex = 0;

            for (int i = 0; i < parameterTypes.length; i++) {
                final Class<?> type = parameterTypes[i];

                if (type == IComputerAccess.class) {
                    invokeArgs[i] = access;
                } else if (type == ILuaContext.class) {
                    invokeArgs[i] = new OCLuaContext();
                } else if (type == ObjectArguments.class) {
                    invokeArgs[i] = new ObjectArguments(args);
                } else if (type == Optional.class) {
                    final Class<?> innerType = resolveOptionalType(genericParameterTypes[i]);
                    if (argIndex < args.length && args[argIndex] != null) {
                        invokeArgs[i] = Optional.ofNullable(coerce(args[argIndex], innerType));
                    } else {
                        invokeArgs[i] = Optional.empty();
                    }
                    argIndex++;
                } else {
                    invokeArgs[i] = argIndex < args.length ? coerce(args[argIndex], type) : defaultValue(type);
                    argIndex++;
                }
            }

            return invokeArgs;
        }

        private Class<?> resolveOptionalType(Type genericType) {
            if (genericType instanceof ParameterizedType parameterized) {
                final Type actual = parameterized.getActualTypeArguments()[0];
                if (actual instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return Object.class;
        }

        private Object coerce(final Object value, final Class<?> type) {
            if (value == null) {
                return defaultValue(type);
            }

            if (type.isInstance(value)) {
                return value;
            }

            if (type == int.class || type == Integer.class) {
                return ((Number) value).intValue();
            }

            if (type == long.class || type == Long.class) {
                return ((Number) value).longValue();
            }

            if (type == double.class || type == Double.class) {
                return ((Number) value).doubleValue();
            }

            if (type == float.class || type == Float.class) {
                return ((Number) value).floatValue();
            }

            if (type == short.class || type == Short.class) {
                return ((Number) value).shortValue();
            }

            if (type == byte.class || type == Byte.class) {
                return ((Number) value).byteValue();
            }

            if (type == boolean.class || type == Boolean.class) {
                return value;
            }

            if (type == String.class) {
                return String.valueOf(value);
            }

            if (type.getName().contains("Coerced")) {
                if (String.valueOf(value) != null) {
                    return new Coerced<>(String.valueOf(value));
                }
            }

            return value;
        }

        private Object defaultValue(final Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }

            if (type == boolean.class) {
                return false;
            }

            if (type == char.class) {
                return '\0';
            }

            return 0;
        }

        private Object[] wrapResult(final Object result) {
            if (result == null) {
                return new Object[0];
            }

            if (result instanceof Object[] objects) {
                return objects;
            }

            if (result.getClass().isArray()) {
                final int len = Array.getLength(result);
                final Object[] out = new Object[len];

                for (int i = 0; i < len; i++) {
                    out[i] = Array.get(result, i);
                }

                return out;
            }

            return new Object[]{result};
        }

        private Object[] resolveMethodResult(MethodResult mr) throws LuaException {
            while (mr.getCallback() != null) {
                final Object[] fakeEvent = new Object[]{"task_complete", 1L, true};

                try {
                    mr = mr.getCallback().resume(fakeEvent);
                } catch (LuaException e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaException(String.valueOf(e.getMessage()));
                }
            }

            return mr.getResult() != null ? mr.getResult() : new Object[0];
        }

        @Override
        public void onConnect(final Node node) {
            super.onConnect(node);

            if (node.host() instanceof Context && !accesses.containsKey(node.address())) {
                final FakeComputerAccess access = new FakeComputerAccess(this, (Context) node.host());

                accesses.put(node.address(), access);

                peripheral.attach(access);
            }
        }

        @Override
        public void onDisconnect(final Node node) {
            super.onDisconnect(node);

            if (node.host() instanceof Context) {
                final FakeComputerAccess access = accesses.remove(node.address());

                if (access != null) {
                    peripheral.detach(access);
                }
            } else if (node == this.node()) {
                for (FakeComputerAccess access : accesses.values()) {
                    peripheral.detach(access);
                    access.close();
                }

                accesses.clear();
            }
        }

        @Override
        public String preferredName() {
            return peripheral.getType();
        }

        @Override
        public int priority() {
            return -1;
        }

        public static class FakeComputerAccess implements IComputerAccess {
            protected final Environment owner;
            protected final Context context;
            protected final Map<String, ManagedEnvironment> fileSystems = new HashMap<>();

            public FakeComputerAccess(final Environment owner, final Context context) {
                this.owner = owner;
                this.context = context;
            }

            public void close() {
                for (ManagedEnvironment fileSystem : fileSystems.values()) {
                    fileSystem.node().remove();
                }

                fileSystems.clear();
            }

            @Override
            public String mount(final String desiredLocation, final Mount mount) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }

                return mount(desiredLocation, FileSystem.asManagedEnvironment(DriverComputerCraftMedia.fromComputerCraft(mount)));
            }

            @Override
            public String mount(final String desiredLocation, final Mount mount, final String driveName) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }

                return mount(desiredLocation, FileSystem.asManagedEnvironment(DriverComputerCraftMedia.fromComputerCraft(mount), driveName));
            }

            @Override
            public String mountWritable(final String desiredLocation, final WritableMount mount) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }

                return mount(desiredLocation, FileSystem.asManagedEnvironment(DriverComputerCraftMedia.fromComputerCraft(mount)));
            }

            @Override
            public String mountWritable(final String desiredLocation, final WritableMount mount, final String driveName) {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null;
                }

                return mount(desiredLocation, FileSystem.asManagedEnvironment(DriverComputerCraftMedia.fromComputerCraft(mount), driveName));
            }

            private String mount(final String path, final ManagedEnvironment fileSystem) {
                fileSystems.put(path, fileSystem);

                context.node().connect(fileSystem.node());

                return path;
            }

            @Override
            public void unmount(final String location) {
                final ManagedEnvironment fileSystem = fileSystems.remove(location);

                if (fileSystem != null) {
                    fileSystem.node().remove();
                }
            }

            @Override
            public int getID() {
                return context.node().address().hashCode();
            }

            @Override
            public void queueEvent(final String event, final Object... arguments) {
                context.signal(event, arguments);
            }

            @Override
            public String getAttachmentName() {
                return owner.node().address();
            }

            @Override
            public @NotNull Map<String, IPeripheral> getAvailablePeripherals() {
                return Collections.emptyMap();
            }

            @Override
            public IPeripheral getAvailablePeripheral(final String name) {
                return null;
            }

            @Override
            public @NotNull WorkMonitor getMainThreadMonitor() {
                return new WorkMonitor() {
                    @Override
                    public boolean canWork() {
                        return false;
                    }

                    @Override
                    public boolean shouldWork() {
                        return false;
                    }

                    @Override
                    public void trackWork(long l, @NotNull TimeUnit timeUnit) {

                    }
                };
            }
        }

        public static final class OCLuaContext implements ILuaContext {
            @Override
            public long issueMainThreadTask(@NotNull LuaTask luaTask) throws LuaException {
                final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

                if (server == null) {
                    throw new LuaException("Server is not available");
                }

                try {
                    server.submit(() -> {
                        try {
                            return luaTask.execute();
                        } catch (LuaException e) {
                            throw new java.util.concurrent.CompletionException(e);
                        }
                    }).get();
                } catch (java.util.concurrent.CompletionException e) {
                    if (e.getCause() instanceof LuaException le) {
                        throw le;
                    }
                    throw new LuaException(String.valueOf(e.getMessage()));
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    throw new LuaException("Main thread task failed: " + e.getMessage());
                }

                return 1L;
            }
        }
    }
}