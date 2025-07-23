// Mise à jour de src/exemple.c pour utiliser sni_exec() et ajouter un item qui change de nom

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>  // Added for sleep function
#include "../include/sni_wrapper.h"  // Inclusion avec le chemin relatif vers le dossier include

void* global_tray = NULL;  // Variable globale pour accéder au tray depuis les callbacks
void* global_menu = NULL;  // Nouvelle globale pour accéder au menu depuis les callbacks
void* change_name_item = NULL;  // Globale pour l'item qui change de nom
void* add_item_button = NULL;   // Globale pour l'item qui ajoute un item
void* disappear_item = NULL;    // Globale pour l'item qui disparaît
void* toggle_item = NULL;       // Globale pour l'item qui peut être activé/désactivé

void on_activate(int x, int y, void* data) {
    printf("Tray activated at (%d, %d)\n", x, y);
}

void on_secondary_activate(int x, int y, void* data) {
    printf("Secondary activate at (%d, %d)\n", x, y);
}

void on_scroll(int delta, int orientation, void* data) {
    printf("Scroll: delta=%d, orientation=%d\n", delta, orientation);
}

void on_action1(void* data) {
    printf("Action 1 clicked!\n");
}

void on_action2(void* data) {
    printf("Action 2 clicked!\n");
}

void on_checkable_action(void* data) {
    printf("Checkable action toggled!\n");
}

void on_submenu_action(void* data) {
    printf("Submenu action clicked!\n");
}

void on_change_icon(void* data) {
    printf("Changing icon dynamically!\n");
    // Chemin vers une nouvelle icône (remplacez par un chemin valide sur votre système)
    const char* new_icon_path = "/usr/share/icons/hicolor/48x48/apps/firefox.png";  // Exemple : icône de Firefox
    update_icon_by_path(global_tray, new_icon_path);
}

void on_change_name(void* data) {
    printf("Changing item name!\n");
    set_menu_item_text(change_name_item, "Nouveau Nom");
}

void on_add_item(void* data) {
    printf("Adding new item dynamically!\n");
    add_menu_action(global_menu, "Nouvel Item Ajouté", NULL, NULL);
}

void on_disappear(void* data) {
    printf("Making item disappear!\n");
    remove_menu_item(global_menu, disappear_item);
    disappear_item = NULL;  // Réinitialise le pointeur après suppression
}

void on_enable_item(void* data) {
    printf("Enabling item!\n");
    set_menu_item_enabled(toggle_item, 1);
}

void on_disable_item(void* data) {
    printf("Disabling item!\n");
    set_menu_item_enabled(toggle_item, 0);
}

void on_toggle_item(void* data) {
    printf("Toggle item clicked!\n");
}

int main() {
    init_tray_system();

    void* tray = create_tray("my_tray_example");
    if (!tray) {
        fprintf(stderr, "Failed to create tray\n");
        return 1;
    }
    global_tray = tray;  // Stocke le tray globalement pour l'accès dans les callbacks

    set_title(tray, "My Tray Example");
    set_status(tray, "Active");
    // Utilisation d'une icône à partir d'un chemin de fichier
    set_icon_by_path(tray, "/usr/share/icons/hicolor/48x48/apps/openjdk-17.png");
    set_tooltip_title(tray, "My App");
    set_tooltip_subtitle(tray, "Example Tooltip");

    set_activate_callback(tray, on_activate, NULL);
    set_secondary_activate_callback(tray, on_secondary_activate, NULL);
    set_scroll_callback(tray, on_scroll, NULL);

    void* menu = create_menu();
    if (!menu) {
        fprintf(stderr, "Failed to create menu\n");
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }
    global_menu = menu;  // Stocke le menu globalement

    // Ajout d'une action standard
    add_menu_action(menu, "Action 1", on_action1, NULL);

    // Ajout d'une action cochable
    add_checkable_menu_action(menu, "Toggle Me", 1, on_checkable_action, NULL);

    // Ajout d'un séparateur
    add_menu_separator(menu);

    // Création d'un sous-menu
    void* submenu = create_submenu(menu, "Submenu");
    if (!submenu) {
        fprintf(stderr, "Failed to create submenu\n");
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    // Ajout d'actions dans le sous-menu
    add_menu_action(submenu, "Submenu Action", on_submenu_action, NULL);
    add_menu_separator(submenu);
    add_menu_action(submenu, "Action 2", on_action2, NULL);

    // Ajout d'un nouvel item pour changer l'icône dynamiquement (dans le menu principal)
    add_menu_separator(menu);
    add_menu_action(menu, "Change Icon", on_change_icon, NULL);

    // Nouvel item qui change de nom quand on clique dessus
    add_menu_separator(menu);
    change_name_item = add_menu_action(menu, "Clique moi pour changer", on_change_name, NULL);
    if (!change_name_item) {
        fprintf(stderr, "Failed to create change name item\n");
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    // Nouvel item qui ajoute un item quand on clique dessus
    add_menu_separator(menu);
    add_item_button = add_menu_action(menu, "Ajoute un item", on_add_item, NULL);
    if (!add_item_button) {
        fprintf(stderr, "Failed to create add item button\n");
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    // Nouvel item qui disparaît quand on clique dessus
    add_menu_separator(menu);
    disappear_item = add_menu_action(menu, "Clique moi pour disparaître", on_disappear, NULL);
    if (!disappear_item) {
        fprintf(stderr, "Failed to create disappear item\n");
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    // Nouvel item qui peut être activé/désactivé (initialement activé)
    add_menu_separator(menu);
    toggle_item = add_menu_action(menu, "Item à toggler", on_toggle_item, NULL);
    if (!toggle_item) {
        fprintf(stderr, "Failed to create toggle item\n");
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    // Sous-menu pour activer/désactiver l'item
    add_menu_separator(menu);
    void* toggle_submenu = create_submenu(menu, "Toggle Item");
    if (!toggle_submenu) {
        fprintf(stderr, "Failed to create toggle submenu\n");
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    add_menu_action(toggle_submenu, "Activer", on_enable_item, NULL);
    add_menu_action(toggle_submenu, "Désactiver", on_disable_item, NULL);

    // Ajout d'un item disabled
    add_menu_separator(menu);
    void* disabled_item = add_disabled_menu_action(menu, "Item Disabled", NULL, NULL);
    if (!disabled_item) {
        fprintf(stderr, "Failed to create disabled item\n");
        destroy_handle(toggle_submenu);
        destroy_handle(submenu);
        destroy_handle(menu);
        destroy_handle(tray);
        shutdown_tray_system();
        return 1;
    }

    set_context_menu(tray, menu);

    // Afficher une notification
    show_notification(tray, "Hello", "This is a test notification", "dialog-information", 5000);

    // Boucle principale pour gérer les événements : utiliser sni_exec() pour un vrai event loop Qt
    printf("Tray is running. Press Ctrl+C to exit.\n");
    sni_exec();  // Bloquant, gère les events correctement

    destroy_handle(toggle_submenu); // Destruction du sous-menu toggle
    destroy_handle(submenu); // Destruction du sous-menu
    destroy_handle(menu);
    destroy_handle(tray);
    shutdown_tray_system();

    return 0;
}