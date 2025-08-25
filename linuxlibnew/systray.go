// Package systray is a Linux-only Go library to place an icon and menu in the notification area.
package systray

import (
	"fmt"
	"log"
	"runtime"
	"sync"
	"sync/atomic"
)

var (
	systrayReady  func()
	systrayExit   func()
	menuItems     = make(map[uint32]*MenuItem)
	menuItemsLock sync.RWMutex

	currentID             = uint32(0)
	quitOnce              sync.Once
	dClickTimeMinInterval int64 = 500
)

func init() {
	runtime.LockOSThread()
}

type IMenu interface {
	ShowMenu() error
}

// MenuItem is used to keep track each menu item of systray.
// Don't create it directly, use the one systray.AddMenuItem() returned
type MenuItem struct {
	// ClickedCh is the channel which will be notified when the menu item is clicked
	click func()

	// id uniquely identify a menu item, not supposed to be modified
	id uint32
	// title is the text shown on menu item
	title string
	// tooltip is the text shown when pointing to menu item
	tooltip string
	// shortcutKey Menu shortcut key
	shortcutKey string
	// disabled menu item is grayed out and has no effect when clicked
	disabled bool
	// checked menu item has a tick before the title
	checked bool
	// has the menu item a checkbox (Linux)
	isCheckable bool
	// parent item, for sub menus
	parent *MenuItem
}

// ID returns the unique ID of the menu item
func (item *MenuItem) ID() uint32 { return item.id }

func (item *MenuItem) Click(fn func()) {
	item.click = fn
}

func (item *MenuItem) String() string {
	if item.parent == nil {
		return fmt.Sprintf("MenuItem[%d, %q]", item.id, item.title)
	}
	return fmt.Sprintf("MenuItem[%d, parent %d, %q]", item.id, item.parent.id, item.title)
}

// newMenuItem returns a populated MenuItem object
func newMenuItem(title string, tooltip string, parent *MenuItem) *MenuItem {
	return &MenuItem{
		id:          atomic.AddUint32(&currentID, 1),
		title:       title,
		tooltip:     tooltip,
		shortcutKey: "",
		disabled:    false,
		checked:     false,
		isCheckable: false,
		parent:      parent,
	}
}

// Run initializes GUI and starts the event loop, then invokes the onReady
// callback. It blocks until systray.Quit() is called.
func Run(onReady, onExit func()) {
	setInternalLoop(true)
	Register(onReady, onExit)

	nativeLoop()
}

// 设置鼠标左键双击事件的时间间隔 默认500毫秒
func SetDClickTimeMinInterval(value int64) {
	dClickTimeMinInterval = value
}

// 设置托盘鼠标左键点击事件
func SetOnClick(fn func(menu IMenu)) {
	setOnClick(fn)
}

// 设置托盘鼠标左键双击事件
func SetOnDClick(fn func(menu IMenu)) {
	setOnDClick(fn)
}

// 设置托盘鼠标右键事件反馈回调
// 支持windows 和 macosx，不支持linux
// 设置事件，菜单默认将不展示，通过menu.ShowMenu()函数显示
// 未设置事件，默认右键显示托盘菜单
// macosx ShowMenu()只支持OnRClick函数内调用
func SetOnRClick(fn func(menu IMenu)) {
	setOnRClick(fn)
}

// RunWithExternalLoop allows the systemtray module to operate with other tookits.
// The returned start and end functions should be called by the toolkit when the application has started and will end.
func RunWithExternalLoop(onReady, onExit func()) (start, end func()) {
	Register(onReady, onExit)

	return nativeStart, func() {
		nativeEnd()
		Quit()
	}
}

// Register initializes GUI and registers the callbacks but relies on the
// caller to run the event loop somewhere else. It's useful if the program
// needs to show other UI elements, for example, webview.
// To overcome some OS weirdness, On macOS versions before Catalina, calling
// this does exactly the same as Run().
func Register(onReady func(), onExit func()) {
	if onReady == nil {
		systrayReady = nil
	} else {
		var readyCh = make(chan interface{})
		// Run onReady on separate goroutine to avoid blocking event loop
		go func() {
			<-readyCh
			onReady()
		}()
		systrayReady = func() {
			systrayReady = nil
			close(readyCh)
		}
	}
	// unlike onReady, onExit runs in the event loop to make sure it has time to
	// finish before the process terminates
	if onExit == nil {
		onExit = func() {}
	}
	systrayExit = onExit
	registerSystray()
}

// ResetMenu will remove all menu items
func ResetMenu() {
	resetMenu()
}

// Quit the systray
func Quit() {
	quitOnce.Do(quit)
}

// AddMenuItem adds a menu item with the designated title and tooltip.
// It can be safely invoked from different goroutines.
// Created menu items are checkable on Windows and OSX by default. For Linux you have to use AddMenuItemCheckbox
func AddMenuItem(title string, tooltip string) *MenuItem {
	item := newMenuItem(title, tooltip, nil)
	item.update()
	return item
}

// AddMenuItemCheckbox adds a menu item with the designated title and tooltip and a checkbox for Linux.
// It can be safely invoked from different goroutines.
// On Windows and OSX this is the same as calling AddMenuItem
func AddMenuItemCheckbox(title string, tooltip string, checked bool) *MenuItem {
	item := newMenuItem(title, tooltip, nil)
	item.isCheckable = true
	item.checked = checked
	item.update()
	return item
}

// AddSeparator adds a separator bar to the menu
func AddSeparator() {
	addSeparator(atomic.AddUint32(&currentID, 1))
}

// AddSubMenuItem adds a nested sub-menu item with the designated title and tooltip.
// It can be safely invoked from different goroutines.
// Created menu items are checkable on Windows and OSX by default. For Linux you have to use AddSubMenuItemCheckbox
func (item *MenuItem) AddSubMenuItem(title string, tooltip string) *MenuItem {
	child := newMenuItem(title, tooltip, item)
	child.update()
	return child
}

// AddSubMenuItemCheckbox adds a nested sub-menu item with the designated title and tooltip and a checkbox for Linux.
// It can be safely invoked from different goroutines.
// On Windows and OSX this is the same as calling AddSubMenuItem
func (item *MenuItem) AddSubMenuItemCheckbox(title string, tooltip string, checked bool) *MenuItem {
	child := newMenuItem(title, tooltip, item)
	child.isCheckable = true
	child.checked = checked
	child.update()
	return child
}

// SetTitle set the text to display on a menu item
func (item *MenuItem) SetTitle(title string) {
	item.title = title
	item.update()
}

// SetTooltip set the tooltip to show when mouse hover
func (item *MenuItem) SetTooltip(tooltip string) {
	item.tooltip = tooltip
	item.update()
}

// Disabled checks if the menu item is disabled
func (item *MenuItem) Disabled() bool {
	return item.disabled
}

// Enable a menu item regardless if it's previously enabled or not
func (item *MenuItem) Enable() {
	item.disabled = false
	item.update()
}

// Disable a menu item regardless if it's previously disabled or not
func (item *MenuItem) Disable() {
	item.disabled = true
	item.update()
}

// Hide hides a menu item
func (item *MenuItem) Hide() {
	hideMenuItem(item)
}

// Show shows a previously hidden menu item
func (item *MenuItem) Show() {
	showMenuItem(item)
}

// Checked returns if the menu item has a check mark
func (item *MenuItem) Checked() bool {
	return item.checked
}

// Check a menu item regardless if it's previously checked or not
func (item *MenuItem) Check() {
	item.checked = true
	item.update()
}

// Uncheck a menu item regardless if it's previously unchecked or not
func (item *MenuItem) Uncheck() {
	item.checked = false
	item.update()
}

// update propagates changes on a menu item to systray
func (item *MenuItem) update() {
	menuItemsLock.Lock()
	menuItems[item.id] = item
	menuItemsLock.Unlock()
	addOrUpdateMenuItem(item)
}

var menuItemSelected func(id uint32)

func SetOnMenuItemSelected(fn func(id uint32)) {
	menuItemSelected = fn
}

func systrayMenuItemSelected(id uint32) {
	menuItemsLock.RLock()
	item, ok := menuItems[id]
	menuItemsLock.RUnlock()
	if !ok {
		log.Printf("systray error: no menu item with ID %d\n", id)
		return
	}
	if item.click != nil {
		item.click()
	}
	if menuItemSelected != nil {
		menuItemSelected(id)
	}
}

// --- Helpers to control menu items by ID for external (JNA) callers ---

// getMenuItem returns the menu item and whether it exists
func getMenuItem(id uint32) (*MenuItem, bool) {
	menuItemsLock.RLock()
	defer menuItemsLock.RUnlock()
	item, ok := menuItems[id]
	return item, ok
}

// AddSubMenuItemByID creates a child under the given parent id and returns the new id; 0 on failure
func AddSubMenuItemByID(parentID uint32, title, tooltip string) uint32 {
	parent, ok := getMenuItem(parentID)
	if !ok || parent == nil {
		return 0
	}
	child := parent.AddSubMenuItem(title, tooltip)
	return child.ID()
}

// AddSubMenuItemCheckboxByID creates a checkable child under the given parent id and returns the new id; 0 on failure
func AddSubMenuItemCheckboxByID(parentID uint32, title, tooltip string, checked bool) uint32 {
	parent, ok := getMenuItem(parentID)
	if !ok || parent == nil {
		return 0
	}
	child := parent.AddSubMenuItemCheckbox(title, tooltip, checked)
	return child.ID()
}

// SetMenuItemTitleByID sets the title of a menu item; returns false if not found
func SetMenuItemTitleByID(id uint32, title string) bool {
	if item, ok := getMenuItem(id); ok {
		item.SetTitle(title)
		return true
	}
	return false
}

// EnableMenuItemByID enables a menu item; returns false if not found
func EnableMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Enable()
		return true
	}
	return false
}

// DisableMenuItemByID disables a menu item; returns false if not found
func DisableMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Disable()
		return true
	}
	return false
}

// ShowMenuItemByID shows a menu item; returns false if not found
func ShowMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Show()
		return true
	}
	return false
}

// HideMenuItemByID hides a menu item; returns false if not found
func HideMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Hide()
		return true
	}
	return false
}

// CheckMenuItemByID checks a menu item; returns false if not found
func CheckMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Check()
		return true
	}
	return false
}

// UncheckMenuItemByID unchecks a menu item; returns false if not found
func UncheckMenuItemByID(id uint32) bool {
	if item, ok := getMenuItem(id); ok {
		item.Uncheck()
		return true
	}
	return false
}

// SetMenuItemIconByID sets the icon of a menu item; returns false if not found
func SetMenuItemIconByID(id uint32, iconBytes []byte) bool {
	if item, ok := getMenuItem(id); ok {
		item.SetIcon(iconBytes)
		return true
	}
	return false
}
