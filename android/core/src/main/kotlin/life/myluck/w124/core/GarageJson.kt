package life.myluck.w124.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object GarageJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeState(text: String): GarageState = json.decodeFromString(GarageState.serializer(), text)

    fun encodeState(state: GarageState): String = json.encodeToString(GarageState.serializer(), state) + "\n"
}
