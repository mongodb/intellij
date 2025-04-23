import com.mongodb.jbplugin.mql.DataDistribution
import kotlin.js.Json

private fun dynamicToMap(obj: dynamic): Map<String, dynamic> {
    val keys = js("Object.keys(obj)") as Array<String>
    return keys.associateWith { key -> obj[key] }
}

fun calculateDataDistribution(sample: Array<Json>): DataDistribution {
    val map = sample.map { dynamicToMap(it) }
    return DataDistribution.generate(map)
}
