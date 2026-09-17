package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.ScoreAccess;

public class OperationArgument implements ArgumentType<OperationArgument.Operation> {
    private static final Collection<String> EXAMPLES = Arrays.asList("=", ">", "<");
    private static final SimpleCommandExceptionType ERROR_INVALID_OPERATION = new SimpleCommandExceptionType(
        Component.translatable("arguments.operation.invalid")
    );
    private static final SimpleCommandExceptionType ERROR_DIVIDE_BY_ZERO = new SimpleCommandExceptionType(Component.translatable("arguments.operation.div0"));

    public static OperationArgument operation() {
        return new OperationArgument();
    }

    public static OperationArgument.Operation getOperation(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, OperationArgument.Operation.class);
    }

    public OperationArgument.Operation parse(StringReader reader) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw ERROR_INVALID_OPERATION.createWithContext(reader);
        } else {
            int i = reader.getCursor();

            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }

            return getOperation(reader.getString().substring(i, reader.getCursor()));
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(new String[]{"=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><"}, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /**
     * Makes an {@link net.minecraft.commands.arguments.OperationArgument.Operation} instance based on the given name. This method handles all operations.
     */
    private static OperationArgument.Operation getOperation(String name) throws CommandSyntaxException {
        return (name.equals("><") ? (p_313447_, p_313448_) -> {
            int i = p_313447_.get();
            p_313447_.set(p_313448_.get());
            p_313448_.set(i);
        } : getSimpleOperation(name));
    }

    /**
     * Makes an {@link net.minecraft.commands.arguments.OperationArgument.Operation} instance based on the given name. This method actually returns {@link net.minecraft.commands.arguments.OperationArgument.SimpleOperation}, which is used as a functional interface target with 2 ints. It handles all operations other than swap (><).
     */
    private static OperationArgument.SimpleOperation getSimpleOperation(String name) throws CommandSyntaxException {
        return switch (name) {
            case "=" -> (p_103298_, p_103299_) -> p_103299_;
            case "+=" -> Integer::sum;
            case "-=" -> (p_103292_, p_103293_) -> p_103292_ - p_103293_;
            case "*=" -> (p_103289_, p_103290_) -> p_103289_ * p_103290_;
            case "/=" -> (p_264713_, p_264714_) -> {
            if (p_264714_ == 0) {
                throw ERROR_DIVIDE_BY_ZERO.create();
            } else {
                return Mth.floorDiv(p_264713_, p_264714_);
            }
        };
            case "%=" -> (p_103271_, p_103272_) -> {
            if (p_103272_ == 0) {
                throw ERROR_DIVIDE_BY_ZERO.create();
            } else {
                return Mth.positiveModulo(p_103271_, p_103272_);
            }
        };
            case "<" -> Math::min;
            case ">" -> Math::max;
            default -> throw ERROR_INVALID_OPERATION.create();
        };
    }

    @FunctionalInterface
    public interface Operation {
        void apply(ScoreAccess targetScore, ScoreAccess sourceScore) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface SimpleOperation extends OperationArgument.Operation {
        int apply(int targetScore, int sourceScore) throws CommandSyntaxException;

        @Override
        default void apply(ScoreAccess targetScore, ScoreAccess sourceScore) throws CommandSyntaxException {
            targetScore.set(this.apply(targetScore.get(), sourceScore.get()));
        }
    }
}
