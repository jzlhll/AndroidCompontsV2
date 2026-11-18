package com.allan.picnote.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.prism4j.GrammarUtils;
import io.noties.prism4j.Prism4j;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;
import static io.noties.prism4j.Prism4j.grammar;
import static io.noties.prism4j.Prism4j.pattern;
import static io.noties.prism4j.Prism4j.token;

/** 补齐 Prism4j 未打包的语法。规则参考 PrismJS，授权见 assets/licenses/PrismJS-LICENSE。 */
public final class ExtendedCodeGrammars {
    private static final String QUOTED = "\"(?:\\\\[\\s\\S]|[^\\\\\"]++)*+\"|'(?:\\\\[\\s\\S]|[^\\\\']++)*+'";
    private static final String BACKTICK_QUOTED = "\\x60(?:\\\\[\\s\\S]|[^\\\\\\x60]++)*+\\x60";
    private static final String PHP_OPEN = "<\\?(?:php(?=\\s|$)|=|(?=\\s|$))";
    private static final String PHP_COMMENT = "(?://|#(?!\\[))(?:[^?\\r\\n]++|\\?(?!>))*+";
    private static final Pattern PHP_OPEN_PATTERN = compile(PHP_OPEN, CASE_INSENSITIVE);
    private static final Pattern PHP_MARKUP_START = compile(
            PHP_OPEN + "|<!--|<!doctype\\b|<\\?xml\\b|</?[a-z][\\w:-]*(?=[\\s>/])|"
                    + QUOTED + "|" + BACKTICK_QUOTED + "|/\\*[\\s\\S]*?(?:\\*/|$)|" + PHP_COMMENT
                    + "|" + phpDocument("Scan", "['\"]?"), CASE_INSENSITIVE | MULTILINE);

    private ExtendedCodeGrammars() { }

    public static Prism4j.Grammar rust() {
        String comment = "/\\*(?:[^*/]++|\\*(?!/)|/(?!\\*)|<self>)*+\\*/";
        for (int i = 0; i < 2; i++) comment = comment.replace("<self>", comment);
        comment = comment.replace("<self>", "(?!)");
        return grammar("rust",
                token("comment", pattern(compile(comment), false, true), pattern(compile("//[^\\r\\n]*"), false, true)),
                token("string", pattern(compile("(?:b|c)?r(#{0,255})\"[\\s\\S]*?\"\\1(?!#)|(?:b|c)?\"(?:\\\\[\\s\\S]|[^\\\\\"]++)*+\""), false, true)),
                token("char", pattern(compile("b?'(?:\\\\(?:x[0-7][\\da-fA-F]|u\\{[\\da-fA-F_]+\\}|.)|[^\\\\\\r\\n\\t'])'"), false, true)),
                token("attribute", pattern(compile("#!?\\[(?:[^\\[\\]\"]++|\"(?:\\\\[\\s\\S]|[^\\\\\"]++)*+\")*+\\]"), false, true, "attr-name")),
                token("lifetime-annotation", pattern(compile("'\\w+"), false, false, "symbol")),
                token("macro", pattern(compile("\\b\\w+!"), false, false, "function")),
                token("keyword", pattern(compile("\\b(?:Self|abstract|as|async|await|become|box|break|const|continue|crate|do|dyn|else|enum|extern|final|fn|for|gen|if|impl|in|let|loop|macro|match|mod|move|mut|override|priv|pub|ref|return|self|static|struct|super|trait|try|type|typeof|union|unsafe|unsized|use|virtual|where|while|yield|bool|char|f32|f64|[ui](?:8|16|32|64|128|size)|str)\\b"))),
                token("boolean", pattern(compile("\\b(?:true|false)\\b"))),
                token("function", pattern(compile("\\b[a-z_]\\w*(?=\\s*(?:::\\s*<|\\())"))),
                token("constant", pattern(compile("\\b[A-Z_][A-Z_\\d]+\\b"))),
                token("class-name", pattern(compile("\\b[A-Z]\\w*\\b"))),
                token("number", pattern(compile("\\b(?:0x[\\da-fA-F_]+|0o[0-7_]+|0b[01_]+|\\d[\\d_]*(?:\\.(?!\\.)[\\d_]*)?(?:[eE][+-]?[\\d_]+)?)(?:_?(?:f32|f64|[iu](?:8|16|32|64|128|size)))?\\b"))),
                token("variable", pattern(compile("\\$\\w+"))),
                token("punctuation", pattern(compile("->|\\.\\.=|\\.{1,3}|::|[{}\\[\\];(),:]"))),
                token("operator", pattern(compile("[-+*/%!^]=?|=[=>]?|&[&=]?|\\|[|=]?|<<?=?|>>?=?|[@?]")))
        );
    }

    public static boolean hasPhpMarkup(String code) {
        if (!PHP_OPEN_PATTERN.matcher(code).find()) return false;
        Matcher start = PHP_MARKUP_START.matcher(code);
        while (start.find()) {
            if (code.charAt(start.start()) == '<' && !code.startsWith("<<<", start.start())) return true;
        }
        return false;
    }

    private static String phpDocument(String suffix, String quote) {
        return "<<<\\h*(?<quote" + suffix + ">" + quote + ")(?<label" + suffix + ">[a-zA-Z_]\\w*)"
                + "\\k<quote" + suffix + ">\\h*\\R[\\s\\S]*?^\\h*(?-i:\\k<label" + suffix + ">)(?!\\w)";
    }

    private static String phpBlock(String suffix) {
        return PHP_OPEN + "(?:" + QUOTED + "|" + BACKTICK_QUOTED + "|" + phpDocument(suffix, "['\"]?")
                + "|/\\*[\\s\\S]*?(?:\\*/|$)|" + PHP_COMMENT
                + "|/(?![*/])|#\\[|\\?(?!>)|<|[^\"'\\x60/#?<]++)*+(?:\\?>|$)";
    }

    public static Prism4j.Grammar php() {
        Prism4j.Grammar interpolation = grammar("php-interpolation",
                token("variable", pattern(compile("\\$+(?:\\w+|\\{[^}]+\\})"))));
        return grammar("php",
                token("comment", pattern(compile("/\\*[\\s\\S]*?\\*/|" + PHP_COMMENT), false, true)),
                token("string",
                        pattern(compile(phpDocument("Nowdoc", "'"), MULTILINE), false, true),
                        pattern(compile(phpDocument("Heredoc", "\"?"), MULTILINE), false, true, null, interpolation),
                        pattern(compile("'(?:\\\\[\\s\\S]|[^\\\\']++)*+'"), false, true),
                        pattern(compile("\"(?:\\\\[\\s\\S]|[^\\\\\"]++)*+\"|" + BACKTICK_QUOTED),
                                false, true, null, interpolation)),
                token("delimiter", pattern(compile(PHP_OPEN + "|\\?>", CASE_INSENSITIVE), false, false, "important")),
                token("attribute", pattern(compile("#\\[[^\\]]*\\]"), false, true, "attr-name")),
                token("variable", pattern(compile("\\$+(?:\\w+\\b|(?=\\{))"))),
                token("keyword", pattern(compile("\\b(?:abstract|and|array|as|bool|boolean|break|callable|case|catch|class|clone|const|continue|declare|default|die|do|echo|else|elseif|empty|enddeclare|endfor|endforeach|endif|endswitch|endwhile|enum|eval|exit|extends|final|finally|float|fn|for|foreach|function|global|goto|if|implements|include|include_once|instanceof|insteadof|int|integer|interface|isset|iterable|list|match|mixed|namespace|never|new|object|or|parent|print|private|protected|public|readonly|require|require_once|return|self|static|string|switch|throw|trait|try|unset|use|var|void|while|xor|yield|__halt_compiler)\\b", CASE_INSENSITIVE))),
                token("boolean", pattern(compile("\\b(?:false|true|null)\\b", CASE_INSENSITIVE))),
                token("function", pattern(compile("\\b[a-z_]\\w*(?=\\s*\\()", CASE_INSENSITIVE))),
                token("class-name", pattern(compile("\\b[A-Z]\\w*(?=\\s*(?:\\$|::|\\{))"))),
                token("constant", pattern(compile("\\b[A-Z_][A-Z_\\d]*\\b"))),
                token("number", pattern(compile("\\b(?:0b[01_]+|0o[0-7_]+|0x[\\da-f_]+|\\d[\\d_]*(?:\\.[\\d_]*)?(?:e[+-]?[\\d_]+)?)\\b", CASE_INSENSITIVE))),
                token("operator", pattern(compile("<=>|\\?\\?=?|\\.{3}|\\??->|[!=]=?=?|::|\\*\\*=?|--|\\+\\+|&&|\\|\\||<<|>>|[?~]|[/^|%*&<>.+=-]=?"))),
                token("punctuation", pattern(compile("[{}\\[\\](),:;\\\\]")))
        );
    }

    public static Prism4j.Grammar phpMarkup(Prism4j prism4j) {
        int flags = CASE_INSENSITIVE | MULTILINE;
        Prism4j.Token phpBlock = token("php-block", pattern(compile(phpBlock("Block"), flags),
                false, true, null, GrammarUtils.require(prism4j, "php")));
        Prism4j.Grammar markup = GrammarUtils.clone(GrammarUtils.require(prism4j, "markup"));
        // HTML 属性作为整体识别，再进入属性内的 PHP 区域。
        Prism4j.Token tag = GrammarUtils.findToken(markup, "tag");
        Prism4j.Grammar tagInside = tag.patterns().get(0).inside();
        String attributeValue = "([\"'])(?:" + phpBlock("Quoted") + "|\\\\[\\s\\S]|(?!\\1)[^\\\\])*+\\1"
                + "|(?:" + phpBlock("Unquoted") + "|[^\\s'\"<>=])++";
        tag.patterns().clear();
        tag.patterns().add(pattern(compile("</?[a-z][\\w:.-]*(?:\\s+[^\\s>/=<]++(?:\\s*=\\s*(?:"
                + attributeValue + "))?+)*+\\s*/?>", flags), false, true, null, tagInside));
        Prism4j.Token value = GrammarUtils.findToken(tagInside, "attr-value");
        Prism4j.Grammar valueInside = value.patterns().get(0).inside();
        valueInside.tokens().add(0, phpBlock);
        value.patterns().clear();
        value.patterns().add(pattern(compile("=\\s*(?:" + attributeValue + ")", flags), false, false, null, valueInside));
        GrammarUtils.insertBeforeToken(markup, "prolog", phpBlock);
        return grammar("php-markup", markup.tokens());
    }

    public static Prism4j.Grammar powershell() {
        Prism4j.Token variable = token("variable", pattern(compile("\\$(?:\\{[^}]+\\}|[\\w?:]+)")));
        Prism4j.Token bool = token("boolean", pattern(compile("\\$(?:false|true|null)\\b", CASE_INSENSITIVE)));
        Prism4j.Grammar interpolation = grammar("powershell-interpolation", bool, variable);
        return grammar("powershell",
                token("comment", pattern(compile("(^|[^`])<#[\\s\\S]*?#>"), true, true), pattern(compile("(^|[^`])#[^\\r\\n]*"), true, true)),
                token("string",
                        pattern(compile("@'\\r?\\n[\\s\\S]*?^'@", MULTILINE), false, true),
                        pattern(compile("@\"\\r?\\n[\\s\\S]*?^\"@", MULTILINE), false, true, null, interpolation),
                        pattern(compile("'(?:[^']++|'')*+'"), false, true),
                        pattern(compile("\"(?:`[\\s\\S]|[^`\"]++)*+\""), false, true, null, interpolation)),
                token("class-name", pattern(compile("\\[[a-z](?:\\[(?:\\[[^\\]]*\\]|[^\\[\\]])*\\]|[^\\[\\]])*\\]", CASE_INSENSITIVE))),
                bool,
                variable,
                token("keyword", pattern(compile("\\b(?:begin|break|catch|class|clean|configuration|continue|data|define|do|dynamicparam|else|elseif|end|enum|exit|filter|finally|for|foreach|from|function|if|inlinescript|parallel|param|process|return|sequence|switch|throw|trap|try|until|using|var|while|workflow)\\b", CASE_INSENSITIVE))),
                token("function", pattern(compile("\\b[a-z]+-[a-z][\\w-]*\\b", CASE_INSENSITIVE)), pattern(compile("\\b(?:cat|cd|cp|dir|echo|gc|gci|iex|irm|iwr|ls|mv|ps|pwd|rm|select|sleep|sort|tee|type|write)\\b", CASE_INSENSITIVE))),
                token("number", pattern(compile("\\b(?:0x[\\da-f]+|\\d+(?:\\.\\d+)?)(?:kb|mb|gb|tb|pb|d|l)?\\b", CASE_INSENSITIVE))),
                token("operator", pattern(compile("-[a-z]+\\b|[-+*/%!=<>]=?|&&|\\|\\||\\?\\?=?", CASE_INSENSITIVE))),
                token("punctuation", pattern(compile("[|{}\\[\\];(),.]")))
        );
    }

    public static Prism4j.Grammar lua() {
        return grammar("lua",
                token("comment", pattern(compile("^#![^\\r\\n]*|--(?:\\[(=*)\\[[\\s\\S]*?\\]\\1\\]|[^\\r\\n]*)", MULTILINE), false, true)),
                token("string", pattern(compile("\"(?:[^\\\\\"\\r\\n]++|\\\\z\\s*|\\\\(?:\\r\\n|[^z]))*+\""
                        + "|'(?:[^\\\\'\\r\\n]++|\\\\z\\s*|\\\\(?:\\r\\n|[^z]))*+'|\\[(=*)\\[[\\s\\S]*?\\]\\1\\]"), false, true)),
                token("number", pattern(compile("\\b0x[a-f\\d]+(?:\\.[a-f\\d]*)?(?:p[+-]?\\d+)?\\b|\\b\\d+(?:\\.\\B|(?:\\.\\d*)?(?:e[+-]?\\d+)?\\b)|\\B\\.\\d+(?:e[+-]?\\d+)?\\b", CASE_INSENSITIVE))),
                token("keyword", pattern(compile("\\b(?:and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while)\\b"))),
                token("function", pattern(compile("(?!\\d)\\w+(?=\\s*[({])"))),
                token("operator", pattern(compile("[-+*%^&|#]|//?|<[<=]?|>[>=]?|[=~]=?|(?<!\\.)\\.\\.(?!\\.)"))),
                token("punctuation", pattern(compile("[\\[\\](){},;]|\\.+|:+")))
        );
    }

    public static Prism4j.Grammar jsx(Prism4j prism4j, String language) {
        Prism4j.Grammar script = GrammarUtils.require(prism4j, "tsx".equals(language) ? "typescript" : "javascript");
        Prism4j.Grammar jsx = GrammarUtils.extend(script, language);
        String braceTemplate = "\\{(?:" + QUOTED + "|" + BACKTICK_QUOTED
                + "|/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*|/(?![*/])|[^{}\"'\\x60/]++|<self>)*+\\}";
        String braces = braceTemplate;
        for (int i = 0; i < 2; i++) braces = braces.replace("<self>", braceTemplate);
        braces = braces.replace("<self>", "(?!)");
        String value = "(?:" + QUOTED + "|" + braces + "|[^\\s{'\"/>=]++)";
        String tag = "</?(?:[a-zA-Z_$][\\w.:-]*(?:\\s+(?:[\\w.:$-]++(?:\\s*=\\s*" + value + ")?+|" + braces + "))*+\\s*/?)?>";
        boolean typescript = "tsx".equals(language);
        if (typescript) {
            String arrowParameters = "\\s*\\([^()]*\\)\\s*(?::[^=<>\\r\\n]+)?\\s*=>";
            tag = "(^|[^\\w$]|(?=</))" + tag + "(?!" + arrowParameters + ")";
        }
        Prism4j.Grammar tagInside = grammar("jsx-tag",
                token("tag", pattern(compile("^</?[^\\s>/]*"), false, false, null,
                        grammar("jsx-tag-name", token("punctuation", pattern(compile("^</?"))),
                                token("class-name", pattern(compile("^[A-Z]\\w*(?:\\.[A-Z]\\w*)*$")))))),
                token("attr-value", pattern(compile("=\\s*(?:" + QUOTED + "|[^\\s{'\">=]++)"), false, true, null,
                        grammar("jsx-attr-value", token("punctuation", pattern(compile("^=|[\"']")))))),
                token("script", pattern(compile("=\\s*" + braces + "|" + braces), false, true, null, jsx)),
                token("punctuation", pattern(compile("/?>"))),
                token("attr-name", pattern(compile("[\\w.:$-]+")))
        );
        GrammarUtils.insertBeforeToken(jsx, "keyword", token("tag", pattern(compile(tag), typescript, true, null, tagInside)));
        return jsx;
    }
}
