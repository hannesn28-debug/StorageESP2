package storagefinder;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Merkt sich alle geladenen Lager-Blöcke und zeichnet sie jeden Frame
 * als Rahmen (und optional als Linie) "always on top", also durch Wände.
 */
public final class Tracker {
	private static final Map<BlockPos, StorageType> BLOCKS = new HashMap<>();

	private Tracker() {
	}

	public static void init() {
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			StorageType type = StorageType.of(blockEntity);
			if (type != null) {
				BLOCKS.put(blockEntity.getBlockPos().immutable(), type);
			}
		});

		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) ->
			BLOCKS.remove(blockEntity.getBlockPos()));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BLOCKS.clear());

		// Läuft einmal pro Frame, während Gizmos gesammelt werden.
		LevelRenderEvents.END_EXTRACTION.register(context -> render());
	}

	public static int count() {
		return BLOCKS.size();
	}

	private static void render() {
		if (!Config.enabled || BLOCKS.isEmpty()) {
			return;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 tracerStart = eye.add(player.getLookAngle().scale(0.6));
		double max = Config.maxDistance;
		double maxSq = max * max;

		for (Map.Entry<BlockPos, StorageType> entry : BLOCKS.entrySet()) {
			StorageType type = entry.getValue();
			if (!Config.isEnabled(type)) {
				continue;
			}

			BlockPos pos = entry.getKey();
			Vec3 center = Vec3.atCenterOf(pos);
			if (max > 0 && center.distanceToSqr(eye) > maxSq) {
				continue;
			}

			Gizmos.cuboid(new AABB(pos).inflate(0.002), GizmoStyle.stroke(type.color)).setAlwaysOnTop();

			if (Config.tracers) {
				Gizmos.line(tracerStart, center, type.color).setAlwaysOnTop();
			}
		}
	}
}
