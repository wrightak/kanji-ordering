fun main() {
    val heisigPath = java.nio.file.Path.of("heisig-kanjis.csv")
    val selectionPath = java.nio.file.Path.of("kanji-selection.csv")
    val exclusionPath = java.nio.file.Path.of("exclusion-list.csv")
    val selectionSamplePath = java.nio.file.Path.of("kanji-selection.sample.csv")
    val exclusionSamplePath = java.nio.file.Path.of("exclusion-list.sample.csv")

    val heisigRows = readHeisig(heisigPath)
    val selection = readSelection(selectionPath, selectionSamplePath)
    val exclusions = readExclusionList(exclusionPath, exclusionSamplePath)

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
