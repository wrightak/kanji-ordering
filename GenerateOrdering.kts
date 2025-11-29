import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashSet

private val JLPT_WEIGHTS = mapOf(
    "N5" to 100,
    "N4" to 70,
    "N3" to 40,
    "N2" to 20,
    "N1" to 10
)

data class KanjiEntry(
    val kanji: String,
    val keyword: String,
    val jlpt: String,
    val components: List<String>,
    val weight: Int
)

data class OrderedKanji(
    val kanji: String,
    val keyword: String,
    val jlpt: String,
    val weight: Int,
    val introducedBy: String,
    val components: List<String>
)

data class ComponentIntro(
    val order: Int,
    val component: String,
    val totalWeight: Int,
    val unlockedCount: Int
)

data class Candidate(
    val component: String,
    val unlockedIndices: List<Int>,
    val totalWeight: Int
)

fun generateOrdering() {
    val inputPath = Path.of("heisig-kanjis-cleaned.csv")
    val orderOutput = Path.of("kanji-learning-order.csv")
    val componentOutput = Path.of("component-introduction-log.csv")

    val entries = readEntries(inputPath)
    val usage = buildComponentUsage(entries)
    val (orderedKanji, componentLog) = buildOrdering(entries, usage)
    writeOrderCsv(orderedKanji, orderOutput)
    writeComponentLog(componentLog, componentOutput)
}

private fun readEntries(path: Path): List<KanjiEntry> {
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: error("Empty CSV: $path")
        val columns = parseCsvLine(header)
        val columnIndex = columns.withIndex().associate { it.value to it.index }
        val kanjiIdx = columnIndex["kanji"] ?: error("Missing kanji column")
        val keywordIdx = columnIndex["keyword_6th_ed"] ?: error("Missing keyword_6th_ed column")
        val jlptIdx = columnIndex["jlpt"] ?: error("Missing jlpt column")
        val componentsIdx = columnIndex["components"] ?: error("Missing components column")

        val entries = mutableListOf<KanjiEntry>()
        reader.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cells = parseCsvLine(line)
                val kanji = cells.getOrNull(kanjiIdx)?.trim().orEmpty()
                if (kanji.isEmpty()) return@forEach
                val keyword = cells.getOrNull(keywordIdx)?.trim().orEmpty()
                val jlpt = cells.getOrNull(jlptIdx)?.trim().orEmpty()
                val componentsField = cells.getOrNull(componentsIdx)?.trim().orEmpty()
                val components = if (componentsField.isEmpty()) {
                    emptyList()
                } else {
                    componentsField.split(';')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
                val weight = jlptWeight(jlpt)
                entries += KanjiEntry(kanji, keyword, jlpt, components, weight)
            }
        return entries
    }
}

private fun buildComponentUsage(entries: List<KanjiEntry>): Map<String, Set<Int>> {
    val usage = mutableMapOf<String, MutableSet<Int>>()
    entries.forEachIndexed { index, entry ->
        entry.components.forEach { component ->
            if (component.isBlank()) return@forEach
            usage.computeIfAbsent(component) { mutableSetOf() }.add(index)
        }
    }
    return usage
}

private fun jlptWeight(level: String): Int {
    val normalized = level.uppercase()
    return JLPT_WEIGHTS[normalized] ?: 1
}

private fun buildOrdering(
    entries: List<KanjiEntry>,
    usage: Map<String, Set<Int>>
): Pair<List<OrderedKanji>, List<ComponentIntro>> {
    val introducedComponents = mutableSetOf<String>()
    val remaining = entries.indices.toMutableSet()
    val allComponents = LinkedHashSet<String>().apply {
        usage.keys.forEach { add(it) }
    }
    val ordered = mutableListOf<OrderedKanji>()
    val introLog = mutableListOf<ComponentIntro>()
    var currentBatchLabel: String? = "INITIAL"

    while (remaining.isNotEmpty()) {
        val available = remaining.filter { idx ->
            entries[idx].components.all { it in introducedComponents }
        }

        if (available.isNotEmpty()) {
            val sorted = available.sortedWith(
                compareByDescending<Int> { entries[it].weight }
                    .thenBy { entries[it].keyword }
                    .thenBy { entries[it].kanji }
            )
            for (idx in sorted) {
                val entry = entries[idx]
                ordered += OrderedKanji(
                    kanji = entry.kanji,
                    keyword = entry.keyword,
                    jlpt = entry.jlpt,
                    weight = entry.weight,
                    introducedBy = currentBatchLabel.orEmpty(),
                    components = entry.components
                )
                remaining.remove(idx)
            }
            currentBatchLabel = null
            continue
        }

        val candidate = selectComponent(entries, remaining, introducedComponents, allComponents, usage)
            ?: break

        introducedComponents += candidate.component
        val orderNumber = introLog.size + 1
        introLog += ComponentIntro(
            order = orderNumber,
            component = candidate.component,
            totalWeight = candidate.totalWeight,
            unlockedCount = candidate.unlockedIndices.size
        )
        currentBatchLabel = candidate.component
    }

    if (remaining.isNotEmpty()) {
        System.err.println(
            "Warning: Unable to resolve ordering for ${remaining.size} kanji: " +
                remaining.map { entries[it].kanji }.sorted().joinToString(", ")
        )
    }

    return ordered to introLog
}

private fun selectComponent(
    entries: List<KanjiEntry>,
    remaining: Set<Int>,
    introduced: Set<String>,
    allComponents: Set<String>,
    usage: Map<String, Set<Int>>
): Candidate? {
    var best: Candidate? = null
    val unused = allComponents.filterNot { it in introduced }
    for (component in unused) {
        val indices = usage[component] ?: continue
        val unlocked = mutableListOf<Int>()
        for (idx in indices) {
            if (idx !in remaining) continue
            val entry = entries[idx]
            val canBuild = entry.components.all { it == component || it in introduced }
            if (canBuild) {
                unlocked += idx
            }
        }
        if (unlocked.isEmpty()) continue
        val weightSum = unlocked.sumOf { entries[it].weight }
        val candidate = Candidate(component, unlocked, weightSum)
        if (isBetter(candidate, best)) {
            best = candidate
        }
    }
    return best
}

private fun isBetter(candidate: Candidate, currentBest: Candidate?): Boolean {
    currentBest ?: return true
    if (candidate.totalWeight != currentBest.totalWeight) {
        return candidate.totalWeight > currentBest.totalWeight
    }
    val candidateCount = candidate.unlockedIndices.size
    val bestCount = currentBest.unlockedIndices.size
    if (candidateCount != bestCount) {
        return candidateCount > bestCount
    }
    return candidate.component < currentBest.component
}

private fun writeOrderCsv(order: List<OrderedKanji>, path: Path) {
    Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
        writer.appendLine("position,kanji,keyword_6th_ed,jlpt,weight,introduced_by_component,components")
        order.forEachIndexed { index, item ->
            val componentsField = item.components.joinToString("; ")
            val row = listOf(
                (index + 1).toString(),
                item.kanji,
                item.keyword,
                item.jlpt.ifEmpty { "None" },
                item.weight.toString(),
                item.introducedBy,
                componentsField
            ).toCsvRow()
            writer.appendLine(row)
        }
    }
}

private fun writeComponentLog(log: List<ComponentIntro>, path: Path) {
    Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
        writer.appendLine("order,component,total_weight,unlocked_kanji")
        for (entry in log) {
            val row = listOf(
                entry.order.toString(),
                entry.component,
                entry.totalWeight.toString(),
                entry.unlockedCount.toString()
            ).toCsvRow()
            writer.appendLine(row)
        }
    }
}

private fun List<String>.toCsvRow(): String = buildString {
    this@toCsvRow.forEachIndexed { index, value ->
        if (index > 0) append(',')
        append(value.escapeCsv())
    }
}

private fun String.escapeCsv(): String {
    val needsQuotes = contains(',') || contains('"') || contains('\n')
    if (!needsQuotes) return this
    val escaped = replace("\"", "\"\"")
    return "\"$escaped\""
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

generateOrdering()
