#include "QtAppManager.h"
#include <QCoreApplication>
#include <QTimer>

QtAppManager& QtAppManager::instance() {
    static QtAppManager instance;
    return instance;
}

QtAppManager::QtAppManager() : app(nullptr), initialized(false), running(false) {
    initializeQt();
}

QtAppManager::~QtAppManager() {
    if (app) {
        app->quit();
    }
    if (qtThread && qtThread->isRunning()) {
        qtThread->wait(1000);  // Wait for thread to finish after quit
    }
}

void QtAppManager::initializeQt() {
    if (QApplication::instance()) {
        app = qobject_cast<QApplication*>(QApplication::instance());
        initialized = true;
        running = true;
        return;
    }

    // Create Qt in a dedicated thread
    qtThread = std::make_unique<QThread>();
    qtThread->start();

    // Initialize QApplication in the Qt thread
    QTimer::singleShot(0, qtThread.get(), [this]() {
        if (!QApplication::instance()) {
            static int argc = 1;
            static char app_name[] = "TrayMenuApp";
            static char *argv[] = {app_name, nullptr};
            app = new QApplication(argc, argv);
            app->setQuitOnLastWindowClosed(false);
        } else {
            app = qobject_cast<QApplication*>(QApplication::instance());
        }
        initialized = true;
        running = true;

        // Run the proper event loop instead of polling
        app->exec();

        running = false;  // Exec returned
    });

    // Wait for initialization with reduced polling
    while (!initialized) {
        QThread::msleep(5);
    }
}

QApplication* QtAppManager::getApp() {
    return app;
}

bool QtAppManager::isReady() {
    return initialized && app;
}

void QtAppManager::ensureRunning() {
    if (!running || !app) {
        initializeQt();
    }
}