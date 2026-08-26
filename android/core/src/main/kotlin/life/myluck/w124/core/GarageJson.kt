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

    fun decodeTools(text: String): ToolInventory = json.decodeFromString(ToolInventory.serializer(), text)

    fun encodeTools(inventory: ToolInventory): String =
        json.encodeToString(ToolInventory.serializer(), inventory) + "\n"

    fun decodeJobs(text: String): JobBook = json.decodeFromString(JobBook.serializer(), text)

    fun encodeJobs(book: JobBook): String = json.encodeToString(JobBook.serializer(), book) + "\n"
}
