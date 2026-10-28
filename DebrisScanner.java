package storagefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Sucht je nach Dimension nach einem bestimmten Block in den bereits geladenen Chunks:
 * im Nether nach Antikem Schrott (Ancient Debris), in der Oberwelt nach ausgewachsenen
 * Amethyst-Clustern (Geoden). Pro Tick werden nur wenige Chunks geprüft, damit das Spiel nicht ruckelt.
 * Zeigt über der Hotbar an, wie viele Funde es gerade gibt.
 */
public final class DebrisScanner {
	private static final int RADIUS = 8;
	private static final int CHUNKS_PER_TICK = 3;

	private static final Map<Long, List<BlockPos>> FOUND = new HashMap<>();
	private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
	private static int cursor = 0;
	private static int lastReported = -1;
	private static StorageType activeType = null;

	private DebrisScanner() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DebrisScanner::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/** Welche Art gerade gesucht wird (Antiker Schrott oder Amethyst), oder null. */
	public static StorageType activeType() {
		return activeType;
	}

	public static void forEach(Consumer<BlockPos> consumer) {
		for (List<BlockPos> list : FOUND.values()) {
			for (BlockPos pos : list) {
				consumer.accept(pos);
			}
		}
	}

	public static int count() {
		int total = 0;
		for (List<BlockPos> list : FOUND.values()) {
			total += list.size();
		}
		return total;
	}

	private static void reset() {
		FOUND.clear();
		cursor = 0;
		lastReported = -1;
		activeType = null;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;

		StorageType wanted = null;
		Block block = null;
		int minY = 0;
		int maxY = 0;

		if (level != null && player != null && Config.enabled) {
			if (level.dimension() == Level.NETHER) {
				wanted = StorageType.DEBRIS;
				block = Blocks.ANCIENT_DEBRIS;
				minY = 4;
				maxY = 70;
			} else if (level.dimension() == Level.OVERWORLD) {
				wanted = StorageType.AMETHYST;
				block = Blocks.AMETHYST_CLUSTER;
				minY = -64;
				maxY = 40;
			}
		}

		if (wanted == null || !Config.isEnabled(wanted)) {
			if (activeType != null || !FOUND.isEmpty() || lastReported != -1) {
				reset();
			}
			return;
		}

		if (wanted != activeType) {
			reset();
			activeType = wanted;
		}

		int playerChunkX = player.getBlockX() >> 4;
		int playerChunkZ = player.getBlockZ() >> 4;
		int size = RADIUS * 2 + 1;
		int total = size * size;

		for (int i = 0; i < CHUNKS_PER_TICK; i++) {
			if (cursor >= total) {
				cursor = 0;
				purge(playerChunkX, playerChunkZ);
			}

			int chunkX = playerChunkX + (cursor % size) - RADIUS;
			int chunkZ = playerChunkZ + (cursor / size) - RADIUS;
			cursor++;

			long key = key(chunkX, chunkZ);
			if (!level.hasChunk(chunkX, chunkZ)) {
				FOUND.remove(key);
				continue;
			}

			List<BlockPos> result = scanChunk(level, chunkX, chunkZ, block, minY, maxY);
			if (result.isEmpty()) {
				FOUND.remove(key);
			} else {
				FOUND.put(key, result);
			}
		}

		// Rückmeldung, damit man sieht, dass der Scanner läuft.
		int found = count();
		if (found != lastReported) {
			lastReported = found;
			minecraft.gui.setOverlayMessage(Component.literal(wanted.label + " gefunden: " + found), false);
		}
	}

	private static List<BlockPos> scanChunk(ClientLevel level, int chunkX, int chunkZ, Block block, int minY, int maxY) {
		List<BlockPos> result = new ArrayList<>();
		int startX = chunkX << 4;
		int startZ = chunkZ << 4;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = minY; y <= maxY; y++) {
					SCAN_POS.set(startX + x, y, startZ + z);
					if (level.getBlockState(SCAN_POS).is(block)) {
						result.add(SCAN_POS.immutable());
					}
				}
			}
		}
		return result;
	}

	/** Entfernt Ergebnisse von Chunks, die weit weg sind. */
	private static void purge(int playerChunkX, int playerChunkZ) {
		FOUND.keySet().removeIf(key -> {
			int chunkX = (int) (key >> 32);
			int chunkZ = (int) (long) key;
			return Math.abs(chunkX - playerChunkX) > RADIUS + 1 || Math.abs(chunkZ - playerChunkZ) > RADIUS + 1;
		});
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
