package com.mojang.blaze3d.preprocessor;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class GlslPreprocessor {
    private static final String C_COMMENT = "/\\*(?:[^*]|\\*+[^*/])*\\*+/";
    private static final String LINE_COMMENT = "//[^\\v]*";
    private static final Pattern REGEX_MOJ_IMPORT = Pattern.compile(
        "(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*moj_import(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(?:\"(.*)\"|<(.*)>))"
    );
    private static final Pattern REGEX_VERSION = Pattern.compile(
        "(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*version(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(\\d+))\\b"
    );
    private static final Pattern REGEX_ENDS_WITH_WHITESPACE = Pattern.compile("(?:^|\\v)(?:\\s|/\\*(?:[^*]|\\*+[^*/])*\\*+/|(//[^\\v]*))*\\z");

    public List<String> process(String shaderData) {
        GlslPreprocessor.Context glslpreprocessor$context = new GlslPreprocessor.Context();
        List<String> list = this.processImports(shaderData, glslpreprocessor$context, "");
        list.set(0, this.setVersion(list.get(0), glslpreprocessor$context.glslVersion));
        return list;
    }

    private List<String> processImports(String shaderData, GlslPreprocessor.Context context, String includeDirectory) {
        int i = context.sourceId;
        int j = 0;
        String s = "";
        List<String> list = Lists.newArrayList();
        Matcher matcher = REGEX_MOJ_IMPORT.matcher(shaderData);

        while (matcher.find()) {
            if (!isDirectiveDisabled(shaderData, matcher, j)) {
                String s1 = matcher.group(2);
                boolean flag = s1 != null;
                if (!flag) {
                    s1 = matcher.group(3);
                }

                if (s1 != null) {
                    String s2 = shaderData.substring(j, matcher.start(1));
                    String s3 = includeDirectory + s1;
                    String s4 = this.applyImport(flag, s3);
                    if (!Strings.isNullOrEmpty(s4)) {
                        if (!StringUtil.endsWithNewLine(s4)) {
                            s4 = s4 + System.lineSeparator();
                        }

                        context.sourceId++;
                        int k = context.sourceId;
                        List<String> list1 = this.processImports(s4, context, flag ? FileUtil.getFullResourcePath(s3) : "");
                        list1.set(0, String.format(Locale.ROOT, "#line %d %d\n%s", 0, k, this.processVersions(list1.get(0), context)));
                        if (!StringUtil.isBlank(s2)) {
                            list.add(s2);
                        }

                        list.addAll(list1);
                    } else {
                        String s6 = flag ? String.format(Locale.ROOT, "/*#moj_import \"%s\"*/", s1) : String.format(Locale.ROOT, "/*#moj_import <%s>*/", s1);
                        list.add(s + s2 + s6);
                    }

                    int l = StringUtil.lineCount(shaderData.substring(0, matcher.end(1)));
                    s = String.format(Locale.ROOT, "#line %d %d", l, i);
                    j = matcher.end(1);
                }
            }
        }

        String s5 = shaderData.substring(j);
        if (!StringUtil.isBlank(s5)) {
            list.add(s + s5);
        }

        return list;
    }

    private String processVersions(String versionData, GlslPreprocessor.Context context) {
        Matcher matcher = REGEX_VERSION.matcher(versionData);
        if (matcher.find() && isDirectiveEnabled(versionData, matcher)) {
            context.glslVersion = Math.max(context.glslVersion, Integer.parseInt(matcher.group(2)));
            return versionData.substring(0, matcher.start(1))
                + "/*"
                + versionData.substring(matcher.start(1), matcher.end(1))
                + "*/"
                + versionData.substring(matcher.end(1));
        } else {
            return versionData;
        }
    }

    private String setVersion(String versionData, int glslVersion) {
        Matcher matcher = REGEX_VERSION.matcher(versionData);
        return matcher.find() && isDirectiveEnabled(versionData, matcher)
            ? versionData.substring(0, matcher.start(2)) + Math.max(glslVersion, Integer.parseInt(matcher.group(2))) + versionData.substring(matcher.end(2))
            : versionData;
    }

    private static boolean isDirectiveEnabled(String shaderData, Matcher matcher) {
        return !isDirectiveDisabled(shaderData, matcher, 0);
    }

    private static boolean isDirectiveDisabled(String shaderData, Matcher p_matcher, int offset) {
        int i = p_matcher.start() - offset;
        if (i == 0) {
            return false;
        } else {
            Matcher matcher = REGEX_ENDS_WITH_WHITESPACE.matcher(shaderData.substring(offset, p_matcher.start()));
            if (!matcher.find()) {
                return true;
            } else {
                int j = matcher.end(1);
                return j == p_matcher.start();
            }
        }
    }

    @Nullable
    public abstract String applyImport(boolean useFullPath, String directory);

    @OnlyIn(Dist.CLIENT)
    static final class Context {
        int glslVersion;
        int sourceId;
    }
}
