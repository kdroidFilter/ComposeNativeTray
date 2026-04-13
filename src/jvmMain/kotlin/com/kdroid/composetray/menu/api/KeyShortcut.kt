package com.kdroid.composetray.menu.api

/**
 * Represents a keyboard shortcut hint displayed alongside a menu item.
 * This is display-only — it does not register any global hotkey handler.
 *
 * On macOS, the shortcut renders as native key equivalent glyphs (e.g. ⌘S, ⇧⌘N).
 * On Linux, the shortcut is serialized as a DBusMenu `shortcut` property
 * and rendered by the desktop environment's indicator renderer (KDE, GNOME, etc.).
 * On Windows, this is currently a no-op.
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

    /**
     * Returns the DBusMenu key name for Linux shortcut hints.
     */
    internal fun toLinuxKey(): String = key.linuxKeyName
}

/**
 * Keyboard keys that can be used in [KeyShortcut].
 *
 * @property macKeyEquivalent The string value passed to NSMenuItem.setKeyEquivalent on macOS.
 * @property linuxKeyName The DBusMenu key name string for Linux (follows XKB naming).
 */
enum class Key(
    internal val macKeyEquivalent: String,
    internal val linuxKeyName: String,
) {
    A("a", "a"), B("b", "b"), C("c", "c"), D("d", "d"), E("e", "e"),
    F("f", "f"), G("g", "g"), H("h", "h"), I("i", "i"), J("j", "j"),
    K("k", "k"), L("l", "l"), M("m", "m"), N("n", "n"), O("o", "o"),
    P("p", "p"), Q("q", "q"), R("r", "r"), S("s", "s"), T("t", "t"),
    U("u", "u"), V("v", "v"), W("w", "w"), X("x", "x"), Y("y", "y"),
    Z("z", "z"),

    Num0("0", "0"), Num1("1", "1"), Num2("2", "2"), Num3("3", "3"), Num4("4", "4"),
    Num5("5", "5"), Num6("6", "6"), Num7("7", "7"), Num8("8", "8"), Num9("9", "9"),

    // Function keys (AppKit private-use Unicode)
    F1("\uF704", "F1"), F2("\uF705", "F2"), F3("\uF706", "F3"), F4("\uF707", "F4"),
    F5("\uF708", "F5"), F6("\uF709", "F6"), F7("\uF70A", "F7"), F8("\uF70B", "F8"),
    F9("\uF70C", "F9"), F10("\uF70D", "F10"), F11("\uF70E", "F11"), F12("\uF70F", "F12"),

    // Special keys
    Return("\r", "Return"),
    Tab("\t", "Tab"),
    Space(" ", "space"),
    Escape("\u001B", "Escape"),
    Delete("\u007F", "BackSpace"),
    ForwardDelete("\uF728", "Delete"),
    UpArrow("\uF700", "Up"),
    DownArrow("\uF701", "Down"),
    LeftArrow("\uF702", "Left"),
    RightArrow("\uF703", "Right"),
    Home("\uF729", "Home"),
    End("\uF72B", "End"),
    PageUp("\uF72C", "Page_Up"),
    PageDown("\uF72D", "Page_Down"),
    Minus("-", "minus"),
    Equal("=", "equal"),
    LeftBracket("[", "bracketleft"),
    RightBracket("]", "bracketright"),
    Backslash("\\", "backslash"),
    Semicolon(";", "semicolon"),
    Quote("'", "apostrophe"),
    Comma(",", "comma"),
    Period(".", "period"),
    Slash("/", "slash"),
    Backquote("`", "grave"),
}
