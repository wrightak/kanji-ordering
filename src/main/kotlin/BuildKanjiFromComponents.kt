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

fun main() {
    val heisigPath = Path.of("heisig-kanjis.csv")
    val selectionPath = Path.of("kanji-selection.csv")
    val exclusionPath = Path.of("exclusion-list.csv")

    val heisigRows = readHeisig(heisigPath)
    val selection = readSelection(selectionPath)
    val exclusions = readExclusionList(exclusionPath)

    if (selection.isEmpty()) {
        error("No kanji found in $selectionPath")
    }

    val missing = selection.filterNot { kanji -> heisigRows.any { it.kanji == kanji } }
    missing.forEach { System.err.println("Warning: kanji '$it' not found in $heisigPath") }

    val result = computeBuildable(heisigRows, selection, exclusions)

    val excludedSet = exclusions.toSet()
    println("Excluded kanji (${excludedSet.size}): ${excludedSet.joinToString(", ")}")
    println("Components collected (${result.components.size}): ${result.components.joinToString(", ")}")
    println("Buildable kanji not already selected (${result.buildable.size}):")
    result.buildable.forEach { println(it.kanji) }
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

fun readSelection(path: Path): List<String> {
    if (!Files.exists(path)) return emptyList()
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
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

fun readExclusionList(path: Path): List<String> {
    if (!Files.exists(path)) return emptyList()
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
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
