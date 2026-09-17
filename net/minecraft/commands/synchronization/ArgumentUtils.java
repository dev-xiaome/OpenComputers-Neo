package net.minecraft.commands.synchronization;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

public class ArgumentUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte NUMBER_FLAG_MIN = 1;
    private static final byte NUMBER_FLAG_MAX = 2;

    public static int createNumberFlags(boolean min, boolean max) {
        int i = 0;
        if (min) {
            i |= 1;
        }

        if (max) {
            i |= 2;
        }

        return i;
    }

    public static boolean numberHasMin(byte number) {
        return (number & 1) != 0;
    }

    public static boolean numberHasMax(byte number) {
        return (number & 2) != 0;
    }

    private static <A extends ArgumentType<?>> void serializeCap(JsonObject json, ArgumentTypeInfo.Template<A> template) {
        serializeCap(json, template.type(), template);
    }

    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeCap(
        JsonObject json, ArgumentTypeInfo<A, T> argumentTypeInfo, ArgumentTypeInfo.Template<A> template
    ) {
        argumentTypeInfo.serializeToJson((T)template, json);
    }

    private static <T extends ArgumentType<?>> void serializeArgumentToJson(JsonObject json, T type) {
        ArgumentTypeInfo.Template<T> template = ArgumentTypeInfos.unpack(type);
        json.addProperty("type", "argument");
        json.addProperty("parser", BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(template.type()).toString());
        JsonObject jsonobject = new JsonObject();
        serializeCap(jsonobject, template);
        if (jsonobject.size() > 0) {
            json.add("properties", jsonobject);
        }
    }

    public static <S> JsonObject serializeNodeToJson(CommandDispatcher<S> dispatcher, CommandNode<S> node) {
        JsonObject jsonobject = new JsonObject();
        if (node instanceof RootCommandNode) {
            jsonobject.addProperty("type", "root");
        } else if (node instanceof LiteralCommandNode) {
            jsonobject.addProperty("type", "literal");
        } else if (node instanceof ArgumentCommandNode<?, ?> argumentcommandnode) {
            serializeArgumentToJson(jsonobject, argumentcommandnode.getType());
        } else {
            LOGGER.error("Could not serialize node {} ({})!", node, node.getClass());
            jsonobject.addProperty("type", "unknown");
        }

        JsonObject jsonobject1 = new JsonObject();

        for (CommandNode<S> commandnode : node.getChildren()) {
            jsonobject1.add(commandnode.getName(), serializeNodeToJson(dispatcher, commandnode));
        }

        if (jsonobject1.size() > 0) {
            jsonobject.add("children", jsonobject1);
        }

        if (node.getCommand() != null) {
            jsonobject.addProperty("executable", true);
        }

        if (node.getRedirect() != null) {
            Collection<String> collection = dispatcher.getPath(node.getRedirect());
            if (!collection.isEmpty()) {
                JsonArray jsonarray = new JsonArray();

                for (String s : collection) {
                    jsonarray.add(s);
                }

                jsonobject.add("redirect", jsonarray);
            }
        }

        return jsonobject;
    }

    public static <T> Set<ArgumentType<?>> findUsedArgumentTypes(CommandNode<T> node) {
        Set<CommandNode<T>> set = Sets.newIdentityHashSet();
        Set<ArgumentType<?>> set1 = Sets.newHashSet();
        findUsedArgumentTypes(node, set1, set);
        return set1;
    }

    private static <T> void findUsedArgumentTypes(CommandNode<T> node, Set<ArgumentType<?>> types, Set<CommandNode<T>> nodes) {
        if (nodes.add(node)) {
            if (node instanceof ArgumentCommandNode<?, ?> argumentcommandnode) {
                types.add(argumentcommandnode.getType());
            }

            node.getChildren().forEach(p_235426_ -> findUsedArgumentTypes((CommandNode<T>)p_235426_, types, nodes));
            CommandNode<T> commandnode = node.getRedirect();
            if (commandnode != null) {
                findUsedArgumentTypes(commandnode, types, nodes);
            }
        }
    }
}
