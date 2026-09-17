package net.minecraft.commands.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;

public record StringTemplate(List<String> segments, List<String> variables) {
    public static StringTemplate fromString(String name, int lineNumber) {
        Builder<String> builder = ImmutableList.builder();
        Builder<String> builder1 = ImmutableList.builder();
        int i = name.length();
        int j = 0;
        int k = name.indexOf(36);

        while (k != -1) {
            if (k != i - 1 && name.charAt(k + 1) == '(') {
                builder.add(name.substring(j, k));
                int l = name.indexOf(41, k + 1);
                if (l == -1) {
                    throw new IllegalArgumentException("Unterminated macro variable in macro '" + name + "' on line " + lineNumber);
                }

                String s = name.substring(k + 2, l);
                if (!isValidVariableName(s)) {
                    throw new IllegalArgumentException("Invalid macro variable name '" + s + "' on line " + lineNumber);
                }

                builder1.add(s);
                j = l + 1;
                k = name.indexOf(36, j);
            } else {
                k = name.indexOf(36, k + 1);
            }
        }

        if (j == 0) {
            throw new IllegalArgumentException("Macro without variables on line " + lineNumber);
        } else {
            if (j != i) {
                builder.add(name.substring(j));
            }

            return new StringTemplate(builder.build(), builder1.build());
        }
    }

    private static boolean isValidVariableName(String variableName) {
        for (int i = 0; i < variableName.length(); i++) {
            char c0 = variableName.charAt(i);
            if (!Character.isLetterOrDigit(c0) && c0 != '_') {
                return false;
            }
        }

        return true;
    }

    public String substitute(List<String> arguments) {
        StringBuilder stringbuilder = new StringBuilder();

        for (int i = 0; i < this.variables.size(); i++) {
            stringbuilder.append(this.segments.get(i)).append(arguments.get(i));
            CommandFunction.checkCommandLineLength(stringbuilder);
        }

        if (this.segments.size() > this.variables.size()) {
            stringbuilder.append(this.segments.get(this.segments.size() - 1));
        }

        CommandFunction.checkCommandLineLength(stringbuilder);
        return stringbuilder.toString();
    }
}
