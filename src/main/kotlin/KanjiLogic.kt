import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class KanjiEntry(
    val kanji: String,
    val keywords: List<String>,
    val components: List<String>
)

data class BuildResult(
    val components: Set<String>,
    val buildable: List<KanjiEntry>
)

data class NextKanjiSuggestion(
    val kanji: String,
    val gained: List<KanjiEntry>
) {
    val gain: Int get() = gained.size
}

data class ComponentSuggestion(
    val component: String,
    val gained: List<KanjiEntry>
) {
    val gain: Int get() = gained.size
}

fun computeBuildable(
    heisigRows: List<KanjiEntry>,
    selection: List<String>,
    excluded: List<String> = emptyList()
): BuildResult {
    val byKanji = heisigRows.associateBy { it.kanji }

    val components = linkedSetOf<String>()
    val visiting = mutableSetOf<String>()

    fun expand(symbol: String) {
        if (!visiting.add(symbol)) return
        val row = byKanji[symbol]
        if (row == null) {
            components += symbol
            return
        }
        row.keywords.forEach { keyword ->
            if (keyword.isNotBlank()) components += keyword
        }
        val rowComponents = row.components
        if (rowComponents.isEmpty()) {
            components += symbol
            return
        }
        for (component in rowComponents) {
            if (component.isBlank()) continue
            components += component
            expand(component)
        }
    }

    selection.forEach { expand(it) }

    val selectionSet = selection.toSet()
    val excludedSet = excluded.toSet()
    val buildable = heisigRows.filter { entry ->
        entry.components.isNotEmpty() &&
            entry.kanji !in selectionSet &&
            entry.kanji !in excludedSet &&
            entry.components.all { it in components }
    }

    return BuildResult(components, buildable)
}

fun rankNextKanji(
    heisigRows: List<KanjiEntry>,
    selection: List<String>,
    excluded: List<String> = emptyList(),
    baseline: BuildResult? = null,
    gradeLookup: Map<String, Int?> = emptyMap()
): List<NextKanjiSuggestion> {
    val selectionSet = selection.toSet()
    val excludedSet = excluded.toSet()
    val baseResult = baseline ?: computeBuildable(heisigRows, selection, excluded)
    val baseBuildable = baseResult.buildable.map { it.kanji }.toSet()

    val suggestions = mutableListOf<NextKanjiSuggestion>()

    for (entry in heisigRows) {
        val candidate = entry.kanji
        if (candidate in selectionSet || candidate in excludedSet) continue

        val candidateResult = computeBuildable(heisigRows, selection + candidate, excluded)
        val candidateBuildable = candidateResult.buildable.map { it.kanji }.toSet()
        val gainedKanji = candidateBuildable - baseBuildable
        if (gainedKanji.isEmpty()) continue

        val gainedEntries = candidateResult.buildable.filter { it.kanji in gainedKanji }
        suggestions += NextKanjiSuggestion(candidate, gainedEntries)
    }

    return suggestions.sortedWith(
        compareBy<NextKanjiSuggestion> { gradeLookup[it.kanji] ?: Int.MAX_VALUE }
            .thenByDescending { it.gain }
            .thenBy { it.kanji }
    )
}

fun rankNextComponent(
    heisigRows: List<KanjiEntry>,
    selection: List<String>,
    excluded: List<String> = emptyList(),
    baseline: BuildResult? = null
): List<ComponentSuggestion> {
    val baseResult = baseline ?: computeBuildable(heisigRows, selection, excluded)
    val baseBuildable = baseResult.buildable.map { it.kanji }.toSet()
    val existingComponents = baseResult.components

    val allComponents = heisigRows.flatMap { it.components }.toSet()

    val suggestions = mutableListOf<ComponentSuggestion>()
    for (component in allComponents) {
        if (component in existingComponents) continue

        val candidateResult = computeBuildable(heisigRows, selection + component, excluded)
        val candidateBuildable = candidateResult.buildable.map { it.kanji }.toSet()
        val gainedKanji = candidateBuildable - baseBuildable
        if (gainedKanji.isEmpty()) continue

        val gainedEntries = candidateResult.buildable.filter { it.kanji in gainedKanji }
        suggestions += ComponentSuggestion(component, gainedEntries)
    }

    return suggestions.sortedByDescending { it.gain }
}

fun readHeisig(path: Path): List<KanjiEntry> {
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: error("Empty CSV: $path")
        val columns = parseCsvLine(header)
        val columnIndex = columns.withIndex().associate { it.value to it.index }
        val kanjiIdx = columnIndex["kanji"] ?: error("Missing kanji column")
        val keyword6Idx = columnIndex["keyword_6th_ed"] ?: columnIndex["keyword"]
        val keyword5Idx = columnIndex["keyword_5th_ed"]
        val componentsIdx = columnIndex["components"] ?: error("Missing components column")

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val cells = parseCsvLine(line)
                val kanji = cells.getOrNull(kanjiIdx)?.trim().orEmpty()
                val keywords = linkedSetOf<String>().apply {
                    if (keyword6Idx != null) add(cells.getOrNull(keyword6Idx)?.trim().orEmpty())
                    if (keyword5Idx != null) add(cells.getOrNull(keyword5Idx)?.trim().orEmpty())
                }.filter { it.isNotEmpty() }
                val rawComponents = cells.getOrNull(componentsIdx)?.trim().orEmpty()
                val components = if (rawComponents.isEmpty()) emptyList() else rawComponents.split(';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                KanjiEntry(kanji, keywords, components)
            }
            .filter { it.kanji.isNotEmpty() }
            .toList()
    }
}

fun readSelection(path: Path, samplePath: Path = Path.of("kanji-selection.sample.csv")): List<String> {
    val resolved = when {
        Files.exists(path) -> path
        Files.exists(samplePath) -> samplePath
        else -> return emptyList()
    }
    Files.newBufferedReader(resolved, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: return emptyList()
        val columns = parseCsvLine(header)
        val kanjiIdx = columns.withIndex().firstOrNull { it.value == "kanji" }?.index
            ?: error("Input CSV must have 'kanji' column")

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .mapNotNull { row -> row.getOrNull(kanjiIdx)?.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}

fun readExclusionList(path: Path, samplePath: Path = Path.of("exclusion-list.sample.csv")): List<String> {
    val resolved = when {
        Files.exists(path) -> path
        Files.exists(samplePath) -> samplePath
        else -> return emptyList()
    }
    Files.newBufferedReader(resolved, StandardCharsets.UTF_8).use { reader ->
        val lines = reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it).firstOrNull()?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
            .toList()
        return if (lines.firstOrNull()?.equals("kanji", ignoreCase = true) == true) lines.drop(1) else lines
    }
}

fun parseCsvLine(line: String): List<String> {
    val cells = mutableListOf<String>()
    val buffer = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        when (val ch = line[i]) {
            '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    buffer.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> if (!inQuotes) {
                cells += buffer.toString()
                buffer.setLength(0)
            } else {
                buffer.append(ch)
            }
            else -> buffer.append(ch)
        }
        i++
    }
    cells += buffer.toString()
    return cells
}

fun readGrades(path: Path): Map<String, Int?> {
    if (!Files.exists(path)) return emptyMap()
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: return emptyMap()
        val columns = parseCsvLine(header)
        val kanjiIdx = columns.indexOf("kanji")
        val gradeIdx = columns.indexOf("grade")
        if (kanjiIdx == -1 || gradeIdx == -1) return emptyMap()

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .mapNotNull { row ->
                val kanji = row.getOrNull(kanjiIdx)?.trim().orEmpty()
                val grade = row.getOrNull(gradeIdx)?.trim()
                if (kanji.isEmpty()) null else kanji to grade?.toIntOrNull()
            }
            .toMap()
    }
}
