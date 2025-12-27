import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import kotlin.test.Test

class BuildKanjiFromComponentsTest {
    @Test
    fun `day and month build bright`() {
        val heisigRows = readHeisig(Path.of("heisig-kanjis.csv"))
        val result = computeBuildable(heisigRows, listOf("日", "月"))

        assertTrue(result.buildable.any { it.kanji == "明" }) {
            "Expected 明 to be buildable from 日 and 月"
        }
    }

    @Test
    fun `excluded kanji are filtered out`() {
        val heisigRows = readHeisig(Path.of("heisig-kanjis.csv"))
        val result = computeBuildable(heisigRows, listOf("日", "月"), excluded = listOf("明"))

        assertTrue(result.buildable.none { it.kanji == "明" }) {
            "Excluded kanji should not appear in buildable list"
        }
    }

    @Test
    fun `ranking orders by gain`() {
        val rows = listOf(
            KanjiEntry("A", emptyList(), emptyList()), // primitive
            KanjiEntry("B", emptyList(), emptyList()), // primitive
            KanjiEntry("C", emptyList(), listOf("A", "B")), // requires A and B
            KanjiEntry("D", emptyList(), listOf("A")), // requires A
        )

        val selection = listOf("A")
        val baseline = computeBuildable(rows, selection, emptyList())
        val ranked = rankNextKanji(rows, selection, emptyList(), baseline)

        assertTrue(ranked.firstOrNull()?.kanji == "B") {
            "Expected B to be the top-ranked suggestion"
        }
    }

    @Test
    fun `component ranking finds best unlock`() {
        val rows = listOf(
            KanjiEntry("A", emptyList(), emptyList()),
            KanjiEntry("B", emptyList(), emptyList()),
            KanjiEntry("C", emptyList(), listOf("A", "B")),
        )
        val selection = listOf("A")
        val baseline = computeBuildable(rows, selection, emptyList())

        val ranked = rankNextComponent(rows, selection, emptyList(), baseline)

        assertTrue(ranked.firstOrNull()?.component == "B") {
            "Expected component B to unlock the most kanji"
        }
    }
}
