import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.GZIPInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

private val KVG_NAMESPACE = "http://kanjivg.tagaini.net"

data class HeisigEntry(
    val kanji: String,
    val keyword: String,
    val jlpt: String
)

fun runCleanup() {
    val heisigPath = Path.of("heisig-kanjis.csv")
    val kanjivgPath = Path.of("kanjivg.xml.gz")
    val outputPath = Path.of("heisig-kanjis-cleaned.csv")

    val heisigEntries = readHeisig(heisigPath)
    val componentMap = parseKanjiVgComponents(kanjivgPath)

    writeOutput(heisigEntries, componentMap, outputPath)
}

runCleanup()

private fun readHeisig(path: Path): List<HeisigEntry> {
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
        val header = reader.readLine() ?: error("Empty CSV: $path")
        val columns = parseCsvLine(header)
        val columnIndex = columns.withIndex().associate { it.value to it.index }
        val kanjiIdx = columnIndex["kanji"] ?: error("Missing kanji column")
        val keywordIdx = columnIndex["keyword_6th_ed"] ?: error("Missing keyword_6th_ed column")
        val jlptIdx = columnIndex["jlpt"] ?: error("Missing jlpt column")

        val entries = mutableListOf<HeisigEntry>()
        reader.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cells = parseCsvLine(line)
                val kanji = cells.getOrElse(kanjiIdx) { "" }
                if (kanji.isBlank()) return@forEach
                val keyword = cells.getOrElse(keywordIdx) { "" }
                val jlpt = cells.getOrElse(jlptIdx) { "" }
                entries += HeisigEntry(kanji, keyword, jlpt)
            }
        return entries
    }
}

private fun parseKanjiVgComponents(path: Path): Map<String, List<String>> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val builder = factory.newDocumentBuilder()

    GZIPInputStream(Files.newInputStream(path)).use { input ->
        val document = builder.parse(input)
        val nodes = document.getElementsByTagName("kanji")
        val result = LinkedHashMap<String, List<String>>(nodes.length)
        for (i in 0 until nodes.length) {
            val kanjiElement = nodes.item(i) as? Element ?: continue
            val rootGroup = firstChildGroup(kanjiElement) ?: continue
            val kanjiChar = getElementName(rootGroup) ?: continue
            val components = extractLeafComponents(rootGroup)
            result[kanjiChar] = components
        }
        return result
    }
}

private fun writeOutput(
    entries: List<HeisigEntry>,
    components: Map<String, List<String>>,
    outputPath: Path
) {
    Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8).use { writer ->
        writer.appendLine("kanji,keyword_6th_ed,jlpt,components")
        for (entry in entries) {
            val directComponents = components[entry.kanji].orEmpty()
            val componentsField = directComponents.joinToString("; ")
            writer.appendLine(
                listOf(entry.kanji, entry.keyword, entry.jlpt, componentsField).toCsvRow()
            )
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

private fun firstChildGroup(element: Element): Element? {
    val children = element.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "g") {
            return node as Element
        }
    }
    return null
}

private fun extractLeafComponents(node: Element): List<String> {
    val children = node.childNodes
    val leafNames = LinkedHashSet<String>()
    var hasChildGroup = false
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "g") {
            hasChildGroup = true
            leafNames += extractLeafComponents(child as Element)
        }
    }
    if (!hasChildGroup) {
        val name = getElementName(node)
        if (!name.isNullOrBlank()) {
            leafNames += name
        }
    }
    return leafNames.toList()
}

private fun getElementName(element: Element): String? {
    val namespaced = element.getAttributeNS(KVG_NAMESPACE, "element")
    if (!namespaced.isNullOrBlank()) return namespaced
    val fallback = element.getAttribute("kvg:element")
    return fallback.takeIf { it.isNotBlank() }
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
