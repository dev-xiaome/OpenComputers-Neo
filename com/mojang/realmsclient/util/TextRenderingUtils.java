package com.mojang.realmsclient.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextRenderingUtils {
    private TextRenderingUtils() {
    }

    @VisibleForTesting
    protected static List<String> lineBreak(String text) {
        return Arrays.asList(text.split("\\n"));
    }

    public static List<TextRenderingUtils.Line> decompose(String text, TextRenderingUtils.LineSegment... segments) {
        return decompose(text, Arrays.asList(segments));
    }

    private static List<TextRenderingUtils.Line> decompose(String text, List<TextRenderingUtils.LineSegment> segments) {
        List<String> list = lineBreak(text);
        return insertLinks(list, segments);
    }

    private static List<TextRenderingUtils.Line> insertLinks(List<String> lines, List<TextRenderingUtils.LineSegment> segments) {
        int i = 0;
        List<TextRenderingUtils.Line> list = Lists.newArrayList();

        for (String s : lines) {
            List<TextRenderingUtils.LineSegment> list1 = Lists.newArrayList();

            for (String s1 : split(s, "%link")) {
                if ("%link".equals(s1)) {
                    list1.add(segments.get(i++));
                } else {
                    list1.add(TextRenderingUtils.LineSegment.text(s1));
                }
            }

            list.add(new TextRenderingUtils.Line(list1));
        }

        return list;
    }

    public static List<String> split(String toSplit, String delimiter) {
        if (delimiter.isEmpty()) {
            throw new IllegalArgumentException("Delimiter cannot be the empty string");
        } else {
            List<String> list = Lists.newArrayList();
            int i = 0;

            int j;
            while ((j = toSplit.indexOf(delimiter, i)) != -1) {
                if (j > i) {
                    list.add(toSplit.substring(i, j));
                }

                list.add(delimiter);
                i = j + delimiter.length();
            }

            if (i < toSplit.length()) {
                list.add(toSplit.substring(i));
            }

            return list;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Line {
        public final List<TextRenderingUtils.LineSegment> segments;

        Line(TextRenderingUtils.LineSegment... segments) {
            this(Arrays.asList(segments));
        }

        Line(List<TextRenderingUtils.LineSegment> segments) {
            this.segments = segments;
        }

        @Override
        public String toString() {
            return "Line{segments=" + this.segments + "}";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                TextRenderingUtils.Line textrenderingutils$line = (TextRenderingUtils.Line)other;
                return Objects.equals(this.segments, textrenderingutils$line.segments);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.segments);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class LineSegment {
        private final String fullText;
        @Nullable
        private final String linkTitle;
        @Nullable
        private final String linkUrl;

        private LineSegment(String fullText) {
            this.fullText = fullText;
            this.linkTitle = null;
            this.linkUrl = null;
        }

        private LineSegment(String fullText, @Nullable String linkTitle, @Nullable String linkUrl) {
            this.fullText = fullText;
            this.linkTitle = linkTitle;
            this.linkUrl = linkUrl;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else if (other != null && this.getClass() == other.getClass()) {
                TextRenderingUtils.LineSegment textrenderingutils$linesegment = (TextRenderingUtils.LineSegment)other;
                return Objects.equals(this.fullText, textrenderingutils$linesegment.fullText)
                    && Objects.equals(this.linkTitle, textrenderingutils$linesegment.linkTitle)
                    && Objects.equals(this.linkUrl, textrenderingutils$linesegment.linkUrl);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.fullText, this.linkTitle, this.linkUrl);
        }

        @Override
        public String toString() {
            return "Segment{fullText='" + this.fullText + "', linkTitle='" + this.linkTitle + "', linkUrl='" + this.linkUrl + "'}";
        }

        public String renderedText() {
            return this.isLink() ? this.linkTitle : this.fullText;
        }

        public boolean isLink() {
            return this.linkTitle != null;
        }

        public String getLinkUrl() {
            if (!this.isLink()) {
                throw new IllegalStateException("Not a link: " + this);
            } else {
                return this.linkUrl;
            }
        }

        public static TextRenderingUtils.LineSegment link(String linkTitle, String linkUrl) {
            return new TextRenderingUtils.LineSegment(null, linkTitle, linkUrl);
        }

        @VisibleForTesting
        protected static TextRenderingUtils.LineSegment text(String fullText) {
            return new TextRenderingUtils.LineSegment(fullText);
        }
    }
}
