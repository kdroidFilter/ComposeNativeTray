// ---------------------------------------------------------------------------
//  sni_wrapper.cpp – C++ back-end + couche C pour JNA **et** applications C
// ---------------------------------------------------------------------------

#include "sni_wrapper.h"            // Définitions des callbacks + EXPORT
#include "statusnotifieritem.h"

#include <QApplication>
#include <QMenu>
#include <QAction>
#include <QPointer>
#include <QIcon> // Ajout pour gérer les icônes
#include <mutex>
#include <QDebug> // Ajout pour le débogage
#include <QProcessEnvironment> // Pour set env
#include <cstdio> // Pour printf

// ---------------------------------------------------------------------------
//  Helpers internes
// ---------------------------------------------------------------------------
namespace {

int            g_argc   = 0;
char**         g_argv   = nullptr;       // Qt exige un argv non nul
QApplication*  g_app    = nullptr;       // Unique QApplication de la lib
std::mutex     g_mutex;                  // Appels possibles depuis >1 thread

inline void ensureQtApp()
{
    if (!g_app) {
        // Forcer Qt à utiliser xcb (X11) pour éviter issues Wayland
        qputenv("QT_QPA_PLATFORM", "xcb");
        g_app = new QApplication(g_argc, g_argv);
        qDebug() << "Qt application created with platform:" << g_app->platformName();
    }
}

inline StatusNotifierItem* fromHandle(void* h)
{
    return reinterpret_cast<StatusNotifierItem*>(h);
}

} // namespace

// ---------------------------------------------------------------------------
//  API « sni_ » (interne / Java / JNA)
// ---------------------------------------------------------------------------
extern "C" {

// Opaque pour le monde C / Java
using SNIHandle = void*;

// -- cycle de vie -----------------------------------------------------------
SNIHandle sni_create(const char* id)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    ensureQtApp();
    try {
        return reinterpret_cast<SNIHandle>(new StatusNotifierItem(QString::fromUtf8(id)));
    } catch (...) {
        return nullptr;
    }
}

void sni_destroy(SNIHandle h)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    delete fromHandle(h);
}

// -- propriétés -------------------------------------------------------------
void sni_set_title(SNIHandle h, const char* t) {
    std::lock_guard<std::mutex> L(g_mutex);
    if (auto* s = fromHandle(h)) s->setTitle(QString::fromUtf8(t));
}
void sni_set_status(SNIHandle h, const char* s) {
    std::lock_guard<std::mutex> L(g_mutex);
    if (auto* n = fromHandle(h)) n->setStatus(QString::fromUtf8(s));
}
void sni_set_icon_name(SNIHandle h, const char* i) {
    std::lock_guard<std::mutex> L(g_mutex);
    if (auto* n = fromHandle(h)) n->setIconByName(QString::fromUtf8(i));
}
void sni_set_icon_path(SNIHandle h, const char* path)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h)) {
        printf("Entering set_icon_by_path for path %s\n", path);
        qDebug() << "Loading initial icon from path:" << path;
        QIcon icon(QString::fromUtf8(path));
        qDebug() << "Initial icon cache key before pixmap:" << icon.cacheKey() << "isNull:" << icon.isNull();
        if (icon.isNull()) {
            printf("Icon is null for path %s\n", path);
            qWarning() << "Failed to load initial icon from" << path;
            return;
        }
        QPixmap dummy = icon.pixmap(QSize(24, 24));  // Force le rendu pour générer une clé de cache valide
        qDebug() << "Initial icon cache key after pixmap:" << icon.cacheKey();
        printf("Setting initial icon\n");
        n->setIconByPixmap(icon);
    }
}
void sni_update_icon_path(SNIHandle h, const char* path)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h)) {
        printf("Entering update_icon_by_path for path %s\n", path);
        qDebug() << "Updating icon to path:" << path;
        QPixmap pm(QString::fromUtf8(path));
        if (pm.isNull()) {
            printf("Pixmap is null for path %s\n", path);
            qWarning() << "Failed to load pixmap from" << path;
            return;
        }
        printf("Pixmap loaded, size %d x %d\n", pm.width(), pm.height());
        QIcon icon(pm);
        printf("New icon cache key before pixmap: %lld\n", (long long)icon.cacheKey());
        qDebug() << "New icon cache key before pixmap:" << icon.cacheKey() << "isNull:" << icon.isNull();
        QPixmap dummy = icon.pixmap(QSize(24, 24));  // Force le rendu pour générer une clé de cache valide
        printf("New icon cache key after pixmap: %lld\n", (long long)icon.cacheKey());
        qDebug() << "New icon cache key after pixmap:" << icon.cacheKey();
        printf("Setting updated icon\n");
        n->setIconByPixmap(icon);
    }
}

void sni_set_overlay_icon_name(SNIHandle h, const char* i) {
    std::lock_guard<std::mutex> L(g_mutex);
    if (auto* n = fromHandle(h)) n->setOverlayIconByName(QString::fromUtf8(i));
}
void sni_set_attention_icon_name(SNIHandle h, const char* i) {
    std::lock_guard<std::mutex> L(g_mutex);
    if (auto* n = fromHandle(h)) n->setAttentionIconByName(QString::fromUtf8(i));
}

// tooltip complet (précédente API Java)
void sni_set_tooltip(SNIHandle h, const char* title, const char* subtitle)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h)) {
        n->setToolTipTitle(QString::fromUtf8(title));
        n->setToolTipSubTitle(QString::fromUtf8(subtitle));
    }
}

// notification
void sni_show_message(SNIHandle h, const char* ttl, const char* msg, const char* icon, int secs)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h))
        n->showMessage(QString::fromUtf8(ttl), QString::fromUtf8(msg),
                       QString::fromUtf8(icon), secs);
}

// -- boucle Qt --------------------------------------------------------------
int sni_exec()
{
    {
        std::lock_guard<std::mutex> l(g_mutex);
        ensureQtApp();
    }
    return g_app->exec();
}
void sni_process_events()
{
    {
        std::lock_guard<std::mutex> l(g_mutex);
        ensureQtApp();
    }
    g_app->processEvents();
}

} // extern "C"

// ---------------------------------------------------------------------------
//  Couche d’aliases C « promise » par sni_wrapper.h
// ---------------------------------------------------------------------------
extern "C" {

//   -- cycle de vie ---------------------------------------------------------
EXPORT int init_tray_system()            { /* bootstrap paresseux → rien */ return 0; }

EXPORT void shutdown_tray_system()
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_app) {
        g_app->quit();
        delete g_app;
        g_app = nullptr;
    }
}

EXPORT void* create_tray(const char* id) { return sni_create(id); }
EXPORT void  destroy_handle(void* h)     { sni_destroy(h); }

//   -- setters simples -------------------------------------------------------
EXPORT void set_title(void* h, const char* t)           { sni_set_title(h, t); }
EXPORT void set_status(void* h, const char* s)          { sni_set_status(h, s); }
EXPORT void set_icon_by_name(void* h, const char* n)    { sni_set_icon_name(h, n); }
EXPORT void set_icon_by_path(void* h, const char* path) { sni_set_icon_path(h, path); }
EXPORT void update_icon_by_path(void* h, const char* path) { sni_update_icon_path(h, path); }

// Tooltip fractionné
EXPORT void set_tooltip_title   (void* h, const char* t) { std::lock_guard<std::mutex> L(g_mutex); if (auto* n = fromHandle(h)) n->setToolTipTitle(QString::fromUtf8(t)); }
EXPORT void set_tooltip_subtitle(void* h, const char* t) { std::lock_guard<std::mutex> L(g_mutex); if (auto* n = fromHandle(h)) n->setToolTipSubTitle(QString::fromUtf8(t)); }

//   -- menus -----------------------------------------------------------------
EXPORT void* create_menu()
{
    std::lock_guard<std::mutex> lock(g_mutex);
    ensureQtApp();
    return new QMenu();
}

EXPORT void* add_menu_action(void* m, const char* txt, ActionCallback cb, void* data)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(m)) {
        QAction* act = menu->addAction(QString::fromUtf8(txt));
        QObject::connect(act, &QAction::triggered, [cb, data] { if (cb) cb(data); });
        return act; // Retourne le handle de l'action
    }
    return nullptr;
}

EXPORT void add_checkable_menu_action(void* m, const char* txt, int checked, ActionCallback cb, void* data)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(m)) {
        QAction* act = menu->addAction(QString::fromUtf8(txt));
        act->setCheckable(true);
        act->setChecked(checked != 0); // Convertir l'entier C en booléen Qt
        QObject::connect(act, &QAction::toggled, [cb, data](bool checked) {
            if (cb) cb(data); // Appeler le callback lors du changement d'état
        });
    }
}

EXPORT void* create_submenu(void* m, const char* txt)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(m)) {
        QMenu* submenu = menu->addMenu(QString::fromUtf8(txt));
        return submenu;
    }
    return nullptr;
}

EXPORT void add_menu_separator(void* m)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(m))
        menu->addSeparator();
}

EXPORT void set_context_menu(void* h, void* menu)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h))
        n->setContextMenu(static_cast<QMenu*>(menu));
}

EXPORT void set_menu_item_text(void* menu_item_handle, const char* text)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* action = static_cast<QAction*>(menu_item_handle)) {
        action->setText(QString::fromUtf8(text));
        printf("Menu item text changed to: %s\n", text);
    } else {
        printf("Failed to change menu item text: invalid handle\n");
    }
}

EXPORT void remove_menu_item(void* menu_handle, void* menu_item_handle)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(menu_handle)) {
        if (auto* action = static_cast<QAction*>(menu_item_handle)) {
            menu->removeAction(action);
            delete action; // Libère la mémoire de l'action
            printf("Menu item removed\n");
        } else {
            printf("Failed to remove menu item: invalid item handle\n");
        }
    } else {
        printf("Failed to remove menu item: invalid menu handle\n");
    }
}

EXPORT void* add_disabled_menu_action(void* m, const char* txt, ActionCallback cb, void* data)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* menu = static_cast<QMenu*>(m)) {
        QAction* act = menu->addAction(QString::fromUtf8(txt));
        act->setEnabled(false);
        QObject::connect(act, &QAction::triggered, [cb, data] { if (cb) cb(data); });
        return act; // Retourne le handle de l'action
    }
    return nullptr;
}

EXPORT void set_menu_item_enabled(void* menu_item_handle, int enabled)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* action = static_cast<QAction*>(menu_item_handle)) {
        action->setEnabled(enabled != 0);
        printf("Menu item enabled: %d\n", enabled);
    } else {
        printf("Failed to set menu item enabled: invalid handle\n");
    }
}

//   -- callbacks SNI → C ----------------------------------------------------
EXPORT void set_activate_callback(void* h, ActivateCallback cb, void* d)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h))
        QObject::connect(n, &StatusNotifierItem::activateRequested,
                         [cb, d](const QPoint& p) { if (cb) cb(p.x(), p.y(), d); });
}

EXPORT void set_secondary_activate_callback(void* h, SecondaryActivateCallback cb, void* d)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h))
        QObject::connect(n, &StatusNotifierItem::secondaryActivateRequested,
                         [cb, d](const QPoint& p) { if (cb) cb(p.x(), p.y(), d); });
}

EXPORT void set_scroll_callback(void* h, ScrollCallback cb, void* d)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (auto* n = fromHandle(h))
        QObject::connect(n, &StatusNotifierItem::scrollRequested,
                         [cb, d](int delta, Qt::Orientation o) { if (cb) cb(delta, o == Qt::Horizontal ? 1 : 0, d); });
}

//   -- notification ----------------------------------------------------------
EXPORT void show_notification(void* h, const char* ttl, const char* msg,
                              const char* icon, int secs)
{
    sni_show_message(h, ttl, msg, icon, secs);
}

} // extern "C"

// ---------------------------------------------------------------------------
//  Fin du fichier
// ---------------------------------------------------------------------------