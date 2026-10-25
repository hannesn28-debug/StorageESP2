package storagefinder;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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

	/** Diese Arten zählen für den Sus-Chunk-Finder (Spawner, Tresore und Töpfe kommen oft natürlich vor). */
	private static final Set<StorageType> BASE_TYPES = EnumSet.of(
		StorageType.CHEST, StorageType.TRAPPED_CHEST, StorageType.ENDER_CHEST, StorageType.BARREL,
		StorageType.SHULKER, StorageType.HOPPER, StorageType.DISPENSER, StorageType.DROPPER,
		StorageType.FURNACE, StorageType.BREWING, StorageType.CRAFTER);

	private static final int SUS_COLOR = 0xFFFF2020;

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

		DebrisScanner.init();

		// Läuft einmal pro Frame, während Gizmos gesammelt werden.
		LevelRenderEvents.END_EXTRACTION.register(context -> render());
	}

	public static int count() {
		return BLOCKS.size();
	}

	private static void render() {
		if (!Config.enabled) {
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
			if (Config.isEnabled(type)) {
				draw(entry.getKey(), type, eye, tracerStart, max, maxSq);
			}
		}

		if (Config.isEnabled(StorageType.DEBRIS)) {
			DebrisScanner.forEach(pos -> draw(pos, StorageType.DEBRIS, eye, tracerStart, max, maxSq));
		}

		if (Config.susChunks) {
			drawSusChunks(eye, tracerStart, max, maxSq);
		}
	}

	/** Markiert Chunks mit auffällig vielen Lager-Blöcken (vermutlich Basen oder Farmen). */
	private static void drawSusChunks(Vec3 eye, Vec3 tracerStart, double max, double maxSq) {
		// Pro Chunk: [Anzahl, kleinste Y, größte Y]
		Map<Long, int[]> chunks = new HashMap<>();

		for (Map.Entry<BlockPos, StorageType> entry : BLOCKS.entrySet()) {
			if (!BASE_TYPES.contains(entry.getValue())) {
				continue;
			}
			BlockPos pos = entry.getKey();
			long key = ((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL);
			int[] stats = chunks.computeIfAbsent(key, k -> new int[] {0, pos.getY(), pos.getY()});
			stats[0]++;
			stats[1] = Math.min(stats[1], pos.getY());
			stats[2] = Math.max(stats[2], pos.getY());
		}

		for (Map.Entry<Long, int[]> entry : chunks.entrySet()) {
			int[] stats = entry.getValue();
			if (stats[0] < Config.susThreshold) {
				continue;
			}

			int chunkX = (int) (entry.getKey() >> 32);
			int chunkZ = (int) (long) entry.getKey();
			double minX = chunkX * 16.0;
			double minZ = chunkZ * 16.0;

			Vec3 center = new Vec3(minX + 8.0, (stats[1] + stats[2]) / 2.0, minZ + 8.0);
			if (max > 0 && center.distanceToSqr(eye) > maxSq) {
				continue;
			}

			AABB box = new AABB(minX, stats[1], minZ, minX + 16.0, stats[2] + 1.0, minZ + 16.0);
			Gizmos.cuboid(box, GizmoStyle.stroke(SUS_COLOR)).setAlwaysOnTop();

			if (Config.tracers) {
				Gizmos.line(tracerStart, center, SUS_COLOR).setAlwaysOnTop();
			}
		}
	}

	private static void draw(BlockPos pos, StorageType type, Vec3 eye, Vec3 tracerStart, double max, double maxSq) {
		Vec3 center = Vec3.atCenterOf(pos);
		if (max > 0 && center.distanceToSqr(eye) > maxSq) {
			return;
		}

		Gizmos.cuboid(new AABB(pos).inflate(0.002), GizmoStyle.stroke(type.color)).setAlwaysOnTop();

		if (Config.tracers) {
			Gizmos.line(tracerStart, center, type.color).setAlwaysOnTop();
		}
	}
}
