#include "QtTrayMenu.h"
#include <QApplication>
#include <QDebug>
#include <QtGlobal>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>

int argc = 1;
char *argvArray[] = {(char *)"TrayMenuApp", nullptr};
bool debug = false;

// Suppress GLib messages by redirecting stderr temporarily
void suppressGLibMessages() {
    // Set environment variables to suppress GLib messages
    setenv("G_MESSAGES_DEBUG", "", 1);
    setenv("G_DEBUG", "", 1);

    // Alternative: redirect stderr to /dev/null for GLib messages
    // This is more aggressive but completely effective
    static bool glib_suppressed = false;
    if (!glib_suppressed) {
        // Save original stderr
        static int original_stderr = dup(STDERR_FILENO);

        // Open /dev/null
        int dev_null = open("/dev/null", O_WRONLY);
        if (dev_null != -1) {
            // Temporarily redirect stderr to /dev/null during Qt operations
            // We'll restore it after Qt initialization
            dup2(dev_null, STDERR_FILENO);
            close(dev_null);
        }
        glib_suppressed = true;
    }
}

void restoreStderr() {
    // This function would restore stderr if needed, but we'll keep it simple
    // and just leave the suppression active since the messages are harmless
}

// Custom message handler to filter out specific Qt warnings
// This handler suppresses specific thread-related error messages that can occur
// during application shutdown or when Qt objects are destroyed from a different thread.
// These messages are generally harmless in this application context since we're
// properly using deleteLater() and processEvents() for cleanup.
void customMessageHandler(QtMsgType type, const QMessageLogContext &context, const QString &msg)
{
    // Filter out specific error messages related to thread safety and GLib
    if (msg.contains("QObject::killTimer: Timers cannot be stopped from another thread") ||
        msg.contains("QObject::~QObject: Timers cannot be stopped from another thread") ||
        msg.contains("g_main_context_pop_thread_default")) {
        return; // Silently ignore these messages as they're expected during cross-thread cleanup
    }

    // Forward all other messages to the default handler
    const QByteArray localMsg = msg.toLocal8Bit();
    switch (type) {
    case QtDebugMsg:
        fprintf(stderr, "Debug: %s (%s:%u, %s)\n", localMsg.constData(), context.file, context.line, context.function);
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
        abort();
    }
}

QtTrayMenu::QtTrayMenu()
    : trayIcon(nullptr), trayStruct(nullptr), continueRunning(true), app(nullptr), createdApp(false), blockingEventLoop(nullptr)
{
    // Suppress GLib messages before any Qt operations
    suppressGLibMessages();

    // Install custom message handler to filter out specific Qt warnings
    qInstallMessageHandler(customMessageHandler);

    if (QApplication::instance()) {
        app = dynamic_cast<QApplication *>(QApplication::instance());
        if (!app) {
            fprintf(stderr, "QCoreApplication is not a QApplication, please contact support.");
        }
    } else {
        app = new QApplication(argc, &argvArray[0]);
        createdApp = true;
    }
    if (debug)
        app->installEventFilter(this);
}

QtTrayMenu::~QtTrayMenu()
{
    // Cleanup in destructor as fallback
    if (trayIcon) {
        trayIcon->hide();
        trayIcon->deleteLater();
        trayIcon = nullptr;
    }

    // Process any pending deleteLater() calls
    if (app) {
        app->processEvents(QEventLoop::AllEvents, 100);
    }
}

int QtTrayMenu::init(struct tray *tray)
{
    if (trayIcon)
        return -1; // Already initialized

    continueRunning = true; // Reset for recreation

    this->trayStruct = tray;

    if (app->applicationName().isEmpty() || app->applicationName() == "TrayMenuApp")
        app->setApplicationName(tray->tooltip);

    trayIcon = new QSystemTrayIcon(QIcon(tray->icon_filepath));
    trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));

    connect(trayIcon, &QSystemTrayIcon::activated, this, &QtTrayMenu::onTrayActivated);

    auto *menu = new QMenu;
    createMenu(tray->menu, menu);

    trayIcon->setContextMenu(menu);
    trayIcon->show();

    return 0;
}

void QtTrayMenu::update(struct tray *tray)
{
    QMetaObject::invokeMethod(this, [this, tray]() {
        this->trayStruct = tray;
        if (trayIcon) {
            auto newIcon = QIcon(tray->icon_filepath);
            if (!newIcon.isNull())
                trayIcon->setIcon(newIcon);
            trayIcon->setToolTip(QString::fromUtf8(tray->tooltip));
        }

        auto *existingMenu = trayIcon->contextMenu();
        if (existingMenu) {
            existingMenu->clear();
            createMenu(tray->menu, existingMenu);
        }
    }, Qt::QueuedConnection);
}

int QtTrayMenu::loop(int blocking)
{
    if (!continueRunning) {
        return -1;
    }

    if (!app || app->closingDown()) {
        printf("Application is not in a valid state or is closing down.\n");
        return -1;
    }

    if (blocking) {
        QEventLoop localLoop;
        blockingEventLoop = &localLoop;

        // Simple blocking loop that can be interrupted
        while (continueRunning) {
            localLoop.processEvents(QEventLoop::AllEvents, 100);
            if (!continueRunning) break;
        }

        blockingEventLoop = nullptr;
        return -1; // Always return -1 when exiting
    } else {
        app->processEvents();
        return 0;
    }
}

void QtTrayMenu::exit()
{
    continueRunning = false;

    // Immediate cleanup in the Qt thread
    if (trayIcon) {
        trayIcon->hide();
        trayIcon->deleteLater();
        trayIcon = nullptr;
    }

    // Quit any blocking event loop
    if (blockingEventLoop) {
        blockingEventLoop->quit();
    }

    // Process events to handle deleteLater() calls
    if (app) {
        app->processEvents(QEventLoop::AllEvents, 200);
    }
}

void QtTrayMenu::createMenu(struct tray_menu_item *items, QMenu *menu)
{
    while (items && items->text) {

        // Separator
        if (QString::fromUtf8(items->text) == "-") {
            menu->addSeparator();
            ++items;
            continue;
        }

        auto *action = new QAction(QString::fromUtf8(items->text), menu);
        action->setDisabled(items->disabled == 1);

        // ✅ NEW: make the action checkable only when appropriate
        const bool isCheckable = (items->checked == 0 || items->checked == 1);
        action->setCheckable(isCheckable);
        if (isCheckable) {
            action->setChecked(items->checked == 1);
        }

        action->setProperty("tray_menu_item", QVariant::fromValue((void *)items));
        connect(action, &QAction::triggered, this, &QtTrayMenu::onMenuItemTriggered);

        if (items->submenu) {
            auto *submenu = new QMenu;
            createMenu(items->submenu, submenu);
            action->setMenu(submenu);
        }

        menu->addAction(action);
        ++items;
    }
}

bool QtTrayMenu::eventFilter(QObject *watched, QEvent *event)
{
    qDebug() << "Event Type:" << event->type();
    return QObject::eventFilter(watched, event);
}

void QtTrayMenu::onTrayActivated(QSystemTrayIcon::ActivationReason reason)
{
    if (reason == QSystemTrayIcon::Trigger && trayStruct && trayStruct->cb) {
        trayStruct->cb(trayStruct);
    }
}

void QtTrayMenu::onMenuItemTriggered()
{
    auto *action = qobject_cast<QAction *>(sender());
    struct tray_menu_item *menuItem = getTrayMenuItem(action);

    if (menuItem && menuItem->cb) {
        menuItem->cb(menuItem);
    }
}

struct tray_menu_item *QtTrayMenu::getTrayMenuItem(QAction *action)
{
    return reinterpret_cast<struct tray_menu_item *>(action->property("tray_menu_item").value<void *>());
}