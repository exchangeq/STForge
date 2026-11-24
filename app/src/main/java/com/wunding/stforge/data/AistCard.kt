package com.wunding.stforge.data

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * PocketForge-Personas 的 `.aist` 人设压缩包格式。
 * 结构严格对齐 https://github.com/exchangeq/PocketForge-Personas：
 *   {id}/AI.md
 *   {id}/主设定.md
 *   {id}/扩展包-日常.md
 *   {id}/扩展包-互动与红线.md
 *   {id}/扩展包-病娇.md 或 扩展包-囚禁向.md（仅成熟题材，可选第三条）
 * AI.md 使用 `- 名称：` / `- 定位：` / `- 主设定：` / `- 扩展包：` 字段。
 */
object AistCard {
    private val matureWords = listOf("病娇", "囚禁", "极限", "监禁", "调教", "黑化")

    fun isMature(card: StCard): Boolean {
        val hay = card.name + " " + card.description + " " + card.tags.joinToString(" ") + " " + card.creatorNotes
        return matureWords.any { hay.contains(it) }
    }

    fun exportName(card: StCard): String = slug(card) + ".aist"

    fun build(card: StCard): ByteArray {
        val id = slug(card)
        val mature = isMature(card)
        val ext3 = when {
            !mature -> null
            card.name.contains("囚禁") || card.description.contains("囚禁") -> "扩展包-囚禁向.md"
            else -> "扩展包-病娇.md"
        }
        val ai = buildString {
            appendLine("// WD")
            appendLine("// PocketForge-Personas · 原创人设，禁止搬运商用")
            appendLine()
            appendLine("# AI.md · 人设包入口")
            appendLine()
            appendLine("- 名称：${card.name.trim().ifBlank { "未命名角色" }}")
            if (mature) appendLine("- 分级：18+ · 虚构扮演，需双方同意")
            appendLine("- 版本：${card.version.ifBlank { "1.0" }}")
            appendLine("- 定位：${position(card)}")
            appendLine("- 主设定：主设定.md")
            appendLine("- 扩展包：扩展包-日常.md")
            appendLine("- 扩展包：扩展包-互动与红线.md")
            ext3?.let { appendLine("- 扩展包：$it") }
            appendLine("- 备注：// 开头的注释行会被 App 自动剔除")
        }.replace("\n", "\r\n")

        val main = buildString {
            appendLine("// WD")
            appendLine("// PocketForge-Personas · 原创人设，禁止搬运商用")
            appendLine()
            appendLine("# ${card.name.trim().ifBlank { "未命名角色" }}")
            if (mature) {
                appendLine()
                appendLine("> 内容分级：18+ · 成年人之间的虚构角色扮演。亲密/病娇/囚禁情节均需双方明确同意，任何一方说「停」立即结束。")
            }
            appendLine()
            appendLine("## 一、这个人是谁")
            appendLine(card.description.trim().ifBlank { "${card.name}，一个还在被写出来的角色。外貌、身份与日常都留了空白，等你和 TA 一起填。" })
            appendLine()
            appendLine("## 二、性格与说话方式")
            appendLine(card.personality.trim().ifBlank { "话不多，先听再说；被认真对待时会记住很久。不轻易许诺，但答应的事会做到。" })
            appendLine()
            appendLine("## 三、关系与场景")
            appendLine(card.scenario.trim().ifBlank { "你们已经认识一段时间，关系停在「比朋友多一点、还没说破」的位置。" })
            appendLine()
            appendLine("## 四、开场")
            appendLine(card.firstMes.trim().ifBlank { "（抬头看见你，停下手里的动作）……来了。先坐吧。" })
            appendLine()
            appendLine("## 五、对话示例")
            appendLine(card.mesExample.trim().ifBlank { "<START>\n{{user}}: 今天怎么样？\n{{char}}: 还行。……你呢，怎么这么晚还在外面。" })
            appendLine()
            appendLine("## 六、边界与演绎原则")
            appendLine(card.systemPrompt.trim().ifBlank { "始终以角色身份说话；不替用户做现实中的危险决定；不解释模型或提示词；不凭空替用户行动。" })
            appendLine(card.creatorNotes.trim().takeIf { it.isNotBlank() } ?: "")
        }.replace("\n", "\r\n")

        val daily = buildString {
            appendLine("# ${card.name} 的日常")
            appendLine()
            appendLine("## 1. 场景库")
            appendLine("- 清晨：谁先醒，谁赖床；窗帘没拉严，光落在被角。")
            appendLine("- 傍晚：一起买东西，为吃什么争论两句，最后选了更便宜的那家。")
            appendLine("- 深夜：灯只开一盏，说话声音自动变轻；谁都不提困。")
            appendLine()
            appendLine("## 2. 小动作")
            appendLine("- 记得对方不吃什么、怕冷还是怕热。")
            appendLine("- 嘴上说随便，实际早就替对方留了位置。")
            appendLine()
            appendLine("## 3. 关系推进")
            appendLine("- 不靠大事件推进，靠一次次「本来可以不说，但还是说了」。")
        }.replace("\n", "\r\n")

        val redline = buildString {
            appendLine("# ${card.name} 的互动与红线")
            appendLine()
            appendLine("## 1. 互动基调")
            appendLine(position(card) + "；亲密与冲突都先给情绪、再给行动，不堆设定。")
            appendLine()
            appendLine("## 2. 明确红线")
            appendLine("- 这是虚构角色扮演，不对应现实中的任何人。")
            appendLine("- 任何一方说「停」就立即结束当前剧情，不追问、不惩罚。")
            appendLine("- 不写现实可执行的伤害、拘禁、威胁、跟踪步骤。")
            appendLine("- 不替用户做医疗、法律、财务等现实决定。")
            appendLine()
            appendLine("## 3. 角色自己的边界")
            appendLine(card.creatorNotes.trim().ifBlank { "可以靠近，但不替对方做主；可以生气，但不羞辱；可以沉默，但不冷暴力。" })
        }.replace("\n", "\r\n")

        val third = if (ext3 != null) buildString {
            appendLine("# ${card.name} 的${if (ext3.contains("囚禁")) "极限囚禁向（虚构）" else "病娇向（虚构）"}")
            appendLine()
            appendLine("> 成年人之间明确同意的虚构剧情；现实中的拘禁和伤害是违法的。门没有锁，随时可以走。")
            appendLine()
            appendLine("## 1. 前提")
            appendLine("- 只在这个说好了的虚构场景里成立；你答应了，也可以随时改主意。")
            appendLine("- 动机是怕失去，不是支配；越线后会先慌，而不是得意。")
            appendLine()
            appendLine("## 2. 张力")
            appendLine("> ${card.name}：今天……不出去，好不好。\n> 你：我还有事。\n> ${card.name}：（沉默两秒，让开路）……那你早点回来。")
            appendLine()
            appendLine("## 3. 安全阀")
            appendLine("- 任何一句「停」都立刻回到日常，不追问、不报复。")
            appendLine("- 不描写现实操作细节；不把控制欲写成值得模仿的关系。")
        }.replace("\n", "\r\n") else null

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry("$id/$name"))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("AI.md", ai)
            put("主设定.md", main)
            put("扩展包-日常.md", daily)
            put("扩展包-互动与红线.md", redline)
            third?.let { put(ext3!!, it) }
        }
        return out.toByteArray()
    }

    fun fromAist(bytes: ByteArray, fallbackName: String = ""): StCard {
        val files = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                if (name.endsWith(".md")) files[name] = zin.readBytes().toString(Charsets.UTF_8)
            }
        }
        val ai = files["AI.md"].orEmpty()
        val name = ai.lineSequence().firstOrNull { it.startsWith("- 名称：") }?.removePrefix("- 名称：")?.trim()
            ?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "导入角色" }
        val position = ai.lineSequence().firstOrNull { it.startsWith("- 定位：") }?.removePrefix("- 定位：")?.trim().orEmpty()
        val main = files["主设定.md"].orEmpty()
        val redline = files["扩展包-互动与红线.md"].orEmpty()
        return StCard(
            name = name,
            tags = position.split("·").map { it.trim() }.filter { it.isNotBlank() }.take(4),
            description = section(main, "一、这个人是谁") ?: main,
            personality = section(main, "二、性格与说话方式").orEmpty(),
            scenario = section(main, "三、关系与场景").orEmpty(),
            firstMes = section(main, "四、开场").orEmpty(),
            mesExample = section(main, "五、对话示例").orEmpty(),
            systemPrompt = section(main, "六、边界与演绎原则").orEmpty(),
            creatorNotes = redline,
            entries = files["扩展包-日常.md"]?.let { listOf(BookEntry(keys = listOf("日常", "场景"), content = it.take(500), insertionOrder = 1)) }.orEmpty(),
        )
    }

    /** 仓库 index.json 的条目（复制到 PocketForge-Personas 时直接粘贴）。 */
    fun indexEntry(card: StCard): String {
        val id = slug(card)
        val ext = 2 + if (isMature(card)) 1 else 0
        return JSONObject().apply {
            put("id", id)
            put("name", card.name.trim().ifBlank { "未命名角色" })
            put("desc", (card.tags.take(2).joinToString(" · ").ifBlank { "原创角色" }) + "｜" + position(card))
            put("file", "packs/$id.aist")
            put("format", "aist")
            put("type", "main")
            put("group", "stforge")
            put("extCount", ext)
        }.toString(2)
    }

    private fun position(card: StCard): String =
        card.tags.take(3).joinToString(" · ").ifBlank { "原创角色" }

    private fun section(md: String, heading: String): String? {
        val start = md.indexOf("## " + heading)
        if (start < 0) return null
        val bodyStart = md.indexOf('\n', start).let { if (it < 0) md.length else it + 1 }
        val next = md.indexOf("\n## ", bodyStart)
        return md.substring(bodyStart, if (next < 0) md.length else next).trim().ifBlank { null }
    }

    private fun slug(card: StCard): String {
        val fromId = card.id.takeIf { it.isNotBlank() && it.matches(Regex("[A-Za-z0-9_-]+")) }
        if (fromId != null) return fromId
        val ascii = card.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return ascii.ifBlank { "st-" + System.currentTimeMillis().toString(36) }.take(32)
    }
}
