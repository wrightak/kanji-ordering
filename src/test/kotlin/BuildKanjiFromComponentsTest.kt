import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class BuildKanjiFromComponentsTest {
    @Test
    fun `day and month build bright`() {
        val heisigRows = readHeisig(Path.of("heisig-kanjis.csv"))
        val result = computeBuildable(heisigRows, listOf("日", "月"))

        assertTrue(result.buildable.any { it.kanji == "明" }) {
            "Expected 明 to be buildable from 日 and 月"
        }
    }
}
