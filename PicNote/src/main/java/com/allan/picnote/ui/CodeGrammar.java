package com.allan.picnote.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

import io.noties.prism4j.GrammarLocator;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.languages.*;

import static java.util.regex.Pattern.compile;
import static io.noties.prism4j.Prism4j.pattern;
import static io.noties.prism4j.Prism4j.token;

/** 加载常用语言语法，并补充 TypeScript 与 Bash 的着色规则。 */
public final class CodeGrammar implements GrammarLocator {
    private final Map<String, Prism4j.Grammar> bundled = new HashMap<>();
    private Prism4j.Grammar typescript;
    private Prism4j.Grammar bash;

    @Override
    public Prism4j.Grammar grammar(Prism4j prism4j, String language) {
        if ("typescript".equals(language) || "ts".equals(language)) {
            if (typescript == null) {
                List<Prism4j.Token> tokens = new ArrayList<>();
                Prism4j.Grammar javascript = prism4j.grammar("javascript");
                if (javascript == null) return null;
                for (Prism4j.Token entry : javascript.tokens()) {
                    if ("keyword".equals(entry.name())) {
                        List<Prism4j.Pattern> patterns = new ArrayList<>(entry.patterns());
                        patterns.add(pattern(compile("\\b(?:abstract|any|as|asserts|bigint|boolean|declare|implements|infer|interface|is|keyof|module|namespace|never|number|object|private|protected|public|readonly|require|satisfies|string|type|undefined|unique|unknown)\\b")));
                        tokens.add(token("keyword", patterns));
                    } else {
                        tokens.add(entry);
                    }
                }
                typescript = Prism4j.grammar("typescript", tokens);
            }
            return typescript;
        }
        if ("bash".equals(language) || "sh".equals(language) || "shell".equals(language)) {
            if (bash == null) {
                bash = Prism4j.grammar("bash",
                        token("comment", pattern(compile("(^|[\\s;])#[^\\r\\n]*"), true)),
                        token("string", pattern(compile("\"(?:\\\\[\\s\\S]|[^\"\\\\])*\"|'[^']*'|`(?:\\\\[\\s\\S]|[^`\\\\])*`"), false, true)),
                        token("variable", pattern(compile("\\$(?:[A-Za-z_][A-Za-z_0-9]*|[0-9@*#?$!_-]|\\{[^}]*\\})"))),
                        token("keyword", pattern(compile("\\b(?:if|then|else|elif|fi|case|esac|for|while|until|do|done|in|function|select|time|coproc)\\b"))),
                        token("builtin", pattern(compile("\\b(?:alias|bg|break|builtin|cd|command|continue|declare|echo|eval|exec|exit|export|false|fg|getopts|hash|help|history|jobs|kill|let|local|printf|pwd|read|readonly|return|set|shift|source|test|trap|true|type|typeset|ulimit|umask|unalias|unset|wait)\\b"))),
                        token("function", pattern(compile("[A-Za-z_][A-Za-z_0-9]*(?=\\s*\\(\\s*\\))"))),
                        token("number", pattern(compile("\\b(?:0x[\\da-fA-F]+|\\d+(?:\\.\\d+)?)\\b"))),
                        token("operator", pattern(compile("&&|\\|\\||<<-?|>>?|[=|&;!<>]"))),
                        token("punctuation", pattern(compile("[{}\\[\\]();]")))
                );
            }
            return bash;
        }
        String name;
        switch (language) {
            case "js": name = "javascript"; break;
            case "xml": case "html": case "svg": case "mathml": name = "markup"; break;
            default: name = language;
        }
        if (bundled.containsKey(name)) return bundled.get(name);
        Prism4j.Grammar result;
        switch (name) {
            case "clike": result = Prism_clike.create(prism4j); break;
            case "java": result = Prism_java.create(prism4j); break;
            case "kotlin": result = Prism_kotlin.create(prism4j); break;
            case "c": result = Prism_c.create(prism4j); break;
            case "cpp": result = Prism_cpp.create(prism4j); break;
            case "markup": result = Prism_markup.create(prism4j); break;
            case "swift": result = Prism_swift.create(prism4j); break;
            case "go": result = Prism_go.create(prism4j); break;
            case "javascript": result = Prism_javascript.create(prism4j); break;
            case "python": result = Prism_python.create(prism4j); break;
            case "json": result = Prism_json.create(prism4j); break;
            case "css": result = Prism_css.create(prism4j); break;
            case "sql": result = Prism_sql.create(prism4j); break;
            case "yaml": result = Prism_yaml.create(prism4j); break;
            default: result = null;
        }
        bundled.put(name, result);
        if ("markup".equals(name)) {
            prism4j.grammar("css");
            prism4j.grammar("javascript");
        }
        return result;
    }

    @Override
    public Set<String> languages() {
        Set<String> result = new HashSet<>(java.util.Arrays.asList(
                "clike", "java", "kotlin", "c", "cpp", "markup", "xml", "html", "svg", "mathml",
                "swift", "go", "javascript", "js", "python", "json", "css", "sql", "yaml"));
        result.add("typescript");
        result.add("ts");
        result.add("bash");
        result.add("sh");
        result.add("shell");
        return result;
    }
}
