package com.allan.picnote.data

// 以 Stack Overflow 2026 调查的使用顺序为基础，调整 Java、Kotlin、JSON 和 SQL 的默认位置。
// 来源：https://survey.stackoverflow.co/2026/technology/data/languages
val CodeLanguages: Map<String, String> = linkedMapOf(
    "javascript" to "JavaScript", "jsx" to "JSX", "json" to "JSON", "html" to "HTML", "css" to "CSS",
    "python" to "Python", "java" to "Java", "kotlin" to "Kotlin", "bash" to "Bash",
    "typescript" to "TypeScript", "tsx" to "TSX", "csharp" to "C#", "powershell" to "PowerShell", "cpp" to "C++", "c" to "C",
    "rust" to "Rust", "php" to "PHP", "go" to "Go", "lua" to "Lua", "swift" to "Swift",
    "dart" to "Dart", "sql" to "SQL", "yaml" to "YAML", "xml" to "XML",
)

val DefaultCodeLanguageOrder = listOf("") + CodeLanguages.keys
