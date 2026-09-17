package net.minecraft.network.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.SignedArgument;

public record SignableCommand<S>(List<SignableCommand.Argument<S>> arguments) {
    public static <S> boolean hasSignableArguments(ParseResults<S> parseResults) {
        return !of(parseResults).arguments().isEmpty();
    }

    public static <S> SignableCommand<S> of(ParseResults<S> results) {
        String s = results.getReader().getString();
        CommandContextBuilder<S> commandcontextbuilder = results.getContext();
        CommandContextBuilder<S> commandcontextbuilder1 = commandcontextbuilder;
        List<SignableCommand.Argument<S>> list = collectArguments(s, commandcontextbuilder);

        CommandContextBuilder<S> commandcontextbuilder2;
        while (
            // Neo: Check if the command node is a RootCommandNode, instead of simply being the original root node; fixes #186
            (commandcontextbuilder2 = commandcontextbuilder1.getChild()) != null && !(commandcontextbuilder2.getRootNode() instanceof com.mojang.brigadier.tree.RootCommandNode)
        ) {
            list.addAll(collectArguments(s, commandcontextbuilder2));
            commandcontextbuilder1 = commandcontextbuilder2;
        }

        return new SignableCommand<>(list);
    }

    private static <S> List<SignableCommand.Argument<S>> collectArguments(String key, CommandContextBuilder<S> contextBuilder) {
        List<SignableCommand.Argument<S>> list = new ArrayList<>();

        for (ParsedCommandNode<S> parsedcommandnode : contextBuilder.getNodes()) {
            CommandNode $$5 = parsedcommandnode.getNode();
            if ($$5 instanceof ArgumentCommandNode) {
                ArgumentCommandNode<S, ?> argumentcommandnode = (ArgumentCommandNode<S, ?>)$$5;
                if (argumentcommandnode.getType() instanceof SignedArgument) {
                    ParsedArgument<S, ?> parsedargument = contextBuilder.getArguments().get(argumentcommandnode.getName());
                    if (parsedargument != null) {
                        String s = parsedargument.getRange().get(key);
                        list.add(new SignableCommand.Argument<>(argumentcommandnode, s));
                    }
                }
            }
        }

        return list;
    }

    @Nullable
    public SignableCommand.Argument<S> getArgument(String p_argument) {
        for (SignableCommand.Argument<S> argument : this.arguments) {
            if (p_argument.equals(argument.name())) {
                return argument;
            }
        }

        return null;
    }

    public static record Argument<S>(ArgumentCommandNode<S, ?> node, String value) {
        public String name() {
            return this.node.getName();
        }
    }
}
