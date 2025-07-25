#include <QApplication>
#include <QIcon>
#include <QMenu>
#include <QTimer>
#include <QDebug>
#include "statusnotifieritem.h"

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);

    StatusNotifierItem trayIcon("example");
    trayIcon.setTitle("Tray Example");

    /* ---------- Icônes ---------- */
    const QString iconPath1 = "/home/elie-gambache/Images/avatar.png";            // Icône par défaut
    const QString iconPath2 = "/usr/share/icons/hicolor/48x48/apps/firefox.png";  // Icône alternative (exemple)
    static bool useAltIcon = false;  // Permet de basculer entre les deux

    QIcon icon(iconPath1);

    // Force un rendu pour remplir availableSizes()
    QPixmap dummy = icon.pixmap(QSize(24, 24));
    if (icon.isNull() || dummy.isNull())
        qWarning() << "Échec de chargement icône" << iconPath1;

    trayIcon.setIconByPixmap(icon);

    /* ---------- ToolTip ---------- */
    trayIcon.setToolTipTitle("Mon App");
    trayIcon.setToolTipSubTitle("Exemple de StatusNotifierItem");

    /* ---------- Menu contextuel ---------- */
    QMenu *menu = new QMenu();

    // Action 1 existante
    QAction *action1 = menu->addAction("Action 1");
    QObject::connect(action1, &QAction::triggered, [](){
        qDebug() << "Action 1 a été cliquée !";
    });

    // ----- Nouvel item : changer dynamiquement l'icône -----
    QAction *changeIconAction = menu->addAction("Changer l'icône");
    QObject::connect(changeIconAction, &QAction::triggered,
                     [&trayIcon, &useAltIcon, iconPath1, iconPath2](){
        const QString &nextPath = useAltIcon ? iconPath1 : iconPath2;
        QIcon newIcon(nextPath);
        if (newIcon.isNull()) {
            qWarning() << "Échec de chargement de la nouvelle icône" << nextPath;
            return;
        }
        trayIcon.setIconByPixmap(newIcon);
        useAltIcon = !useAltIcon;  // Inverser pour la prochaine fois
        qDebug() << "Icône changée pour" << nextPath;
    });

    // Quitter l'application
    menu->addAction("Quitter", &app, &QApplication::quit);
    trayIcon.setContextMenu(menu);

    return app.exec();
}
