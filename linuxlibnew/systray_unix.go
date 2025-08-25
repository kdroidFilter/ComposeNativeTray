//go:build linux || freebsd || openbsd || netbsd
// +build linux freebsd openbsd netbsd

// Note: you need github.com/knightpp/dbus-codegen-go installed from "custom" branch
//go:generate dbus-codegen-go -prefix org.kde -package notifier -output internal/generated/notifier/status_notifier_item.go internal/StatusNotifierItem.xml
//go:generate dbus-codegen-go -prefix com.canonical -package menu -output internal/generated/menu/dbus_menu.go internal/DbusMenu.xml

package systray

import (
	"bytes"
	"fmt"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"log"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/godbus/dbus/v5/introspect"
	"github.com/godbus/dbus/v5/prop"

	"github.com/energye/systray/internal/generated/menu"
	"github.com/energye/systray/internal/generated/notifier"
	dbus "github.com/godbus/dbus/v5"

	"golang.org/x/image/draw"
)

const (
	path     = "/StatusNotifierItem"
	menuPath = "/StatusNotifierMenu"
)

var (
	// to signal quitting the internal main loop
	quitChan = make(chan struct{})

	// instance is the current instance of our DBus tray server
	instance = &tray{menu: &menuLayout{}, menuVersion: 1}
)

// SetTemplateIcon sets the systray icon as a template icon (on macOS), falling back
// to a regular icon on other platforms. On Linux we just set the regular icon.
func SetTemplateIcon(_ []byte, regularIconBytes []byte) {
	SetIcon(regularIconBytes)
}

// SetIcon sets the systray icon (PNG/JPG/ICO supported by image/*).
func SetIcon(iconBytes []byte) {
	instance.lock.Lock()
	instance.iconData = iconBytes
	props := instance.props
	conn := instance.conn
	instance.lock.Unlock()

	if props == nil {
		return
	}

	// Build a multi-resolution pixmap list (ARGB32 big-endian) for better quality.
	pixmaps := buildPixmaps(iconBytes)
	props.SetMust("org.kde.StatusNotifierItem", "IconPixmap", pixmaps)

	// Keep tooltip icon consistent with main icon
	props.SetMust("org.kde.StatusNotifierItem", "ToolTip",
		dbus.MakeVariant(tooltip{V2: instance.tooltipTitle, V1: pixmaps}))

	if conn == nil {
		return
	}

	if err := notifier.Emit(conn, &notifier.StatusNotifierItem_NewIconSignal{
		Path: path,
		Body: &notifier.StatusNotifierItem_NewIconSignalBody{},
	}); err != nil {
		log.Printf("systray error: failed to emit new icon signal: %s\n", err)
	}
}

// SetTitle sets the systray title.
func SetTitle(t string) {
	instance.lock.Lock()
	instance.title = t
	props := instance.props
	conn := instance.conn
	instance.lock.Unlock()

	if props == nil {
		return
	}
	if dbusErr := props.Set("org.kde.StatusNotifierItem", "Title", dbus.MakeVariant(t)); dbusErr != nil {
		log.Printf("systray error: failed to set Title prop: %s\n", dbusErr)
		return
	}
	if conn == nil {
		return
	}
	if err := notifier.Emit(conn, &notifier.StatusNotifierItem_NewTitleSignal{
		Path: path,
		Body: &notifier.StatusNotifierItem_NewTitleSignalBody{},
	}); err != nil {
		log.Printf("systray error: failed to emit new title signal: %s\n", err)
	}
}

// SetTooltip sets the systray tooltip text and keeps the icon consistent.
func SetTooltip(tooltipTitle string) {
	instance.lock.Lock()
	instance.tooltipTitle = tooltipTitle
	props := instance.props
	iconData := instance.iconData
	instance.lock.Unlock()

	if props == nil {
		return
	}
	props.SetMust("org.kde.StatusNotifierItem", "ToolTip",
		dbus.MakeVariant(tooltip{
			V2: tooltipTitle,
			V1: buildPixmaps(iconData), // show same icon in tooltip if host uses it
		}),
	)
}

func (item *MenuItem) SetTemplateIcon(_ []byte, regularIconBytes []byte) {
	item.SetIcon(regularIconBytes)
}

func setInternalLoop(_ bool) {}

// CreateMenu creates the tray menu (no-op on Linux).
func CreateMenu() {}

// SetMenuNil sets the tray menu to nil (no-op on Linux).
func SetMenuNil() {}

func registerSystray() {}

func nativeLoop() int {
	nativeStart()
	<-quitChan
	nativeEnd()
	return 0
}

func nativeEnd() {
	systrayExit()

	// Snapshot and clear shared state under lock to make teardown idempotent.
	instance.lock.Lock()
	conn := instance.conn
	busName := instance.name
	props := instance.props
	menuProps := instance.menuProps
	_ = menuProps
	instance.conn = nil
	instance.props = nil
	instance.menuProps = nil
	instance.iconData = nil
	instance.title = ""
	instance.tooltipTitle = ""
	instance.lock.Unlock()

	if conn == nil {
		return
	}

	// Best-effort: mark status passive before unexport (optional).
	if props != nil {
		props.SetMust("org.kde.StatusNotifierItem", "Status", "Passive")
	}

	// Unexport SNI and menu interfaces so the host removes our icon/menu immediately.
	_ = notifier.UnexportStatusNotifierItem(conn, path)
	_ = menu.UnexportDbusmenu(conn, menuPath)

	// Unexport Properties and Introspectable on both paths.
	_ = conn.Export(nil, path, "org.freedesktop.DBus.Properties")
	_ = conn.Export(nil, menuPath, "org.freedesktop.DBus.Properties")
	_ = conn.Export(nil, path, "org.freedesktop.DBus.Introspectable")
	_ = conn.Export(nil, menuPath, "org.freedesktop.DBus.Introspectable")

	// Release our well-known bus name so watchers drop the item instantly.
	if busName != "" {
		_, _ = conn.ReleaseName(busName)
	}

	// Finally, close the connection asynchronously to avoid freezes.
	done := make(chan struct{})
	go func() {
		_ = conn.Close()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(300 * time.Millisecond):
		// Give up waiting; the background goroutine will finish later.
	}
}

func quit() {
	close(quitChan)
}

var usni = &UnimplementedStatusNotifierItem{}

var (
	lastClickMu sync.Mutex
	lastClickX int32
	lastClickY int32
)

type UnimplementedStatusNotifierItem struct {
	contextMenu       func(x int32, y int32)
	activate          func(x int32, y int32)
	dActivate         func(x int32, y int32)
	secondaryActivate func(x int32, y int32)
	scroll            func(delta int32, orientation string)
	dActivateTime     int64
}

func (*UnimplementedStatusNotifierItem) iface() string {
	return notifier.InterfaceStatusNotifierItem
}

func (m *UnimplementedStatusNotifierItem) ContextMenu(x int32, y int32) (err *dbus.Error) {
	// remember last right-click coordinates too (for context menu)
	lastClickMu.Lock()
	lastClickX, lastClickY = x, y
	lastClickMu.Unlock()
	if m.contextMenu != nil {
		m.contextMenu(x, y)
	} else {
		err = &dbus.ErrMsgUnknownMethod
	}
	return
}

func (m *UnimplementedStatusNotifierItem) Activate(x int32, y int32) (err *dbus.Error) {
	// remember last click coordinates
	lastClickMu.Lock()
	lastClickX, lastClickY = x, y
	lastClickMu.Unlock()

	if m.dActivateTime == 0 {
		m.dActivateTime = time.Now().UnixMilli()
	} else {
		nowMilli := time.Now().UnixMilli()
		if nowMilli-m.dActivateTime < dClickTimeMinInterval {
			m.dActivateTime = dClickTimeMinInterval
			if m.dActivate != nil {
				m.dActivate(x, y)
				return
			}
		} else {
			m.dActivateTime = nowMilli
		}
	}
	if m.activate != nil {
		m.activate(x, y)
	} else {
		err = &dbus.ErrMsgUnknownMethod
	}
	return
}

func (m *UnimplementedStatusNotifierItem) SecondaryActivate(x int32, y int32) (err *dbus.Error) {
	// remember last click coordinates for secondary activation as well
	lastClickMu.Lock()
	lastClickX, lastClickY = x, y
	lastClickMu.Unlock()
	if m.secondaryActivate != nil {
		m.secondaryActivate(x, y)
	} else {
		err = &dbus.ErrMsgUnknownMethod
	}
	return
}

func (m *UnimplementedStatusNotifierItem) Scroll(delta int32, orientation string) (err *dbus.Error) {
	if m.scroll != nil {
		m.scroll(delta, orientation)
	} else {
		err = &dbus.ErrMsgUnknownMethod
	}
	return
}

func setOnClick(fn func(menu IMenu)) { usni.activate = func(int32, int32) { fn(nil) } }
func setOnDClick(fn func(menu IMenu)) { usni.dActivate = func(int32, int32) { fn(nil) } }
func setOnRClick(dClick func(IMenu))  {}

// GetLastClickXY returns the last click coordinates captured from Activate/ContextMenu/SecondaryActivate.
func GetLastClickXY() (int32, int32) {
	lastClickMu.Lock()
	x, y := lastClickX, lastClickY
	lastClickMu.Unlock()
	return x, y
}

func nativeStart() {
	if systrayReady != nil {
		systrayReady()
	}
	conn, _ := dbus.ConnectSessionBus()
	if err := notifier.ExportStatusNotifierItem(conn, path, usni); err != nil {
		log.Printf("systray error: failed to export status notifier item: %s\n", err)
	}
	if err := menu.ExportDbusmenu(conn, menuPath, instance); err != nil {
		log.Printf("systray error: failed to export status notifier item: %s\n", err)
	}

	name := fmt.Sprintf("org.kde.StatusNotifierItem-%d-1", os.Getpid()) // id 1 for this process
	if _, err := conn.RequestName(name, dbus.NameFlagDoNotQueue); err != nil {
		log.Printf("systray error: failed to request name: %s\n", err)
	}
	// store requested bus name for later immediate release on dispose
	instance.lock.Lock()
	instance.name = name
	instance.lock.Unlock()
	props, err := prop.Export(conn, path, instance.createPropSpec())
	if err != nil {
		log.Printf("systray error: failed to export notifier item properties to bus: %s\n", err)
		return
	}
	menuProps, err := prop.Export(conn, menuPath, createMenuPropSpec())
	if err != nil {
		log.Printf("systray error: failed to export notifier menu properties to bus: %s\n", err)
		return
	}

	node := introspect.Node{
		Name: path,
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			notifier.IntrospectDataStatusNotifierItem,
		},
	}
	if err := conn.Export(introspect.NewIntrospectable(&node), path, "org.freedesktop.DBus.Introspectable"); err != nil {
		log.Printf("systray error: failed to export node introspection: %s\n", err)
		return
	}

	menuNode := introspect.Node{
		Name: menuPath,
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			menu.IntrospectDataDbusmenu,
		},
	}
	if err := conn.Export(introspect.NewIntrospectable(&menuNode), menuPath, "org.freedesktop.DBus.Introspectable"); err != nil {
		log.Printf("systray error: failed to export menu node introspection: %s\n", err)
		return
	}

	instance.lock.Lock()
	instance.conn = conn
	instance.props = props
	instance.menuProps = menuProps
	instance.lock.Unlock()

	obj := conn.Object("org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher")
	call := obj.Call("org.kde.StatusNotifierWatcher.RegisterStatusNotifierItem", 0, path)
	if call.Err != nil {
		log.Printf("systray error: failed to register our icon with the notifier watcher (maybe no tray is running?): %s\n", call.Err)
	}
}

// tray holds DBus state
type tray struct {
	conn           *dbus.Conn
	name           string
	iconData       []byte
	title          string
	tooltipTitle   string
	lock           sync.Mutex
	menu           *menuLayout
	menuLock       sync.RWMutex
	props          *prop.Properties
	menuProps      *prop.Properties
	menuVersion    uint32
}

func (*tray) iface() string { return notifier.InterfaceStatusNotifierItem }

func (t *tray) createPropSpec() map[string]map[string]*prop.Prop {
	t.lock.Lock()
	icon := t.iconData
	title := t.title
	tooltipTitle := t.tooltipTitle
	t.lock.Unlock()

	return map[string]map[string]*prop.Prop{
		"org.kde.StatusNotifierItem": {
			"Status":         {Value: "Active", Writable: false, Emit: prop.EmitTrue},
			"Title":          {Value: title, Writable: true, Emit: prop.EmitTrue},
			"Id":             {Value: "1", Writable: false, Emit: prop.EmitTrue},
			"Category":       {Value: "ApplicationStatus", Writable: false, Emit: prop.EmitTrue},
			"IconName":       {Value: "", Writable: false, Emit: prop.EmitTrue},
			"IconPixmap":     {Value: buildPixmaps(icon), Writable: true, Emit: prop.EmitTrue},
			"IconThemePath":  {Value: "", Writable: false, Emit: prop.EmitTrue},
			"ItemIsMenu":     {Value: true, Writable: false, Emit: prop.EmitTrue},
   "Menu":           {Value: noMenuPathForEnvironment(), Writable: true, Emit: prop.EmitTrue},
			"ToolTip":        {Value: tooltip{V2: tooltipTitle, V1: buildPixmaps(icon)}, Writable: true, Emit: prop.EmitTrue},
		},
	}
}

// PX is a pixmap with width, height and ARGB32 big-endian data
type PX struct {
	W, H int
	Pix  []byte
}

// tooltip maps the SNI tooltip struct (name, icons, title, description)
type tooltip = struct {
	V0 string // name
	V1 []PX   // icons
	V2 string // title
	V3 string // description
}

// buildPixmaps decodes the source icon and produces multiple high-quality
// resized pixmaps encoded as ARGB32 big-endian.
func buildPixmaps(data []byte) []PX {
	if len(data) == 0 {
		return nil
	}
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		log.Printf("systray: failed to decode icon: %v", err)
		return nil
	}

	// Common tray sizes across environments (match C++ behavior of providing multiple sizes)
	targets := []int{16, 22, 24, 32, 48, 64, 128}

	out := make([]PX, 0, len(targets))
	for _, s := range targets {
		pm := resizeTo(img, s, s)
		out = append(out, PX{W: s, H: s, Pix: toARGB32BigEndian(pm)})
	}
	return out
}

// resizeTo resizes with CatmullRom for crisp yet smooth icons.
func resizeTo(src image.Image, w, h int) *image.RGBA {
	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, src.Bounds(), draw.Over, nil)
	return dst
}

// toARGB32BigEndian converts *image.RGBA to a byte slice of ARGB32 in big-endian
// order (A, R, G, B per byte) as required by SNI over D-Bus.
// This mirrors the C++ implementation that enforces big-endian layout. :contentReference[oaicite:4]{index=4}
func toARGB32BigEndian(img *image.RGBA) []byte {
	w, h := img.Bounds().Dx(), img.Bounds().Dy()
	buf := make([]byte, w*h*4)

	i := 0
	for y := 0; y < h; y++ {
		off := img.PixOffset(0, y)
		row := img.Pix[off : off+w*4]
		for x := 0; x < w; x++ {
			r := row[x*4+0]
			g := row[x*4+1]
			b := row[x*4+2]
			a := row[x*4+3]

			// 0xAARRGGBB big-endian -> bytes [AA][RR][GG][BB]
			buf[i+0] = a
			buf[i+1] = r
			buf[i+2] = g
			buf[i+3] = b
			i += 4
		}
	}
	return buf
}


// noMenuPathForEnvironment returns the DBus object path to advertise when there is NO menu.
// - KDE/Plasma: "/NO_DBUSMENU"
// - GNOME/Others: "/"
// Simple detection via environment variables.
func noMenuPathForEnvironment() dbus.ObjectPath {
	xdg := strings.ToLower(os.Getenv("XDG_CURRENT_DESKTOP"))
	sess := strings.ToLower(os.Getenv("DESKTOP_SESSION"))
	kdeFull := os.Getenv("KDE_FULL_SESSION") != ""
	if strings.Contains(xdg, "kde") || strings.Contains(xdg, "plasma") ||
		strings.Contains(sess, "kde") || strings.Contains(sess, "plasma") || kdeFull {
		return dbus.ObjectPath("/NO_DBUSMENU")
	}
	return dbus.ObjectPath("/")
}

// setMenuPropTo updates the StatusNotifierItem "Menu" property to the given path.
func setMenuPropTo(p dbus.ObjectPath) {
	instance.lock.Lock()
	props := instance.props
	instance.lock.Unlock()
	if props == nil {
		return
	}
	if dbusErr := props.Set("org.kde.StatusNotifierItem", "Menu", dbus.MakeVariant(p)); dbusErr != nil {
		log.Printf("systray error: failed to set Menu prop: %s\n", dbusErr)
	}
}
