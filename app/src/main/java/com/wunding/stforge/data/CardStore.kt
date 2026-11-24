package com.wunding.stforge.data

import android.content.Context
import android.net.Uri
import java.io.File

object CardStore {
    fun dir(ctx: Context): File = File((ctx.getExternalFilesDir(null) ?: ctx.filesDir), "cards").apply { mkdirs() }
    fun exportDir(ctx: Context): File = File((ctx.getExternalFilesDir(null) ?: ctx.filesDir), "exports").apply { mkdirs() }

    fun list(ctx: Context): List<StCard> =
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> runCatching { StCard.fromJson(f.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun save(ctx: Context, card: StCard): StCard {
        val id = card.id.ifBlank { "st-" + System.currentTimeMillis().toString(36) }
        val fixed = card.copy(id = id, updatedAt = System.currentTimeMillis())
        File(dir(ctx), "$id.json").writeText(fixed.toV2Json())
        return fixed
    }

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.json").delete() }

    fun importCard(ctx: Context, uri: Uri): StCard {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("读不到文件")
        val name = uri.lastPathSegment.orEmpty()
        val json = if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
            PngCard.extractChara(bytes) ?: throw IllegalStateException("这张 PNG 里没有 chara 数据，不是 SillyTavern 角色卡")
        } else String(bytes, Charsets.UTF_8)
        val card = StCard.fromJson(json)
        return save(ctx, card.copy(id = "", updatedAt = System.currentTimeMillis()))
    }

    fun exportJson(ctx: Context, card: StCard): File {
        val f = File(exportDir(ctx), safe(card.name) + ".json")
        f.writeText(card.toV2Json())
        return f
    }

    fun exportPng(ctx: Context, card: StCard): File {
        val bmp = PngCard.avatar(card)
        val png = PngCard.embed(PngCard.toPng(bmp), "chara", android.util.Base64.encodeToString(card.toV2Json().toByteArray(), android.util.Base64.NO_WRAP))
        val f = File(exportDir(ctx), safe(card.name) + ".png")
        f.writeBytes(png)
        return f
    }

    fun safe(name: String): String = name.trim().ifBlank { "card" }.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(24)
}
