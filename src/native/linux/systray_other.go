//go:build !linux && !freebsd && !openbsd && !netbsd
// +build !linux,!freebsd,!openbsd,!netbsd

package systray

import (
	"errors"
	"fmt"
	"runtime"
)

var errUnsupportedPlatform = errors.New("systray: unsupported platform, this library is Linux-only")

// IMenu interface
type IMenu interface {
	ShowMenu() error
}

// Run initializes GUI and starts the event loop, then invokes the onReady
// callback. It blocks until systray.Quit() is called.
func Run(onReady, onExit func()) {
	fmt.Printf("systray: unsupported platform '%s', this library is Linux-only\n", runtime.GOOS)
	panic(errUnsupportedPlatform)
}

// RunWithExternalLoop allows the systemtray module to operate with other tookits.
// The returned start and end functions should be called by the toolkit when the application has started and will end.
func RunWithExternalLoop(onReady, onExit func()) (start, end func()) {
	fmt.Printf("systray: unsupported platform '%s', this library is Linux-only\n", runtime.GOOS)
	panic(errUnsupportedPlatform)
}

// CreateMenu is a no-op on non-Linux platforms
func CreateMenu() {
	panic(errUnsupportedPlatform)
}

// SetMenuNil is a no-op on non-Linux platforms
func SetMenuNil() {
	panic(errUnsupportedPlatform)
}

// SetIcon sets the systray icon.
func SetIcon(iconBytes []byte) {
	panic(errUnsupportedPlatform)
}

// SetTemplateIcon sets the systray icon as a template icon.
func SetTemplateIcon(templateIconBytes []byte, regularIconBytes []byte) {
	panic(errUnsupportedPlatform)
}

// SetTitle sets the systray title.
func SetTitle(title string) {
	panic(errUnsupportedPlatform)
}

// SetTooltip sets the systray tooltip.
func SetTooltip(tooltip string) {
	panic(errUnsupportedPlatform)
}

// SetOnClick sets the on click callback.
func SetOnClick(fn func(menu IMenu)) {
	panic(errUnsupportedPlatform)
}

// SetOnDClick sets the on double click callback.
func SetOnDClick(fn func(menu IMenu)) {
	panic(errUnsupportedPlatform)
}

// SetOnRClick sets the on right click callback.
func SetOnRClick(fn func(menu IMenu)) {
	panic(errUnsupportedPlatform)
}

// SetDClickTimeMinInterval sets the minimum time interval for double clicks.
func SetDClickTimeMinInterval(value int64) {
	panic(errUnsupportedPlatform)
}

// Quit the systray.
func Quit() {
	panic(errUnsupportedPlatform)
}

// AddMenuItem adds a menu item.
func AddMenuItem(title string, tooltip string) *MenuItem {
	panic(errUnsupportedPlatform)
}

// AddMenuItemCheckbox adds a menu item with a checkbox.
func AddMenuItemCheckbox(title string, tooltip string, checked bool) *MenuItem {
	panic(errUnsupportedPlatform)
}

// AddSeparator adds a separator.
func AddSeparator() {
	panic(errUnsupportedPlatform)
}

// ResetMenu resets the menu.
func ResetMenu() {
	panic(errUnsupportedPlatform)
}

// MenuItem methods

// AddSubMenuItem adds a submenu item.
func (item *MenuItem) AddSubMenuItem(title string, tooltip string) *MenuItem {
	panic(errUnsupportedPlatform)
}

// AddSubMenuItemCheckbox adds a submenu item with a checkbox.
func (item *MenuItem) AddSubMenuItemCheckbox(title string, tooltip string, checked bool) *MenuItem {
	panic(errUnsupportedPlatform)
}

// SetTitle sets the menu item title.
func (item *MenuItem) SetTitle(title string) {
	panic(errUnsupportedPlatform)
}

// SetTooltip sets the menu item tooltip.
func (item *MenuItem) SetTooltip(tooltip string) {
	panic(errUnsupportedPlatform)
}

// Disabled checks if the menu item is disabled.
func (item *MenuItem) Disabled() bool {
	panic(errUnsupportedPlatform)
}

// Enable enables the menu item.
func (item *MenuItem) Enable() {
	panic(errUnsupportedPlatform)
}

// Disable disables the menu item.
func (item *MenuItem) Disable() {
	panic(errUnsupportedPlatform)
}

// Hide hides the menu item.
func (item *MenuItem) Hide() {
	panic(errUnsupportedPlatform)
}

// Show shows the menu item.
func (item *MenuItem) Show() {
	panic(errUnsupportedPlatform)
}

// Checked returns if the menu item is checked.
func (item *MenuItem) Checked() bool {
	panic(errUnsupportedPlatform)
}

// Check checks the menu item.
func (item *MenuItem) Check() {
	panic(errUnsupportedPlatform)
}

// Uncheck unchecks the menu item.
func (item *MenuItem) Uncheck() {
	panic(errUnsupportedPlatform)
}

// Click sets the click callback.
func (item *MenuItem) Click(fn func()) {
	panic(errUnsupportedPlatform)
}

// SetIcon sets the menu item icon.
func (item *MenuItem) SetIcon(iconBytes []byte) {
	panic(errUnsupportedPlatform)
}

// SetTemplateIcon sets the menu item template icon.
func (item *MenuItem) SetTemplateIcon(templateIconBytes []byte, regularIconBytes []byte) {
	panic(errUnsupportedPlatform)
}
