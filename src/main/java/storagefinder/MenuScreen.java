package storagefinder;

import java.util.function.Supplier;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menü zum Ein-/Ausschalten der Anzeige und der einzelnen Block-Arten. */
public class MenuScreen extends Screen {
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW = 22;
	private static final int COLUMN_WIDTH = 150;

	public MenuScreen() {
		super(Component.literal("Storage Finder"));
	}

	@Override
	protected void init() {
		int left = this.width / 2 - COLUMN_WIDTH - 3;
		int right = this.width / 2 + 3;

		addRenderableWidget(new StringWidget(this.width / 2 - 100, 10, 200, 14, this.title, this.font).alignCenter());

		addRenderableWidget(toggle(left, 28, () ->
			Component.literal("Anzeige: " + onOff(Config.enabled)), () -> Config.enabled = !Config.enabled));

		addRenderableWidget(toggle(right, 28, () ->
			Component.literal("Linien: " + onOff(Config.tracers)), () -> Config.tracers = !Config.tracers));

		addRenderableWidget(toggle(left, 28 + ROW, () ->
			Component.literal("Reichweite: " + (Config.maxDistance == 0 ? "unbegrenzt" : Config.maxDistance + " Blöcke")),
			Config::cycleDistance));

		addRenderableWidget(Button.builder(Component.literal("Alle an/aus"), button -> {
			Config.setAllTypes(!Config.allTypesEnabled());
			rebuildWidgets();
		}).bounds(right, 28 + ROW, COLUMN_WIDTH, BUTTON_HEIGHT).build());

		StorageType[] types = StorageType.values();
		int startY = 28 + ROW * 2 + 8;
		for (int i = 0; i < types.length; i++) {
			StorageType type = types[i];
			int x = (i % 2 == 0) ? left : right;
			int y = startY + (i / 2) * ROW;

			addRenderableWidget(toggle(x, y, () -> typeLabel(type), () ->
				Config.setEnabled(type, !Config.isEnabled(type))));
		}

		int doneY = startY + ((types.length + 1) / 2) * ROW + 6;
		addRenderableWidget(Button.builder(Component.literal("Fertig"), button -> onClose())
			.bounds(this.width / 2 - 75, doneY, 150, BUTTON_HEIGHT).build());
	}

	private static Component typeLabel(StorageType type) {
		// Farbiger Punkt in der Farbe des Rahmens, dahinter Name und Status.
		return Component.literal("■ ").withColor(type.color & 0xFFFFFF)
			.append(Component.literal(type.label + ": " + onOff(Config.isEnabled(type))).withColor(0xFFFFFF));
	}

	private static String onOff(boolean value) {
		return value ? "AN" : "AUS";
	}

	private static Button toggle(int x, int y, Supplier<Component> label, Runnable action) {
		return Button.builder(label.get(), button -> {
			action.run();
			button.setMessage(label.get());
		}).bounds(x, y, COLUMN_WIDTH, BUTTON_HEIGHT).build();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
