//go:build linux

package main

/*
#include <stdint.h>

typedef void (*void_cb)();
typedef void (*menu_item_cb)(uint32_t id);

static void call_void(void_cb f) { if (f) { f(); } }
static void call_menu_item(menu_item_cb f, uint32_t id) { if (f) { f(id); } }
*/
import "C"
import (
	"log"
	"unsafe"

	systraypkg "github.com/energye/systray"
)

var (
	cbReady     C.void_cb
	cbExit      C.void_cb
	cbOnClick   C.void_cb
	cbOnRClick  C.void_cb
	cbOnMenuSel C.menu_item_cb

	startFn func()
	endFn   func()

	running bool
)

//export Systray_InitCallbacks
func Systray_InitCallbacks(ready C.void_cb, exit C.void_cb, onClick C.void_cb, onRClick C.void_cb, onMenuItem C.menu_item_cb) {
	cbReady = ready
	cbExit = exit
	cbOnClick = onClick
	cbOnRClick = onRClick
	cbOnMenuSel = onMenuItem

	// Wire Go-level callbacks to call out to the provided C callbacks
	systraypkg.SetOnClick(func(menu systraypkg.IMenu) { C.call_void(cbOnClick) })
	systraypkg.SetOnRClick(func(menu systraypkg.IMenu) { C.call_void(cbOnRClick) })
	systraypkg.SetOnDClick(func(menu systraypkg.IMenu) { C.call_void(cbOnClick) }) // reuse onClick for double click
	systraypkg.SetOnMenuItemSelected(func(id uint32) { C.call_menu_item(cbOnMenuSel, C.uint(id)) })
}

//export Systray_Run
func Systray_Run() {
	systraypkg.Run(
		func() { running = true; C.call_void(cbReady) },
		func() { running = false; C.call_void(cbExit) },
	)
}

//export Systray_PrepareExternalLoop
func Systray_PrepareExternalLoop() {
	startFn, endFn = systraypkg.RunWithExternalLoop(
		func() { running = true; C.call_void(cbReady) },
		func() { running = false; C.call_void(cbExit) },
	)
}

//export Systray_NativeStart
func Systray_NativeStart() {
	if startFn != nil {
		startFn()
	}
}

//export Systray_NativeEnd
func Systray_NativeEnd() {
	if endFn != nil {
		endFn()
	}
	// ensure state cleaned for next run
	running = false
	startFn = nil
	endFn = nil
}

//export Systray_Quit
func Systray_Quit() { running = false; systraypkg.Quit() }

//export Systray_SetIcon
func Systray_SetIcon(iconBytes *C.char, length C.int) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in SetIcon: %v", r) } }()
	if !running {
		return
	}
	if iconBytes == nil || length <= 0 {
		return
	}
	b := C.GoBytes(unsafe.Pointer(iconBytes), length)
	systraypkg.SetIcon(b)
}

//export Systray_SetTitle
func Systray_SetTitle(title *C.char) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in SetTitle: %v", r) } }()
	if !running {
		return
	}
	if title == nil {
		return
	}
	systraypkg.SetTitle(C.GoString(title))
}

//export Systray_SetTooltip
func Systray_SetTooltip(tooltip *C.char) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in SetTooltip: %v", r) } }()
	if !running {
		return
	}
	if tooltip == nil {
		return
	}
	systraypkg.SetTooltip(C.GoString(tooltip))
}

//export Systray_AddMenuItem
func Systray_AddMenuItem(title *C.char, tooltip *C.char) C.uint {
	var t, tt string
	if title != nil {
		t = C.GoString(title)
	}
	if tooltip != nil {
		tt = C.GoString(tooltip)
	}
	item := systraypkg.AddMenuItem(t, tt)
	return C.uint(item.ID())
}

//export Systray_AddMenuItemCheckbox
func Systray_AddMenuItemCheckbox(title *C.char, tooltip *C.char, checked C.int) C.uint {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in AddMenuItemCheckbox: %v", r) } }()
	if !running {
		return 0
	}
	var t, tt string
	if title != nil {
		t = C.GoString(title)
	}
	if tooltip != nil {
		tt = C.GoString(tooltip)
	}
	item := systraypkg.AddMenuItemCheckbox(t, tt, checked != 0)
	return C.uint(item.ID())
}

//export Systray_AddSeparator
func Systray_AddSeparator() {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in AddSeparator: %v", r) } }()
	if !running {
		return
	}
	systraypkg.AddSeparator()
}

//export Systray_ResetMenu
func Systray_ResetMenu() {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in ResetMenu: %v", r) } }()
	if !running {
		return
	}
	systraypkg.ResetMenu()
}

// Submenu creation
//
//export Systray_AddSubMenuItem
func Systray_AddSubMenuItem(parentID C.uint, title *C.char, tooltip *C.char) C.uint {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in AddSubMenuItem: %v", r) } }()
	if !running {
		return 0
	}
	var t, tt string
	if title != nil {
		t = C.GoString(title)
	}
	if tooltip != nil {
		tt = C.GoString(tooltip)
	}
	id := systraypkg.AddSubMenuItemByID(uint32(parentID), t, tt)
	return C.uint(id)
}

//export Systray_AddSubMenuItemCheckbox
func Systray_AddSubMenuItemCheckbox(parentID C.uint, title *C.char, tooltip *C.char, checked C.int) C.uint {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in AddSubMenuItemCheckbox: %v", r) } }()
	if !running {
		return 0
	}
	var t, tt string
	if title != nil {
		t = C.GoString(title)
	}
	if tooltip != nil {
		tt = C.GoString(tooltip)
	}
	id := systraypkg.AddSubMenuItemCheckboxByID(uint32(parentID), t, tt, checked != 0)
	return C.uint(id)
}

// Per-item operations
//
//export Systray_MenuItem_SetTitle
func Systray_MenuItem_SetTitle(id C.uint, title *C.char) C.int {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_SetTitle: %v", r) } }()
	if !running {
		return 0
	}
	if title == nil {
		return 0
	}
	ok := systraypkg.SetMenuItemTitleByID(uint32(id), C.GoString(title))
	if ok {
		return 1
	}
	return 0
}

//export Systray_MenuItem_Enable
func Systray_MenuItem_Enable(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Enable: %v", r) } }()
	if !running { return }
	_ = systraypkg.EnableMenuItemByID(uint32(id))
}

//export Systray_MenuItem_Disable
func Systray_MenuItem_Disable(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Disable: %v", r) } }()
	if !running { return }
	_ = systraypkg.DisableMenuItemByID(uint32(id))
}

//export Systray_MenuItem_Show
func Systray_MenuItem_Show(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Show: %v", r) } }()
	if !running { return }
	_ = systraypkg.ShowMenuItemByID(uint32(id))
}

//export Systray_MenuItem_Hide
func Systray_MenuItem_Hide(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Hide: %v", r) } }()
	if !running { return }
	_ = systraypkg.HideMenuItemByID(uint32(id))
}

//export Systray_MenuItem_Check
func Systray_MenuItem_Check(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Check: %v", r) } }()
	if !running { return }
	_ = systraypkg.CheckMenuItemByID(uint32(id))
}

//export Systray_MenuItem_Uncheck
func Systray_MenuItem_Uncheck(id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in MenuItem_Uncheck: %v", r) } }()
	if !running { return }
	_ = systraypkg.UncheckMenuItemByID(uint32(id))
}

//export Systray_SetMenuItemIcon
func Systray_SetMenuItemIcon(iconBytes *C.char, length C.int, id C.uint) {
	defer func() { if r := recover(); r != nil { log.Printf("[systray] recovered panic in SetMenuItemIcon: %v", r) } }()
	if !running { return }
	if iconBytes == nil || length <= 0 {
		return
	}
	b := C.GoBytes(unsafe.Pointer(iconBytes), length)
	_ = systraypkg.SetMenuItemIconByID(uint32(id), b)
}
