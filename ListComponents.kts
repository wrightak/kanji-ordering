import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class KanjiInfo(
    val kanji: String,
    val keyword: String,
    val jlpt: String,
    val components: List<String>
)

fun main(args: Array<String>) {
    val newComponent = args.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Usage: kotlinc -script ListComponents.kts <new-component>")

    val inputPath = Path.of("kanji-selection.csv")
    val targets = readTargetList(inputPath)
    if (targets.isEmpty()) {
        error("Input CSV $inputPath did not contain any kanji entries")
    }

    val entries = readKanjiData(Path.of("heisig-kanjis-cleaned.csv"))
    val data = entries.associateBy { it.kanji }
    val missing = targets.filterNot { data.containsKey(it) }
    if (missing.isNotEmpty()) {
        error("Kanji not found in cleaned CSV: ${missing.joinToString(", ")}")
    }

    val allComponents = linkedSetOf<String>()
    val perKanji = mutableMapOf<String, List<String>>()

    for (kanji in targets) {
        val collected = linkedSetOf<String>()
        collectComponents(kanji, data, collected)
        perKanji[kanji] = collected.toList()
        allComponents += collected
        if (collected.isEmpty()) {
            allComponents += kanji
        }
    }

    println("Targets: ${targets.joinToString(", ")}")
    println()
    perKanji.forEach { (kanji, comps) ->
        println("Components for $kanji (${comps.size}): ${comps.joinToString(", ")}")
    }
    println()
    println("All components (${allComponents.size} unique):")
    allComponents.forEach { println(it) }
    println()

    val allowedComponents = linkedSetOf<String>().apply {
        addAll(allComponents)
        add(newComponent)
        collectComponents(newComponent, data, this)
    }

    val buildable = findBuildableKanji(entries, allowedComponents)

    println("New component introduced: $newComponent")
    println("Allowed components now (${allowedComponents.size}): ${allowedComponents.joinToString(", ")}")
    println()
    println("Kanji buildable with current components (${buildable.size}):")
    buildable.forEach { println("${it.kanji} (${it.keyword}) [${it.jlpt.ifEmpty { "None" }}]") }
}

main(args)

private fun collectComponents(
    kanji: String,
    data: Map<String, KanjiInfo>,
    accumulator: MutableSet<String>
) {
    val info = data[kanji]
    val components = info?.components
    if (components == null || components.isEmpty()) {
        accumulator += kanji
        return
    }
    for (component in components) {
        if (component.isBlank()) continue
        if (accumulator.add(component)) {
            collectComponents(component, data, accumulator)
        }
    }
}

private fun readKanjiData(path: Path): List<KanjiInfo> {
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: error("Empty CSV: $path")
        val columns = parseCsvLine(header)
        val columnIndex = columns.withIndex().associate { it.value to it.index }
        val kanjiIdx = columnIndex["kanji"] ?: error("Missing kanji column")
        val keywordIdx = columnIndex["keyword_6th_ed"] ?: error("Missing keyword column")
        val jlptIdx = columnIndex["jlpt"] ?: error("Missing jlpt column")
        val componentsIdx = columnIndex["components"] ?: error("Missing components column")

        val list = mutableListOf<KanjiInfo>()
        reader.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val cells = parseCsvLine(line)
            val kanji = cells.getOrNull(kanjiIdx)?.trim().orEmpty()
            if (kanji.isEmpty()) return@forEach
            val keyword = cells.getOrNull(keywordIdx)?.trim().orEmpty()
            val jlpt = cells.getOrNull(jlptIdx)?.trim().orEmpty()
            val rawComponents = cells.getOrNull(componentsIdx)?.trim().orEmpty()
            val components = if (rawComponents.isEmpty()) emptyList() else rawComponents.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            list += KanjiInfo(kanji, keyword, jlpt, components)
        }
        return list
    }
}

private fun findBuildableKanji(entries: List<KanjiInfo>, allowed: Set<String>): List<KanjiInfo> {
    return entries.filter { entry ->
        if (entry.components.isEmpty()) {
            entry.kanji in allowed
        } else {
            entry.components.all { it.isNotBlank() && it in allowed }
        }
    }
}

private fun parseCsvLine(line: String): List<String> {
    val cells = mutableListOf<String>()
    val buffer = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    buffer.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ch == ',' && !inQuotes -> {
                cells += buffer.toString()
                buffer.setLength(0)
            }
            else -> buffer.append(ch)
        }
        i++
    }
    cells += buffer.toString()
    return cells
}

private fun readTargetList(path: Path): List<String> {
    if (!Files.exists(path)) {
        error("Input file $path not found")
    }

    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: return emptyList()
        val columns = parseCsvLine(header)
        val columnIndex = columns.withIndex().associate { it.value to it.index }
        val kanjiIdx = columnIndex["kanji"] ?: error("Input CSV must have 'kanji' column")

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { parseCsvLine(it) }
            .mapNotNull { row -> row.getOrNull(kanjiIdx)?.takeIf { it.isNotBlank() }?.trim() }
            .toList()
    }
}
