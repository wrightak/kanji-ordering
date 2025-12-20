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
}
