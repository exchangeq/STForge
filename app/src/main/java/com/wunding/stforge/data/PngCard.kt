package com.wunding.stforge.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

object PngCard {
    /** 生成角色卡封面：渐变底 + 大首字 + 名字 + 标签，全部代码绘制。 */
    fun avatar(card: StCard, w: Int = 512, h: Int = 768): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val seed = (card.name + card.id).hashCode()
        val hue = ((seed % 360) + 360) % 360
        val top = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.22f))
        val bottom = Color.HSVToColor(floatArrayOf(((hue + 40) % 360).toFloat(), 0.55f, 0.10f))
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val initial = card.name.trim().take(1).ifBlank { "?" }
        val ink = Paint().apply {
            color = Color.argb(235, 245, 245, 240)
            textSize = h * 0.30f
            typeface = Typeface.create("serif", Typeface.BOLD)
            isAntiAlias = true
        }
        val iw = ink.measureText(initial)
        c.drawText(initial, (w - iw) / 2f, h * 0.48f, ink)

        val namePaint = Paint().apply {
            color = Color.argb(240, 250, 248, 240)
            textSize = w * 0.085f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
        }
        val name = card.name.take(14)
        val tw = namePaint.measureText(name)
        c.drawText(name, (w - tw) / 2f, h * 0.68f, namePaint)

        val tagPaint = Paint().apply {
            color = Color.argb(170, 240, 238, 225)
            textSize = w * 0.042f
            isAntiAlias = true
        }
        val tagText = card.tags.take(2).joinToString(" · ").ifBlank { "SillyTavern 角色卡" }
        c.drawText(tagText, (w - tagPaint.measureText(tagText)) / 2f, h * 0.76f, tagPaint)

        val line = Paint().apply { color = Color.argb(90, 255, 255, 255) }
        c.drawRect(w * 0.35f, h * 0.82f, w * 0.65f, h * 0.82f + 2f, line)

        val foot = Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            textSize = w * 0.032f
            isAntiAlias = true
        }
        c.drawText("STForge · chara card v2", (w - foot.measureText("STForge · chara card v2")) / 2f, h * 0.88f, foot)
        return bmp
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun be32(v: Int) {
            out.write((v ushr 24) and 0xFF); out.write((v ushr 16) and 0xFF)
            out.write((v ushr 8) and 0xFF); out.write(v and 0xFF)
        }
        be32(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes); out.write(data)
        val crc = CRC32().apply { update(typeBytes); update(data) }
        be32(crc.value.toInt())
        return out.toByteArray()
    }

    /** 把 base64(JSON) 写进 PNG 的 tEXt(chara)，SillyTavern 可直接识别。 */
    fun embed(png: ByteArray, key: String, value: String): ByteArray {
        val sig = png.copyOfRange(0, 8)
        val out = ByteArrayOutputStream()
        out.write(sig)
        var pos = 8
        var inserted = false
        while (pos + 8 <= png.size) {
            val len = ((png[pos].toInt() and 0xFF) shl 24) or ((png[pos + 1].toInt() and 0xFF) shl 16) or
                ((png[pos + 2].toInt() and 0xFF) shl 8) or (png[pos + 3].toInt() and 0xFF)
            val type = String(png, pos + 4, 4, Charsets.US_ASCII)
            val total = 12 + len
            if (type == "IEND" && !inserted) {
                val textData = ByteArrayOutputStream().apply {
                    write(key.toByteArray(Charsets.US_ASCII)); write(0)
                    write(value.toByteArray(Charsets.US_ASCII))
                }.toByteArray()
                out.write(chunk("tEXt", textData)); inserted = true
            }
            out.write(png, pos, total)
            pos += total
        }
        return out.toByteArray()
    }

    /** 从 PNG 里取 tEXt/iTXt 的 chara 内容并 base64 解码。 */
    fun extractChara(png: ByteArray): String? {
        var pos = 8
        while (pos + 8 <= png.size) {
            val len = ((png[pos].toInt() and 0xFF) shl 24) or ((png[pos + 1].toInt() and 0xFF) shl 16) or
                ((png[pos + 2].toInt() and 0xFF) shl 8) or (png[pos + 3].toInt() and 0xFF)
            if (len < 0 || pos + 12 + len > png.size) break
            val type = String(png, pos + 4, 4, Charsets.US_ASCII)
            if (type == "tEXt" || type == "iTXt") {
                val data = png.copyOfRange(pos + 8, pos + 8 + len)
                val zero = data.indexOfFirst { it == 0.toByte() }
                if (zero > 0) {
                    val keyword = String(data, 0, zero, Charsets.US_ASCII)
                    if (keyword == "chara" || keyword == "ccv3") {
                        val body = String(data, zero + 1, data.size - zero - 1, Charsets.UTF_8)
                        val b64 = body.substringAfter("base64,", body)
                        return runCatching { String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8) }
                            .getOrNull()
                    }
                }
            }
            pos += 12 + len
        }
        return null
    }

    fun toPng(bmp: Bitmap): ByteArray = ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
}
