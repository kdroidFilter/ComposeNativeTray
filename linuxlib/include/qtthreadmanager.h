// File: qtthreadmanager.h
#pragma once

#include <QThread>
#include <QApplication>
#include <QMutex>
#include <QWaitCondition>
#include <QEventLoop>
#include <functional>

/**
 * QtThreadManager
 * ---------------
 * • Singleton paresseux : un nouvel objet QThread est créé
 *   si le précédent est inexistant **ou** déjà terminé.
 * • Peut donc être arrêté puis recréé indéfiniment.
 */
class QtThreadManager : public QThread
{
    Q_OBJECT
public:
    /** Renvoie une instance *active* du thread Qt */
    static QtThreadManager* instance();

    /** Arrête proprement le thread et la QApplication (idempotent) */
    static void shutdown();

    /** Exécute `fn` dans le thread Qt puis bloque jusqu’à son retour */
    void runBlocking(const std::function<void()>& fn);

    /** Exécute `fn` dans le thread Qt de façon asynchrone */
    void runAsync(const std::function<void()>& fn);

    /** Accès direct (lecture seule) à la QApplication */
    QApplication* app() const { return m_app; }

protected:
    void run() override;      // point d’entrée du QThread

private:
    QtThreadManager();        // construction privée
    ~QtThreadManager() override = default;

    /** Initialisation commune (création + attente de QApplication) */
    static QtThreadManager* createAndStart();

    QApplication*  m_app      = nullptr;
    QMutex         readyMutex;
    QWaitCondition readyCond;
};
