package net.azisaba.craftgui.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender

/**
 * 設定ファイルやメッセージで使う「&」「§」形式のカラーコードと Adventure Component の相互変換。
 */
object Text {

    private val legacySerializer = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR)
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    private val AMPERSAND_CODE = Regex("&([0-9a-fk-orxA-FK-ORX])")

    /** 「&」「§」どちらのカラーコードも解釈して Component にする */
    @JvmStatic
    fun legacy(text: String): Component =
        legacySerializer.deserialize(AMPERSAND_CODE.replace(text, "${LegacyComponentSerializer.SECTION_CHAR}$1"))

    /** アイテム名・Lore 用。既定の斜体を打ち消す */
    @JvmStatic
    fun item(text: String): Component = noItalic(legacy(text))

    /** 複数の部品を 1 行にまとめる (アイテム名・Lore 用) */
    @JvmStatic
    fun itemLine(vararg parts: Component): Component =
        Component.text().decoration(TextDecoration.ITALIC, false).append(*parts).build()

    @JvmStatic
    fun noItalic(component: Component): Component = component.decoration(TextDecoration.ITALIC, false)

    /** 色指定の無い部分を白にする (表示名の埋め込み用) */
    @JvmStatic
    fun whiteIfUncolored(component: Component): Component = component.colorIfAbsent(NamedTextColor.WHITE)

    @JvmStatic
    fun plain(component: Component): String = PlainTextComponentSerializer.plainText().serialize(component)

    @JvmStatic
    fun toLegacy(component: Component): String = legacySerializer.serialize(component)
}

fun CommandSender.sendLegacy(text: String) {
    sendMessage(Text.legacy(text))
}
