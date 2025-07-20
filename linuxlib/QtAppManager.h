// QtAppManager.h
#ifndef QT_APP_MANAGER_H
#define QT_APP_MANAGER_H

#include <QApplication>
#include <QMutex>
#include <QWaitCondition>
#include <QThread>
#include <memory>

class QtAppManager : public QThread {
    Q_OBJECT

public:
    static QtAppManager& instance();
    QApplication* getApp();
    bool isReady();
    void ensureRunning();

protected:
    void run() override;

private:
    QtAppManager();
    ~QtAppManager() override;

    QApplication* app;
    bool initialized;
    bool running;
    QMutex mutex;
    QWaitCondition initCondition;
};

#endif // QT_APP_MANAGER_H