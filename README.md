# Storage Finder (Fabric, Minecraft 26.1)

Client-Mod, der Lager-Blöcke durch Wände anzeigt: Kisten, Redstone-Kisten, Endertruhen, Fässer,
Shulkerboxen, Trichter, Werfer, Spender, Öfen, Brauständer, Spawner (auch Trial Spawner),
Tresore, Crafter, Bücherregale und Dekotöpfe. Jede Art hat ihre eigene Farbe.

## Bedienung

| Taste | Funktion |
|---|---|
| Rechte Shift | Menü öffnen (Arten einzeln an/aus, Linien, Reichweite) |
| G | Anzeige schnell an/aus |

Die Tasten lassen sich unter Optionen > Steuerung > "Storage Finder" ändern.
Einstellungen werden in `config/storagefinder.json` gespeichert.

## Bauen

Voraussetzungen: JDK 25.

1. Gradle-Wrapper erzeugen (einmalig): `gradle wrapper --gradle-version 9.4.0`
   oder `gradlew`, `gradlew.bat` und `gradle/wrapper/gradle-wrapper.jar` aus der
   Fabric-Example-Mod (https://github.com/FabricMC/fabric-example-mod) hierher kopieren.
2. `./gradlew build` (Windows: `gradlew.bat build`)
3. Fertige Datei: `build/libs/storagefinder-1.0.0.jar` in den `mods`-Ordner legen
   (zusammen mit Fabric Loader >= 0.19.1 und Fabric API).

Zum Testen direkt aus dem Quellcode: `./gradlew runClient`.

## Hinweis zu Servern

Auf vielen Servern sind solche Mods verboten und führen zum Bann. Nutze ihn nur dort,
wo es erlaubt ist (Singleplayer, eigene Server, Anarchie-Server).

## Falls der Build Fehler meldet

Minecraft 26.1 hat das Rendering stark umgebaut. Die Stellen, die sich am ehesten ändern:

- `Tracker.java`: Event `LevelRenderEvents.END_EXTRACTION` und die Gizmo-Aufrufe
  (`Gizmos.cuboid`, `Gizmos.line`, `GizmoStyle.stroke`, `setAlwaysOnTop`).
- `StorageFinderClient.java`: `KeyMapping.Category.register(...)` und
  `minecraft.gui.setOverlayMessage(...)`.
