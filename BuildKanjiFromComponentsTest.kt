import java.nio.file.Path
import kotlin.test.assertTrue

fun main() {
    val heisigRows = readHeisig(Path.of("heisig-kanjis.csv"))
    val result = computeBuildable(heisigRows, listOf("日", "月"))
    assertTrue(result.buildable.any { it.kanji == "明" }) { "Expected 明 to be buildable from 日 and 月" }
    println("Test passed: 日 and 月 can build 明")
}
