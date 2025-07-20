#include "QtTrayMenu.h"

#include <QIcon>
#include <QThread>  // Added for async callback execution

//----------------------------------------
// Construction / Destruction
//----------------------------------------
QtTrayMenu::QtTrayMenu()
    : trayIcon(nullptr)
    , trayStruct(nullptr)
    , continueRunning(true)
    , app(nullptr)
{
    // Ensure a QApplication exists
    if (QApplication::instance()) {
        app = qobject_cast<QApplication *>(QApplication::instance());
    } else {
        static int   static_argc  = 1;
        static char  app_name[]   = "TrayMenuApp";
        static char* static_argv[] = { app_name, nullptr };

        app = new QApplication(static_argc, static_argv);
        app->setQuitOnLastWindowClosed(false);   // Tray apps stay alive
    }

    // Move this QObject to the Qt GUI thread
    if (app) this->moveToThread(app->thread());

    // Non‑blocking connections
    connect(this, &QtTrayMenu::cleanupRequested,
            this, &QtTrayMenu::onCleanupRequested,
            Qt::QueuedConnection);

    connect(this, &QtTrayMenu::exitRequested,
            this, &QtTrayMenu::onExitRequested,
            Qt::QueuedConnection);
}

QtTrayMenu::~QtTrayMenu()
{
    // Non‑blocking cleanup (process may déjà quitter)
    if (trayIcon) {
        if (QThread::currentThread() == this->thread()) {
            onCleanupRequested();
        } else {
            QMetaObject::invokeMethod(
                this, &QtTrayMenu::onCleanupRequested, Qt::QueuedConnection);
        }
    }
}

//----------------------------------------
// Public API
//----------------------------------------
int QtTrayMenu::init(struct tray *tray)
{
    if (!app) {
        return -1;
    }

    // Always execute on the Qt thread
    if (QThread::currentThread() != app->thread()) {
        int result = -1;
        runInQtThreadBlocking([&]() { result = this->init(tray); });
        return result;
    }

    if (trayIcon) {
        return -1;
    }

    trayStruct = tray;

    if (app->applicationName().isEmpty() || app->applicationName() == "TrayMenuApp")
        app->setApplicationName(tray->tooltip ? tray->tooltip : "System Tray");

    // ----- Create the tray icon -----
    trayIcon = new QSystemTrayIcon(this);

    if (tray->icon_filepath) {
        QIcon icon(tray->icon_filepath);
        if (!icon.isNull())
            trayIcon->setIcon(icon);
    }

    if (tray->tooltip) trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));

    connect(trayIcon, &QSystemTrayIcon::activated,
            this, &QtTrayMenu::onTrayActivated);

    // ----- Context menu -----
    if (tray->menu) {
        auto *menu = new QMenu();
        createMenu(tray->menu, menu);
        trayIcon->setContextMenu(menu);
    }

    trayIcon->show();
    app->processEvents();                         // Ensure visibility and process events

    return 0;
}

void QtTrayMenu::update(struct tray *tray)
{
    if (!trayIcon || !app) return;

    QMetaObject::invokeMethod(
        this,
        [this, tray]() {
            trayStruct = tray;

            if (tray->icon_filepath) {
                QIcon newIcon(tray->icon_filepath);
                if (!newIcon.isNull()) trayIcon->setIcon(newIcon);
            }

            if (tray->tooltip) trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));

            if (tray->menu) {
                if (auto *existingMenu = trayIcon->contextMenu()) {
                    existingMenu->clear();
                    createMenu(tray->menu, existingMenu);
                }
            }
            app->processEvents();  // Force event processing after update
        },
        Qt::QueuedConnection);
}

int QtTrayMenu::loop(int blocking)
{
    if (!app) {
        return -1;
    }
    if (!continueRunning) return -1;

    try {
        if (blocking) {
            return app->exec();                   // Never returns until quit()
        } else {
            app->processEvents(QEventLoop::AllEvents);  // No timeout for full processing in non-blocking mode
            return continueRunning ? 0 : -1;
        }
    } catch (...) {
        return -1;
    }
}

void QtTrayMenu::exit()
{
    continueRunning = false;

    // Use blocking queued connection to ensure cleanup and exit are executed synchronously
    QMetaObject::invokeMethod(this, &QtTrayMenu::onCleanupRequested, Qt::BlockingQueuedConnection);
    QMetaObject::invokeMethod(this, &QtTrayMenu::onExitRequested, Qt::BlockingQueuedConnection);
}

//----------------------------------------
// Internal helpers
//----------------------------------------
void QtTrayMenu::createMenu(struct tray_menu_item *items, QMenu *menu)
{
    if (!items || !menu) return;

    while (items && items->text) {
        // Separator
        if (QString::fromUtf8(items->text) == "-") {
            menu->addSeparator();
            ++items;
            continue;
        }

        auto *action = new QAction(QString::fromUtf8(items->text), menu);
        action->setDisabled(items->disabled == 1);

        const bool isCheckable = (items->checked == 0 || items->checked == 1);
        action->setCheckable(isCheckable);
        if (isCheckable) action->setChecked(items->checked == 1);

        action->setProperty("tray_menu_item",
                            QVariant::fromValue(static_cast<void *>(items)));

        connect(action, &QAction::triggered,
                this, &QtTrayMenu::onMenuItemTriggered);

        // Sub‑menu
        if (items->submenu) {
            auto *submenu = new QMenu(menu);
            createMenu(items->submenu, submenu);
            action->setMenu(submenu);
        }

        menu->addAction(action);
        ++items;
    }
}

void QtTrayMenu::onTrayActivated(QSystemTrayIcon::ActivationReason reason)
{
    if (reason == QSystemTrayIcon::Trigger && trayStruct && trayStruct->cb) {
        // Run callback in a separate thread to avoid blocking Qt main thread
        QThread *callbackThread = QThread::create([this]() {
            if (trayStruct && trayStruct->cb) {
                trayStruct->cb(trayStruct);
            }
        });
        callbackThread->start();
        // No need to wait; thread will auto-delete when done
        app->processEvents();  // Still process events immediately
    }
}

void QtTrayMenu::onMenuItemTriggered()
{
    auto *action = qobject_cast<QAction *>(sender());
    if (!action) return;

    auto *item = getTrayMenuItem(action);
    if (!item || !item->cb) return;

    if (action->isCheckable()) item->checked = action->isChecked() ? 1 : 0;

    // Run callback in a separate thread to avoid blocking
    QThread *callbackThread = QThread::create([item]() {
        item->cb(item);
    });
    callbackThread->start();
    // No need to wait; thread will auto-delete when done
    app->processEvents();  // Force event processing after trigger
}

struct tray_menu_item *QtTrayMenu::getTrayMenuItem(QAction *action)
{
    if (!action) return nullptr;
    return static_cast<tray_menu_item *>(
        action->property("tray_menu_item").value<void *>());
}

void QtTrayMenu::onExitRequested()
{
    continueRunning = false;

    // Request global Qt application shutdown
    if (app && !app->closingDown())
        app->quit();            // posts event which stops exec()

    // Extra insurance: force exit code 0
    QCoreApplication::exit(0);
}

void QtTrayMenu::onCleanupRequested()
{
    if (!trayIcon) return;

    trayIcon->hide();

    if (auto *menu = trayIcon->contextMenu()) {
        trayIcon->setContextMenu(nullptr);
        delete menu;
    }

    delete trayIcon;
    trayIcon = nullptr;
}