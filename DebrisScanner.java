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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Sucht im Nether nach Ancient Debris in den bereits geladenen Chunks.
 * Pro Tick werden nur wenige Chunks geprüft, damit das Spiel nicht ruckelt.
 */
public final class DebrisScanner {
	private static final int RADIUS = 6;
	private static final int MIN_Y = 0;
	private static final int MAX_Y = 40;
	private static final int CHUNKS_PER_TICK = 2;

	private static final Map<Long, List<BlockPos>> FOUND = new HashMap<>();
	private static final BlockPos.MutableBlockPos CURSOR_POS = new BlockPos.MutableBlockPos();
	private static int cursor = 0;

	private DebrisScanner() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DebrisScanner::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FOUND.clear());
	}

	public static void forEach(Consumer<BlockPos> consumer) {
		for (List<BlockPos> list : FOUND.values()) {
			for (BlockPos pos : list) {
				consumer.accept(pos);
			}
		}
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;

		if (level == null || player == null || !Config.enabled
			|| !Config.isEnabled(StorageType.DEBRIS) || level.dimension() != Level.NETHER) {
			if (!FOUND.isEmpty()) {
				FOUND.clear();
			}
			return;
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

			List<BlockPos> result = scanChunk(level, chunkX, chunkZ);
			if (result.isEmpty()) {
				FOUND.remove(key);
			} else {
				FOUND.put(key, result);
			}
		}
	}

	private static List<BlockPos> scanChunk(ClientLevel level, int chunkX, int chunkZ) {
		List<BlockPos> result = new ArrayList<>();
		int startX = chunkX << 4;
		int startZ = chunkZ << 4;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					CURSOR_POS.set(startX + x, y, startZ + z);
					if (level.getBlockState(CURSOR_POS).is(Blocks.ANCIENT_DEBRIS)) {
						result.add(CURSOR_POS.immutable());
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
