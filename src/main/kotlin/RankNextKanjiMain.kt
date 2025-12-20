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

    val baseline = computeBuildable(heisigRows, selection, exclusions)
    val suggestions = rankNextKanji(heisigRows, selection, exclusions, baseline)

    if (suggestions.isEmpty()) {
        println("No candidate kanji would unlock additional buildable entries.")
    } else {
        println("Next kanji ranked by unlocked count:")
        suggestions.forEach { suggestion ->
            val gainedList = suggestion.gained.joinToString(", ") { it.kanji }
            println(" - ${suggestion.kanji} (+${suggestion.gain}): [$gainedList]")
        }
    }
}
