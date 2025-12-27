import java.nio.file.Path

fun main() {
    val heisigPath = Path.of("heisig-kanjis.csv")
    val selectionPath = Path.of("kanji-selection.csv")
    val exclusionPath = Path.of("exclusion-list.csv")
    val selectionSamplePath = Path.of("kanji-selection.sample.csv")
    val exclusionSamplePath = Path.of("exclusion-list.sample.csv")
    val gradesPath = Path.of("kanji-grades.csv")

    val heisigRows = readHeisig(heisigPath)
    val selection = readSelection(selectionPath, selectionSamplePath)
    val exclusions = readExclusionList(exclusionPath, exclusionSamplePath)
    val grades = readGrades(gradesPath)

    if (selection.isEmpty()) {
        error("No kanji found in $selectionPath")
    }

    val missing = selection.filterNot { kanji -> heisigRows.any { it.kanji == kanji } }
    missing.forEach { System.err.println("Warning: kanji '$it' not found in $heisigPath") }

    val baseline = computeBuildable(heisigRows, selection, exclusions)
    val suggestions = rankNextComponent(heisigRows, selection, exclusions, baseline, grades)

    if (suggestions.isEmpty()) {
        println("No component would unlock additional buildable kanji.")
    } else {
        println("Components ranked by grade-weighted unlocked points (higher is better):")
        suggestions.forEach { suggestion ->
            val gainedList = suggestion.gained.joinToString(", ") { it.kanji }
            val score = scoreComponentSuggestion(suggestion, grades)
            println(" - ${suggestion.component} (score $score, +${suggestion.gain} kanji): [$gainedList]")
        }
    }
}
