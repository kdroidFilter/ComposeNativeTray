#ifndef QT_APP_MANAGER_H
#define QT_APP_MANAGER_H

#include <QApplication>
#include <QThread>
#include <atomic>
#include <memory>

class QtAppManager {
public:
    static QtAppManager& instance();
    QApplication* getApp();
    bool isReady();
    void ensureRunning();

private:
    QtAppManager();
    ~QtAppManager();

    void initializeQt();

    std::unique_ptr<QThread> qtThread;
    QApplication* app;
    std::atomic<bool> initialized;
    std::atomic<bool> running;
};

#endif // QT_APP_MANAGER_H