package com.allan.picnote.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.prism4j.Prism4j;
import io.noties.prism4j.SyntaxImpl;
import io.noties.prism4j.TextImpl;

/** 区分 JSX 标签中的文本与花括号表达式，避免普通文字被当成 JavaScript 关键字。 */
public final class JsxSyntaxTokens {
    private static final Pattern TAG_NAME = Pattern.compile("^</?([\\w.:-]*)");
    private static final Pattern TEXT_BOUNDARY = Pattern.compile("\\{|</?(?:[a-zA-Z_$]|(?=>))");

    private JsxSyntaxTokens() { }

    public static List<Prism4j.Node> normalize(List<? extends Prism4j.Node> nodes,
                                             Prism4j prism4j, Prism4j.Grammar grammar, String source) {
        List<Prism4j.Node> result = new ArrayList<>(nodes.size());
        List<TagState> tags = new ArrayList<>();
        int offset = 0;
        int rescanBudget = source.length() * 4;
        for (int i = 0; i < nodes.size(); i++) {
            Prism4j.Node node = nodes.get(i);
            Prism4j.Syntax syntax = node instanceof Prism4j.Syntax ? (Prism4j.Syntax) node : null;
            String raw = syntax != null ? syntax.matchedString() : ((Prism4j.Text) node).literal();
            TagState current = tags.isEmpty() ? null : tags.get(tags.size() - 1);
            if (syntax != null && "tag".equals(syntax.type()) && !syntax.children().isEmpty()
                    && syntax.children().get(0) instanceof Prism4j.Syntax
                    && "tag".equals(((Prism4j.Syntax) syntax.children().get(0)).type())) {
                Matcher match = TAG_NAME.matcher(raw);
                if (match.find()) {
                    String name = match.group(1);
                    if (raw.startsWith("</")) {
                        if (current != null && current.name.equals(name)) tags.remove(tags.size() - 1);
                    } else if (!raw.endsWith("/>") && hasClosingTag(source, name, offset + raw.length())) {
                        tags.add(new TagState(name));
                    }
                }
            } else if (current != null && syntax != null && "punctuation".equals(syntax.type()) && "{".equals(raw)) {
                current.braces += 1;
            } else if (current != null && current.braces > 0 && syntax != null && "punctuation".equals(syntax.type()) && "}".equals(raw)) {
                current.braces -= 1;
            } else if (current != null && current.braces == 0) {
                // 文本中的引号和斜杠可能被脚本语法连同后续标签吞掉。从被遮住的边界恢复解析。
                if (syntax != null && raw.length() > 1) {
                    Matcher boundary = TEXT_BOUNDARY.matcher(raw);
                    if (boundary.find(1)) {
                        int restart = offset + boundary.start();
                        int remaining = source.length() - restart;
                        if (remaining <= rescanBudget) {
                            result.add(new TextImpl(raw.substring(0, boundary.start())));
                            rescanBudget -= remaining;
                            offset = restart;
                            nodes = prism4j.tokenize(source.substring(restart), grammar);
                            i = -1;
                            continue;
                        }
                    }
                }
                result.add(new TextImpl(raw));
                offset += raw.length();
                continue;
            }
            result.add(syntax == null || !syntax.tokenized() ? node : new SyntaxImpl(syntax.type(),
                    normalize(syntax.children(), prism4j, grammar, raw), syntax.alias(), raw, syntax.greedy(), true));
            offset += raw.length();
        }
        return result;
    }

    private static boolean hasClosingTag(String source, String name, int start) {
        String prefix = "</" + name;
        int index = source.indexOf(prefix, start);
        while (index >= 0) {
            int end = index + prefix.length();
            while (end < source.length() && Character.isWhitespace(source.charAt(end))) end++;
            if (end < source.length() && source.charAt(end) == '>') return true;
            index = source.indexOf(prefix, index + prefix.length());
        }
        return false;
    }

    private static final class TagState {
        final String name;
        int braces;

        TagState(String name) { this.name = name; }
    }
}
