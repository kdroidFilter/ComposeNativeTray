// File: linuxlibdbus/src/sni_wrapper.cpp
#include "sni_wrapper.h"
#include "statusnotifieritem.h"
#include "dbustypes.h"
#include <QApplication>
#include <QDebug>
#include <QThread>
#include <QEventLoop>
#include <QTimer>
#include <QDBusConnection>
#include <QMenu>
#include <QAction>
#include <QIcon>
#include <QMetaObject>
#include <QObject>
#include <QSystemTrayIcon> // For notification, if needed
#include <QPoint>
#include <QMutex>
#include <QWaitCondition>

static bool debug = false;
static int trayCount = 0;

// Custom message handler to filter warnings
void customMessageHandler(QtMsgType type, const QMessageLogContext &context, const QString &msg) {
    if (msg.contains("QObject::killTimer: Timers cannot be stopped from another thread") ||
        msg.contains("QObject::~QObject: Timers cannot be stopped from another thread") ||
        msg.contains("g_main_context_pop_thread_default") ||
        msg.contains("QtDBus: cannot relay signals") ||
        msg.contains("QApplication was not created in the main() thread") ||
        msg.contains("QWidget: Cannot create a QWidget without QApplication")) {
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
        // Do not abort for filtered fatals
        if (!msg.contains("QWidget: Cannot create a QWidget without QApplication")) {
            abort();
        }
        break;
    }
}

class SNIWrapperManager : public QObject {
    Q_OBJECT
public:
    static SNIWrapperManager* instance() {
        static SNIWrapperManager* mgr = nullptr;
        if (!mgr) {
            mgr = new SNIWrapperManager();
        }
        return mgr;
    }

    QThread* qtThread;
    QApplication* app;
    bool createdApp;
    QMutex initMutex;
    QWaitCondition initCond;
    bool initialized;

    ~SNIWrapperManager() {
        QMetaObject::invokeMethod(this, [this]() {
            if (app) {
                app->quit();
                app->processEvents(QEventLoop::AllEvents, 2000);
                if (createdApp) delete app;
                app = nullptr;
            }
        }, Qt::BlockingQueuedConnection);
        qtThread->quit();
        qtThread->wait(5000); // Wait up to 5 seconds
        delete qtThread;
    }

private:
    SNIWrapperManager() : QObject(), qtThread(new QThread()), app(nullptr), createdApp(false), initialized(false) {
        moveToThread(qtThread);
        connect(qtThread, &QThread::started, this, &SNIWrapperManager::initialize);
        initMutex.lock();
        qtThread->start();
        initCond.wait(&initMutex);
        initMutex.unlock();
    }

signals:
    void exited();

private slots:
    void initialize() {
        int argc = 1;
        const char* argv_const[] = {"sni_app", nullptr};
        char** argv = const_cast<char**>(argv_const);
        app = new QApplication(argc, argv);
        createdApp = true;
        qInstallMessageHandler(customMessageHandler);
        setenv("G_MESSAGES_DEBUG", "", 1);
        setenv("G_DEBUG", "", 1);
        // Ensure DBus session bus is initialized in this thread
        QDBusConnection::sessionBus();
        initialized = true;
        initCond.wakeAll();
    }

public:
    void startEventLoop() {
        QMetaObject::invokeMethod(this, [this]() {
            app->exec();
            emit exited();
        }, Qt::QueuedConnection);
    }

    StatusNotifierItem* createSNI(const char* id) {
        StatusNotifierItem* sni = nullptr;
        QMetaObject::invokeMethod(this, [this, &sni, id]() {
            sni = new StatusNotifierItem(QString(id), this);
        }, Qt::BlockingQueuedConnection);
        return sni;
    }

    void destroySNI(StatusNotifierItem* sni) {
        QMetaObject::invokeMethod(this, [sni]() {
            QDBusConnection::sessionBus().unregisterObject("/StatusNotifierItem");
            sni->deleteLater();
        }, Qt::BlockingQueuedConnection);
    }

    void processEvents() {
        QMetaObject::invokeMethod(this, [this]() {
            app->processEvents(QEventLoop::AllEvents | QEventLoop::WaitForMoreEvents, 100);
        }, Qt::BlockingQueuedConnection);
    }
};

int init_tray_system(void) {
    SNIWrapperManager::instance();
    return 0;
}

void shutdown_tray_system(void) {
    delete SNIWrapperManager::instance();
}

void* create_tray(const char* id) {
    trayCount++;
    return SNIWrapperManager::instance()->createSNI(id);
}

void destroy_handle(void* handle) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    SNIWrapperManager::instance()->destroySNI(sni);
    trayCount--;
    if (trayCount <= 0) {
        shutdown_tray_system();
    }
}

void set_title(void* handle, const char* title) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title]() {
        sni->setTitle(QString(title));
    }, Qt::QueuedConnection);
}

void set_status(void* handle, const char* status) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, status]() {
        sni->setStatus(QString(status));
    }, Qt::QueuedConnection);
}

void set_icon_by_name(void* handle, const char* name) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, name]() {
        sni->setIconByName(QString(name));
    }, Qt::QueuedConnection);
}

void set_icon_by_path(void* handle, const char* path) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, path]() {
        sni->setIconByPixmap(QIcon(QString(path)));
    }, Qt::QueuedConnection);
}

void update_icon_by_path(void* handle, const char* path) {
    set_icon_by_path(handle, path);
}

void set_tooltip_title(void* handle, const char* title) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title]() {
        sni->setToolTipTitle(QString(title));
    }, Qt::QueuedConnection);
}

void set_tooltip_subtitle(void* handle, const char* subTitle) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, subTitle]() {
        sni->setToolTipSubTitle(QString(subTitle));
    }, Qt::QueuedConnection);
}

void* create_menu(void) {
    void* menu = nullptr;
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [&menu]() {
        menu = new QMenu();
    }, Qt::BlockingQueuedConnection);
    return menu;
}

void set_context_menu(void* handle, void* menu_handle) {
    if (!handle || !menu_handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QMetaObject::invokeMethod(sni, [sni, menu]() {
        sni->setContextMenu(menu);
    }, Qt::QueuedConnection);
}

void* add_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data) {
    if (!menu_handle) return nullptr;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* action = nullptr;
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [&action, menu, text, cb, data]() {
        action = menu->addAction(QString(text));
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
    }, Qt::BlockingQueuedConnection);
    return action;
}

void* add_disabled_menu_action(void* menu_handle, const char* text, ActionCallback cb, void* data) {
    if (!menu_handle) return nullptr;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* action = nullptr;
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [&action, menu, text, cb, data]() {
        action = menu->addAction(QString(text));
        action->setEnabled(false);
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
    }, Qt::BlockingQueuedConnection);
    return action;
}

void add_checkable_menu_action(void* menu_handle, const char* text, int checked, ActionCallback cb, void* data) {
    if (!menu_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [menu, text, checked, cb, data]() {
        QAction* action = menu->addAction(QString(text));
        action->setCheckable(true);
        action->setChecked(checked != 0);
        QObject::connect(action, &QAction::triggered, [cb, data]() { cb(data); });
    }, Qt::QueuedConnection);
}

void add_menu_separator(void* menu_handle) {
    if (!menu_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [menu]() {
        menu->addSeparator();
    }, Qt::QueuedConnection);
}

void* create_submenu(void* menu_handle, const char* text) {
    if (!menu_handle) return nullptr;
    QMenu* parentMenu = static_cast<QMenu*>(menu_handle);
    QMenu* subMenu = nullptr;
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [&subMenu, parentMenu, text]() {
        QAction* action = parentMenu->addAction(QString(text));
        subMenu = new QMenu();
        action->setMenu(subMenu);
    }, Qt::BlockingQueuedConnection);
    return subMenu;
}

void set_menu_item_text(void* menu_item_handle, const char* text) {
    if (!menu_item_handle) return;
    QAction* action = static_cast<QAction*>(menu_item_handle);
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [action, text]() {
        action->setText(QString(text));
    }, Qt::QueuedConnection);
}

void set_menu_item_enabled(void* menu_item_handle, int enabled) {
    if (!menu_item_handle) return;
    QAction* action = static_cast<QAction*>(menu_item_handle);
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [action, enabled]() {
        action->setEnabled(enabled != 0);
    }, Qt::QueuedConnection);
}

void remove_menu_item(void* menu_handle, void* menu_item_handle) {
    if (!menu_handle || !menu_item_handle) return;
    QMenu* menu = static_cast<QMenu*>(menu_handle);
    QAction* action = static_cast<QAction*>(menu_item_handle);
    QMetaObject::invokeMethod(SNIWrapperManager::instance(), [menu, action]() {
        menu->removeAction(action);
        action->deleteLater();
    }, Qt::QueuedConnection);
}

void set_activate_callback(void* handle, ActivateCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::activateRequested, sni, [cb, data](const QPoint& pos) {
            cb(pos.x(), pos.y(), data);
        }, Qt::DirectConnection);
    }, Qt::QueuedConnection);
}

void set_secondary_activate_callback(void* handle, SecondaryActivateCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::secondaryActivateRequested, sni, [cb, data](const QPoint& pos) {
            cb(pos.x(), pos.y(), data);
        }, Qt::DirectConnection);
    }, Qt::QueuedConnection);
}

void set_scroll_callback(void* handle, ScrollCallback cb, void* data) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, cb, data]() {
        QObject::connect(sni, &StatusNotifierItem::scrollRequested, sni, [cb, data](int delta, Qt::Orientation orientation) {
            cb(delta, orientation == Qt::Horizontal ? 1 : 0, data);
        }, Qt::DirectConnection);
    }, Qt::QueuedConnection);
}

void show_notification(void* handle, const char* title, const char* msg, const char* iconName, int secs) {
    if (!handle) return;
    StatusNotifierItem* sni = static_cast<StatusNotifierItem*>(handle);
    QMetaObject::invokeMethod(sni, [sni, title, msg, iconName, secs]() {
        sni->showMessage(QString(title), QString(msg), QString(iconName), secs * 1000);
    }, Qt::QueuedConnection);
}

int sni_exec(void) {
    SNIWrapperManager* mgr = SNIWrapperManager::instance();
    QEventLoop loop;
    QObject::connect(mgr, &SNIWrapperManager::exited, &loop, &QEventLoop::quit);
    mgr->startEventLoop();
    return loop.exec();
}

void sni_process_events(void) {
    SNIWrapperManager::instance()->processEvents();
}

#include "sni_wrapper.moc"