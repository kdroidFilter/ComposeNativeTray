
import com.kdroid.composetray.tray.NativeTray

fun main() {
    val trayIconPath = "C:\\Users\\Eyahou Gambache\\CLionProjects\\tray\\icon2.ico"

     NativeTray(
        iconPath = trayIconPath,
        menuContent = {
            SubMenu(label = "Element 1") {
                Item(label = "Sous Element 1" ) {
                    println("Sous-élément 1 cliqué\n")
                }
            }
            SubMenu("Élément avec autre sous-menu") {
               Item("Autre Sous-élément 1") {
                   println("Autre sous-élément 1 cliqué\n")
               }
                SubMenu("Autre Sous-élément avec Sous-sous-menu") {
                    Item("Sous-sous-élément 1") {
                        println("Sous-sous-élément 1 cliqué\n")
                    }
                    Item("Sous-sous-élément 2") {
                        println("Sous-sous-élément 2 cliqué\n")
                    }
                    Item("Sous-sous-élément 3") {
                        println("Sous-sous-élément 3 cliqué\n")
                    }
                }
            }
            Divider()
            Item("Élément désactivé", isEnabled = false) {}
            CheckableItem("Élément cochable") {
                println("Élément cochable cliqué, nouvel état: ${it}")
            }
            Item("Élément 2") {
                println("Élément 2 cliqué\n")
            }

            Item("Quitter") {
                println("Quitter l'application")
                dispose()
                System.exit(0)
            }
        }

    )
}