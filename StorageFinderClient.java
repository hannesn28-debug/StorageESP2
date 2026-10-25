package storagefinder;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class StorageFinderClient implements ClientModInitializer {
	public static final String MOD_ID = "storagefinder";

	private static KeyMapping menuKey;
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		Config.load();
		Tracker.init();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		menuKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.storagefinder.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.storagefinder.toggle", GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (toggleKey.consumeClick()) {
				Config.enabled = !Config.enabled;
				Config.save();
				minecraft.gui.setOverlayMessage(
					Component.literal("Storage Finder: " + (Config.enabled ? "AN" : "AUS")), false);
			}

			while (menuKey.consumeClick()) {
				if (minecraft.screen == null) {
					minecraft.setScreen(new MenuScreen());
				}
			}
		});
	}
}
