package com.wunding.stforge.data

import org.json.JSONArray
import org.json.JSONObject

data class BookEntry(
    val keys: List<String> = emptyList(),
    val content: String = "",
    val insertionOrder: Int = 0,
    val enabled: Boolean = true,
    val comment: String = "",
    val constant: Boolean = false,
)

data class StCard(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistory: String = "",
    val tags: List<String> = emptyList(),
    val alternates: List<String> = emptyList(),
    val entries: List<BookEntry> = emptyList(),
    val creator: String = "Wunding Studio",
    val version: String = "1.0",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toV2Json(): String = JSONObject().apply {
        put("spec", "chara_card_v2")
        put("spec_version", "2.0")
        put("name", name)
        put("description", description)
        put("personality", personality)
        put("scenario", scenario)
        put("first_mes", firstMes)
        put("mes_example", mesExample)
        put("creator_notes", creatorNotes)
        put("system_prompt", systemPrompt)
        put("post_history_instructions", postHistory)
        put("tags", JSONArray(tags))
        put("creator", creator)
        put("character_version", version)
        put("alternate_greetings", JSONArray(alternates))
        put("data", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("personality", personality)
            put("scenario", scenario)
            put("first_mes", firstMes)
            put("mes_example", mesExample)
            put("creator_notes", creatorNotes)
            put("system_prompt", systemPrompt)
            put("post_history_instructions", postHistory)
            put("tags", JSONArray(tags))
            put("creator", creator)
            put("character_version", version)
            put("alternate_greetings", JSONArray(alternates))
            put("extensions", JSONObject().put("stforge", JSONObject().put("id", id)))
            put("character_book", JSONObject().apply {
                put("name", "$name 的世界书")
                put("entries", JSONArray().apply {
                    entries.forEachIndexed { i, e ->
                        put(JSONObject().apply {
                            put("id", i)
                            put("keys", JSONArray(e.keys))
                            put("secondary_keys", JSONArray())
                            put("content", e.content)
                            put("enabled", e.enabled)
                            put("insertion_order", e.insertionOrder)
                            put("case_sensitive", false)
                            put("name", e.comment.ifBlank { e.keys.firstOrNull() ?: "条目 ${i + 1}" })
                            put("priority", 10)
                            put("comment", e.comment)
                            put("selective", false)
                            put("constant", e.constant)
                            put("position", "before_char")
                        })
                    }
                })
            })
        })
    }.toString(2)

    companion object {
        fun fromJson(raw: String): StCard {
            val o = JSONObject(raw)
            val d = if (o.has("data") && o.opt("data") is JSONObject) o.getJSONObject("data") else o
            val book = d.optJSONObject("character_book")
            val entries = mutableListOf<BookEntry>()
            book?.optJSONArray("entries")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    entries.add(
                        BookEntry(
                            keys = e.optJSONArray("keys")?.let { k -> (0 until k.length()).map { k.optString(it) } } ?: emptyList(),
                            content = e.optString("content"),
                            insertionOrder = e.optInt("insertion_order", 0),
                            enabled = e.optBoolean("enabled", true),
                            comment = e.optString("comment").ifBlank { e.optString("name") },
                            constant = e.optBoolean("constant", false),
                        )
                    )
                }
            }
            fun arrOf(key: String): List<String> = d.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val extId = d.optJSONObject("extensions")?.optJSONObject("stforge")?.optString("id").orEmpty()
            return StCard(
                id = extId.ifBlank { "imp-" + System.currentTimeMillis().toString(36) },
                name = d.optString("name").ifBlank { "未命名角色" },
                description = d.optString("description"),
                personality = d.optString("personality"),
                scenario = d.optString("scenario"),
                firstMes = d.optString("first_mes"),
                mesExample = d.optString("mes_example"),
                creatorNotes = d.optString("creator_notes"),
                systemPrompt = d.optString("system_prompt"),
                postHistory = d.optString("post_history_instructions"),
                tags = arrOf("tags"),
                alternates = arrOf("alternate_greetings"),
                entries = entries,
                creator = d.optString("creator", "Wunding Studio"),
                version = d.optString("character_version", "1.0"),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }
}
