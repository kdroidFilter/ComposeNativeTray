package com.kdroid.composetray.menu.api

/**
 * Represents a keyboard shortcut hint displayed alongside a menu item.
 * This is display-only — it does not register any global hotkey handler.
 *
 * On macOS, the shortcut renders as native key equivalent glyphs (e.g. ⌘S, ⇧⌘N).
 * On other platforms, this is currently a no-op.
 *
 * Example:
 * ```
 * Item("Save", shortcut = KeyShortcut(Key.S, meta = true)) { ... }
 * Item("New Window", shortcut = KeyShortcut(Key.N, meta = true, shift = true)) { ... }
 * ```
 */
data class KeyShortcut(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    /**
     * Returns the macOS NSEventModifierFlags bitmask for this shortcut.
     */
    internal fun toMacModifierMask(): Long {
        var mask = 0L
        if (meta) mask = mask or (1L shl 20)   // NSEventModifierFlagCommand
        if (shift) mask = mask or (1L shl 17)   // NSEventModifierFlagShift
        if (alt) mask = mask or (1L shl 19)     // NSEventModifierFlagOption
        if (ctrl) mask = mask or (1L shl 18)    // NSEventModifierFlagControl
        return mask
    }

    /**
     * Returns the key equivalent string for macOS NSMenuItem.
     * Lowercase letter for regular key, special Unicode for function/special keys.
     */
    internal fun toMacKeyEquivalent(): String = key.macKeyEquivalent
}

/**
 * Keyboard keys that can be used in [KeyShortcut].
 *
 * @property macKeyEquivalent The string value passed to NSMenuItem.setKeyEquivalent on macOS.
 */
enum class Key(internal val macKeyEquivalent: String) {
    A("a"), B("b"), C("c"), D("d"), E("e"), F("f"), G("g"), H("h"),
    I("i"), J("j"), K("k"), L("l"), M("m"), N("n"), O("o"), P("p"),
    Q("q"), R("r"), S("s"), T("t"), U("u"), V("v"), W("w"), X("x"),
    Y("y"), Z("z"),

    Num0("0"), Num1("1"), Num2("2"), Num3("3"), Num4("4"),
    Num5("5"), Num6("6"), Num7("7"), Num8("8"), Num9("9"),

    // Function keys (AppKit private-use Unicode)
    F1("\uF704"), F2("\uF705"), F3("\uF706"), F4("\uF707"),
    F5("\uF708"), F6("\uF709"), F7("\uF70A"), F8("\uF70B"),
    F9("\uF70C"), F10("\uF70D"), F11("\uF70E"), F12("\uF70F"),

    // Special keys
    Return("\r"),
    Tab("\t"),
    Space(" "),
    Escape("\u001B"),
    Delete("\u007F"),
    ForwardDelete("\uF728"),
    UpArrow("\uF700"),
    DownArrow("\uF701"),
    LeftArrow("\uF702"),
    RightArrow("\uF703"),
    Home("\uF729"),
    End("\uF72B"),
    PageUp("\uF72C"),
    PageDown("\uF72D"),
    Minus("-"),
    Equal("="),
    LeftBracket("["),
    RightBracket("]"),
    Backslash("\\"),
    Semicolon(";"),
    Quote("'"),
    Comma(","),
    Period("."),
    Slash("/"),
    Backquote("`"),
}
