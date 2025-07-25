// File: sni_wrapper.cpp

#include "sni_wrapper.h"
#include "statusnotifieritem.h"
#include "dbustypes.h"
#include "qtthreadmanager.h"

#include <QApplication>
#include <QDebug>
#include <QEventLoop>
#include <QTimer>
#include <QDBusConnection>
#include <QMenu>
#include <QAction>
#include <QIcon>
#include <QMetaObject>
#include <QObject>
#include <QThread>
#include <QPoint>
#include <QMutex>
#include <unistd.h>  // For sleep in sni_exec
#include <atomic>

static std::atomic<bool> sni_running{true};
static bool debug = true;
static int  trayCount = 0;

// -----------------------------------------------------------------------------
// Custom message handler to filter warnings
// -----------------------------------------------------------------------------
void customMessageHandler(QtMsgType type, const QMessageLogContext &context, const QString &msg) {
    if (msg.contains("QObject::killTimer: Timers cannot be stopped from another thread") ||
        msg.contains("QObject::~QObject: Timers cannot be stopped from another thread") ||
        msg.contains("g_main_context_pop_thread_default") ||
        msg.contains("QtDBus: cannot relay signals") ||
        msg.contains("QApplication was not created in the main() thread") ||
        msg.contains("QWidget: Cannot create a QWidget without QApplication") ||
        msg.contains("QSocketNotifier: Can only be used with threads started with QThread") ||
        msg.contains("QObject::startTimer: Timers can only be used with threads started with QThread") ||
        msg.contains("QMetaObject::invokeMethod: Dead lock detected")) {
        return;
    }
    const QByteArray localMsg = msg.toLocal8Bit();
    switch (type) {
    case QtDebugMsg:
        if (debug) fprintf(stderr, "Debug: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtInfoMsg:
        fprintf(stderr, "Info: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtWarningMsg:
        fprintf(stderr, "Warning: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtCriticalMsg:
        fprintf(stderr, "Critical: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        break;
    case QtFatalMsg:
        fprintf(stderr, "Fatal: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
        if (!msg.contains("QWidget: Cannot create a QWidget without QApplication")) {
            abort();
        }
        break;
    }
}

// -----------------------------------------------------------------------------
// Helper: get safe connection type to avoid deadlocks when already on Qt thread
// -----------------------------------------------------------------------------
static inline Qt::ConnectionType safeConn(QObject* receiver) {
    if (QThread::currentThread() == receiver->thread()) {
        return Qt::DirectConnection;
    } else {
        return Qt::BlockingQueuedConnection;
    }
}

// Implémentation de SNIWrapperManager
SNIWrapperManager* SNIWrapperManager::s_instance = nullptr;

SNIWrapperManager* SNIWrapperManager::instance() {
    static QMutex mutex;

    if (!s_instance) {
        QMutexLocker locker(&mutex);
        if (!s_instance) {
            // Utiliser QtThreadManager pour créer SNIWrapperManager dans le thread Qt
            QtThreadManager::instance()->runBlocking([] {
                s_instance = new SNIWrapperManager();
            });
        }
    }
    return s_instance;
}

void SNIWrapperManager::shutdown() {
    if (s_instance) {
        delete s_instance;
        s_instance = nullptr;
    }
}

SNIWrapperManager::~SNIWrapperManager() {
    // No app quit or delete here; handled by QtThreadManager
}

SNIWrapperManager::SNIWrapperManager() : QObject(), app(qApp) {
    qInstallMessageHandler(customMessageHandler);
    setenv("G_MESSAGES_DEBUG", "", 1);
    setenv("G_DEBUG", "", 1);
    // Ensure DBus session bus is initialized in this thread
    QDBusConnection::sessionBus();
}

void SNIWrapperManager::startEventLoop() {
    // No-op; event loop is managed by QtThreadManager
}

StatusNotifierItem* SNIWrapperManager::createSNI(const char* id) {
    return new StatusNotifierItem(QString(id), this);
}

void SNIWrapperManager::destroySNI(StatusNotifierItem* sni) {
    if (!sni) return;
    sni->unregister();
    sni->deleteLater();
}

void SNIWrapperManager::processEvents() {
    app->processEvents(QEventLoop::AllEvents | QEventLoop::WaitForMoreEvents, 100);
}

// -----------------------------------------------------------------------------
// C API Implementation
// -----------------------------------------------------------------------------

int init_tray_system(void) {
    SNIWrapperManager::instance();  // Ensures creation in Qt thread
    return 0;
}

void shutdown_tray_system(void) {
    SNIWrapperManager::shutdown();
    QtThreadManager::shutdown(); // Va maintenant quitter proprement QApplication
}

void* create_tray(const char* id) {
    trayCount++;
    StatusNotifierItem* result = nullptr;
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [&]() {
        result = mgr->createSNI(id);
    }, safeConn(mgr));
    return result;
}

void destroy_handle(void* handle) {
    if (!handle) return;
    auto mgr = SNIWrapperManager::instance();
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(mgr, [mgr, sni]() {
        mgr->destroySNI(sni);
    }, safeConn(mgr));
    trayCount--;
    if (trayCount <= 0) {
        shutdown_tray_system();
    }
}

// ------------------- Tray property setters -------------------

void set_title(void* handle, const char* title) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title]() {
        sni->setTitle(QString(title));
    }, safeConn(sni));
}

void set_status(void* handle, const char* status) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, status]() {
        sni->setStatus(QString(status));
    }, safeConn(sni));
}

void set_icon_by_name(void* handle, const char* name) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, name]() {
        sni->setIconByName(QString(name));
    }, safeConn(sni));
}

void set_icon_by_path(void* handle, const char* path) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, path]() {
        // Reset cache first to force update
        sni->setIconByName(QString());
        sni->setIconByPixmap(QIcon(QString(path)));
    }, safeConn(sni));
}

void update_icon_by_path(void* handle, const char* path) {
    // Reuse set_icon_by_path to force icon refresh
    set_icon_by_path(handle, path);
}

void set_tooltip_title(void* handle, const char* title) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title]() {
        sni->setToolTipTitle(QString(title));
    }, safeConn(sni));
}

void set_tooltip_subtitle(void* handle, const char* subTitle) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, subTitle]() {
        sni->setToolTipSubTitle(QString(subTitle));
    }, safeConn(sni));
}

// ------------------- Menu creation & management -------------------

void* create_menu(void) {
    QMenu* result = nullptr;
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [&]() {
        result = new QMenu();
    }, safeConn(mgr));
    return result;
}

void destroy_menu(void* menu_handle) {
    if (!menu_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [menu]() {
        menu->deleteLater();
    }, safeConn(mgr));
}

void set_context_menu(void* handle, void* menu_handle) {
    if (!handle || !menu_handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QMetaObject::invokeMethod(sni, [sni, menu]() {
        sni->setContextMenu(menu);
    }, safeConn(sni));
}

void* add_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data) {
    if (!menu_handle) return nullptr;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* result = nullptr;
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [&]() {
        QAction* action = menu->addAction(QString(text));
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
        result = action;
    }, safeConn(mgr));
    return result;
}

void* add_disabled_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data) {
    if (!menu_handle) return nullptr;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* result = nullptr;
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [&]() {
        QAction* action = menu->addAction(QString(text));
        action->setEnabled(false);
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
        result = action;
    }, safeConn(mgr));
    return result;
}

void add_checkable_menu_action(void* menu_handle, const char* text, int checked, ActionCallback cb, void* data) {
    if (!menu_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [=]() {
        QAction* action = menu->addAction(QString(text));
        action->setCheckable(true);
        action->setChecked(checked != 0);
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
    }, safeConn(mgr));
}

void add_menu_separator(void* menu_handle) {
    if (!menu_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [menu]() {
        menu->addSeparator();
    }, safeConn(mgr));
}

void* create_submenu(void* menu_handle, const char* text) {
    if (!menu_handle) return nullptr;
    QMenu* parentMenu = static_cast<QMenu*>(menu_handle);
    QMenu* subMenu = nullptr;
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [&]() {
        QAction* action = parentMenu->addAction(QString(text));
        subMenu = new QMenu();
        action->setMenu(subMenu);
    }, safeConn(mgr));
    return subMenu;
}

void set_menu_item_text(void* menu_item_handle, const char* text) {
    if (!menu_item_handle) return;
    QAction* action = static_cast<QAction*>(menu_item_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [action, text]() {
        action->setText(QString(text));
    }, safeConn(mgr));
}

void set_menu_item_enabled(void* menu_item_handle, int enabled) {
    if (!menu_item_handle) return;
    QAction* action = static_cast<QAction*>(menu_item_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [action, enabled]() {
        action->setEnabled(enabled != 0);
    }, safeConn(mgr));
}

void remove_menu_item(void* menu_handle, void* menu_item_handle) {
    if (!menu_handle || !menu_item_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* action = static_cast<QAction*>(menu_item_handle);
    auto mgr = SNIWrapperManager::instance();
    QMetaObject::invokeMethod(mgr, [menu, action]() {
        menu->removeAction(action);
        action->deleteLater();
    }, safeConn(mgr));
}

// ------------------- Tray event callbacks -------------------

void set_activate_callback(void* handle, ActivateCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::activateRequested, sni,
                         [cb, data](const QPoint& pos) { cb(pos.x(), pos.y(), data); },
                         Qt::DirectConnection);
    }, safeConn(sni));
}

void set_secondary_activate_callback(void* handle, SecondaryActivateCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::secondaryActivateRequested, sni,
                         [cb, data](const QPoint& pos) { cb(pos.x(), pos.y(), data); },
                         Qt::DirectConnection);
    }, safeConn(sni));
}

void set_scroll_callback(void* handle, ScrollCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::scrollRequested, sni,
                         [cb, data](int delta, Qt::Orientation orientation) {
                             cb(delta, orientation == Qt::Horizontal ? 1 : 0, data);
                         },
                         Qt::DirectConnection);
    }, safeConn(sni));
}

// ------------------- Notifications -------------------

void show_notification(void* handle, const char* title, const char* msg, const char* iconName, int secs) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title, msg, iconName, secs]() {
        sni->showMessage(QString(title), QString(msg), QString(iconName), secs * 1000);
    }, safeConn(sni));
}

// ------------------- Event loop management -------------------

int sni_exec(void) {
    while (sni_running.load()) {
        try {
            sni_process_events();
            usleep(100000); // 100ms
        } catch (const std::exception& e) {
            fprintf(stderr, "Exception dans sni_exec: %s\n", e.what());
        } catch (...) {
            fprintf(stderr, "Exception inconnue dans sni_exec\n");
        }
    }
    sni_running.store(true); // Reset for reuse
    return 0;
}

EXPORT void sni_stop_exec(void) {
    sni_running.store(false);
}

void sni_process_events(void) {
    QtThreadManager::instance()->runBlocking([] {
        SNIWrapperManager::instance()->processEvents();
    });
}

// Pas besoin d'inclure le fichier .moc - AUTOMOC s'en chargera
