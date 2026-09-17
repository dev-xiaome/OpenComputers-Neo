package net.minecraft.network.protocol.game;

import com.google.common.collect.Queues;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class ClientboundCommandsPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCommandsPacket> STREAM_CODEC = Packet.codec(
        ClientboundCommandsPacket::write, ClientboundCommandsPacket::new
    );
    private static final byte MASK_TYPE = 3;
    private static final byte FLAG_EXECUTABLE = 4;
    private static final byte FLAG_REDIRECT = 8;
    private static final byte FLAG_CUSTOM_SUGGESTIONS = 16;
    private static final byte TYPE_ROOT = 0;
    private static final byte TYPE_LITERAL = 1;
    private static final byte TYPE_ARGUMENT = 2;
    private final int rootIndex;
    private final List<ClientboundCommandsPacket.Entry> entries;

    public ClientboundCommandsPacket(RootCommandNode<SharedSuggestionProvider> root) {
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2intmap = enumerateNodes(root);
        this.entries = createEntries(object2intmap);
        this.rootIndex = object2intmap.getInt(root);
    }

    private ClientboundCommandsPacket(FriendlyByteBuf buffer) {
        this.entries = buffer.readList(ClientboundCommandsPacket::readNode);
        this.rootIndex = buffer.readVarInt();
        validateEntries(this.entries);
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    private void write(FriendlyByteBuf buffer) {
        buffer.writeCollection(this.entries, (p_237642_, p_237643_) -> p_237643_.write(p_237642_));
        buffer.writeVarInt(this.rootIndex);
    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> entries, BiPredicate<ClientboundCommandsPacket.Entry, IntSet> validator) {
        IntSet intset = new IntOpenHashSet(IntSets.fromTo(0, entries.size()));

        while (!intset.isEmpty()) {
            boolean flag = intset.removeIf(p_237637_ -> validator.test(entries.get(p_237637_), intset));
            if (!flag) {
                throw new IllegalStateException("Server sent an impossible command tree");
            }
        }
    }

    private static void validateEntries(List<ClientboundCommandsPacket.Entry> entries) {
        validateEntries(entries, ClientboundCommandsPacket.Entry::canBuild);
        validateEntries(entries, ClientboundCommandsPacket.Entry::canResolve);
    }

    private static Object2IntMap<CommandNode<SharedSuggestionProvider>> enumerateNodes(RootCommandNode<SharedSuggestionProvider> rootNode) {
        Object2IntMap<CommandNode<SharedSuggestionProvider>> object2intmap = new Object2IntOpenHashMap<>();
        Queue<CommandNode<SharedSuggestionProvider>> queue = Queues.newArrayDeque();
        queue.add(rootNode);

        CommandNode<SharedSuggestionProvider> commandnode;
        while ((commandnode = queue.poll()) != null) {
            if (!object2intmap.containsKey(commandnode)) {
                int i = object2intmap.size();
                object2intmap.put(commandnode, i);
                queue.addAll(commandnode.getChildren());
                if (commandnode.getRedirect() != null) {
                    queue.add(commandnode.getRedirect());
                }
            }
        }

        return object2intmap;
    }

    private static List<ClientboundCommandsPacket.Entry> createEntries(Object2IntMap<CommandNode<SharedSuggestionProvider>> nodes) {
        ObjectArrayList<ClientboundCommandsPacket.Entry> objectarraylist = new ObjectArrayList<>(nodes.size());
        objectarraylist.size(nodes.size());

        for (Object2IntMap.Entry<CommandNode<SharedSuggestionProvider>> entry : Object2IntMaps.fastIterable(nodes)) {
            objectarraylist.set(entry.getIntValue(), createEntry(entry.getKey(), nodes));
        }

        return objectarraylist;
    }

    private static ClientboundCommandsPacket.Entry readNode(FriendlyByteBuf buffer) {
        byte b0 = buffer.readByte();
        int[] aint = buffer.readVarIntArray();
        int i = (b0 & 8) != 0 ? buffer.readVarInt() : 0;
        ClientboundCommandsPacket.NodeStub clientboundcommandspacket$nodestub = read(buffer, b0);
        return new ClientboundCommandsPacket.Entry(clientboundcommandspacket$nodestub, b0, i, aint);
    }

    @Nullable
    private static ClientboundCommandsPacket.NodeStub read(FriendlyByteBuf buffer, byte flags) {
        int i = flags & 3;
        if (i == 2) {
            String s1 = buffer.readUtf();
            int j = buffer.readVarInt();
            ArgumentTypeInfo<?, ?> argumenttypeinfo = BuiltInRegistries.COMMAND_ARGUMENT_TYPE.byId(j);
            if (argumenttypeinfo == null) {
                return null;
            } else {
                ArgumentTypeInfo.Template<?> template = argumenttypeinfo.deserializeFromNetwork(buffer);
                ResourceLocation resourcelocation = (flags & 16) != 0 ? buffer.readResourceLocation() : null;
                return new ClientboundCommandsPacket.ArgumentNodeStub(s1, template, resourcelocation);
            }
        } else if (i == 1) {
            String s = buffer.readUtf();
            return new ClientboundCommandsPacket.LiteralNodeStub(s);
        } else {
            return null;
        }
    }

    private static ClientboundCommandsPacket.Entry createEntry(
        CommandNode<SharedSuggestionProvider> node, Object2IntMap<CommandNode<SharedSuggestionProvider>> nodes
    ) {
        int i = 0;
        int j;
        if (node.getRedirect() != null) {
            i |= 8;
            j = nodes.getInt(node.getRedirect());
        } else {
            j = 0;
        }

        if (node.getCommand() != null) {
            i |= 4;
        }

        ClientboundCommandsPacket.NodeStub clientboundcommandspacket$nodestub;
        if (node instanceof RootCommandNode) {
            i |= 0;
            clientboundcommandspacket$nodestub = null;
        } else if (node instanceof ArgumentCommandNode<SharedSuggestionProvider, ?> argumentcommandnode) {
            clientboundcommandspacket$nodestub = new ClientboundCommandsPacket.ArgumentNodeStub(argumentcommandnode);
            i |= 2;
            if (argumentcommandnode.getCustomSuggestions() != null) {
                i |= 16;
            }
        } else {
            if (!(node instanceof LiteralCommandNode literalcommandnode)) {
                throw new UnsupportedOperationException("Unknown node type " + node);
            }

            clientboundcommandspacket$nodestub = new ClientboundCommandsPacket.LiteralNodeStub(literalcommandnode.getLiteral());
            i |= 1;
        }

        int[] aint = node.getChildren().stream().mapToInt(nodes::getInt).toArray();
        return new ClientboundCommandsPacket.Entry(clientboundcommandspacket$nodestub, i, j, aint);
    }

    @Override
    public PacketType<ClientboundCommandsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_COMMANDS;
    }

    /**
     * Passes this Packet on to the NetHandler for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleCommands(this);
    }

    public RootCommandNode<SharedSuggestionProvider> getRoot(CommandBuildContext context) {
        return (RootCommandNode<SharedSuggestionProvider>)new ClientboundCommandsPacket.NodeResolver(context, this.entries).resolve(this.rootIndex);
    }

    static class ArgumentNodeStub implements ClientboundCommandsPacket.NodeStub {
        private final String id;
        private final ArgumentTypeInfo.Template<?> argumentType;
        @Nullable
        private final ResourceLocation suggestionId;

        @Nullable
        private static ResourceLocation getSuggestionId(@Nullable SuggestionProvider<SharedSuggestionProvider> provider) {
            return provider != null ? SuggestionProviders.getName(provider) : null;
        }

        ArgumentNodeStub(String id, ArgumentTypeInfo.Template<?> argumentType, @Nullable ResourceLocation suggestionId) {
            this.id = id;
            this.argumentType = argumentType;
            this.suggestionId = suggestionId;
        }

        public ArgumentNodeStub(ArgumentCommandNode<SharedSuggestionProvider, ?> argumentNode) {
            this(argumentNode.getName(), ArgumentTypeInfos.unpack(argumentNode.getType()), getSuggestionId(argumentNode.getCustomSuggestions()));
        }

        @Override
        public ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext context) {
            ArgumentType<?> argumenttype = this.argumentType.instantiate(context);
            RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredargumentbuilder = RequiredArgumentBuilder.argument(this.id, argumenttype);
            if (this.suggestionId != null) {
                requiredargumentbuilder.suggests(SuggestionProviders.getProvider(this.suggestionId));
            }

            return requiredargumentbuilder;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.id);
            serializeCap(buffer, this.argumentType);
            if (this.suggestionId != null) {
                buffer.writeResourceLocation(this.suggestionId);
            }
        }

        private static <A extends ArgumentType<?>> void serializeCap(FriendlyByteBuf buffer, ArgumentTypeInfo.Template<A> argumentInfoTemplate) {
            serializeCap(buffer, argumentInfoTemplate.type(), argumentInfoTemplate);
        }

        private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(
            FriendlyByteBuf buffer, ArgumentTypeInfo<A, T> argumentInfo, ArgumentTypeInfo.Template<A> argumentInfoTemplate
        ) {
            buffer.writeVarInt(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(argumentInfo));
            argumentInfo.serializeToNetwork((T)argumentInfoTemplate, buffer);
        }
    }

    static class Entry {
        @Nullable
        final ClientboundCommandsPacket.NodeStub stub;
        final int flags;
        final int redirect;
        final int[] children;

        Entry(@Nullable ClientboundCommandsPacket.NodeStub stub, int flags, int redirect, int[] children) {
            this.stub = stub;
            this.flags = flags;
            this.redirect = redirect;
            this.children = children;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeByte(this.flags);
            buffer.writeVarIntArray(this.children);
            if ((this.flags & 8) != 0) {
                buffer.writeVarInt(this.redirect);
            }

            if (this.stub != null) {
                this.stub.write(buffer);
            }
        }

        public boolean canBuild(IntSet children) {
            return (this.flags & 8) != 0 ? !children.contains(this.redirect) : true;
        }

        public boolean canResolve(IntSet children) {
            for (int i : this.children) {
                if (children.contains(i)) {
                    return false;
                }
            }

            return true;
        }
    }

    static class LiteralNodeStub implements ClientboundCommandsPacket.NodeStub {
        private final String id;

        LiteralNodeStub(String id) {
            this.id = id;
        }

        @Override
        public ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext context) {
            return LiteralArgumentBuilder.literal(this.id);
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.id);
        }
    }

    static class NodeResolver {
        private final CommandBuildContext context;
        private final List<ClientboundCommandsPacket.Entry> entries;
        private final List<CommandNode<SharedSuggestionProvider>> nodes;

        NodeResolver(CommandBuildContext context, List<ClientboundCommandsPacket.Entry> entries) {
            this.context = context;
            this.entries = entries;
            ObjectArrayList<CommandNode<SharedSuggestionProvider>> objectarraylist = new ObjectArrayList<>();
            objectarraylist.size(entries.size());
            this.nodes = objectarraylist;
        }

        public CommandNode<SharedSuggestionProvider> resolve(int index) {
            CommandNode<SharedSuggestionProvider> commandnode = this.nodes.get(index);
            if (commandnode != null) {
                return commandnode;
            } else {
                ClientboundCommandsPacket.Entry clientboundcommandspacket$entry = this.entries.get(index);
                CommandNode<SharedSuggestionProvider> commandnode1;
                if (clientboundcommandspacket$entry.stub == null) {
                    commandnode1 = new RootCommandNode<>();
                } else {
                    ArgumentBuilder<SharedSuggestionProvider, ?> argumentbuilder = clientboundcommandspacket$entry.stub.build(this.context);
                    if ((clientboundcommandspacket$entry.flags & 8) != 0) {
                        argumentbuilder.redirect(this.resolve(clientboundcommandspacket$entry.redirect));
                    }

                    if ((clientboundcommandspacket$entry.flags & 4) != 0) {
                        argumentbuilder.executes(p_237694_ -> 0);
                    }

                    commandnode1 = argumentbuilder.build();
                }

                this.nodes.set(index, commandnode1);

                for (int i : clientboundcommandspacket$entry.children) {
                    CommandNode<SharedSuggestionProvider> commandnode2 = this.resolve(i);
                    if (!(commandnode2 instanceof RootCommandNode)) {
                        commandnode1.addChild(commandnode2);
                    }
                }

                return commandnode1;
            }
        }
    }

    interface NodeStub {
        ArgumentBuilder<SharedSuggestionProvider, ?> build(CommandBuildContext context);

        void write(FriendlyByteBuf buffer);
    }
}
