package storagefinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chunk-Finder: Untersucht die geladenen Chunks und schätzt, wie wahrscheinlich dort eine Basis steht.
 * Bewertet werden Maschinen (Trichter, Kolben, Redstone ...), Lager (Kisten, Fässer ...) und
 * gebaute Blöcke (Bretter, Stufen, Glas, Wolle ...). Treffer werden als Säule markiert.
 */
public final class BaseFinder {
	public record BaseChunk(int chunkX, int chunkZ, int machines, int storage, int built,
							int minY, int maxY, int percent) {
	}

	private static final int RADIUS = 12;
	private static final int CHUNKS_PER_TICK = 2;

	private static final int RED = 0xFFFF4040;
	private static final int ORANGE = 0xFFFF9030;
	private static final int YELLOW = 0xFFFFE040;

	private static final String[] DIRECTIONS = {"O", "SO", "S", "SW", "W", "NW", "N", "NO"};

	private static final Set<Block> MACHINES = Set.of(
		Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.OBSERVER, Blocks.PISTON,
		Blocks.STICKY_PISTON, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_LAMP,
		Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND, Blocks.CRAFTER);

	private static final Set<Block> STORAGE = Set.of(
		Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST);

	private static final Set<Block> BUILT = Set.of(
		Blocks.GLASS, Blocks.GLASS_PANE, Blocks.BRICKS, Blocks.STONE_BRICKS,
		Blocks.TORCH, Blocks.LANTERN, Blocks.CRAFTING_TABLE);

	/** Häufige Natur-Blöcke, die schnell übersprungen werden. */
	private static final Set<Block> NATURAL = Set.of(
		Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DEEPSLATE, Blocks.NETHERRACK,
		Blocks.BEDROCK, Blocks.WATER, Blocks.LAVA, Blocks.SAND, Blocks.GRAVEL, Blocks.SANDSTONE,
		Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF);

	private static final Map<Long, BaseChunk> CHUNKS = new HashMap<>();
	private static final BlockPos.MutableBlockPos SCAN_POS = new BlockPos.MutableBlockPos();
	private static int cursor = 0;
	private static int ticks = 0;

	private BaseFinder() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BaseFinder::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void reset() {
		CHUNKS.clear();
		cursor = 0;
		ticks = 0;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;

		if (level == null || player == null || !Config.enabled || !Config.susChunks) {
			if (!CHUNKS.isEmpty()) {
				reset();
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
				CHUNKS.remove(key);
				continue;
			}

			BaseChunk result = scanChunk(level, chunkX, chunkZ);
			if (result == null) {
				CHUNKS.remove(key);
			} else {
				CHUNKS.put(key, result);
			}
		}

		ticks++;
		if (ticks % 40 == 0) {
			showNearest(minecraft, player);
		}
	}

	private static BaseChunk scanChunk(ClientLevel level, int chunkX, int chunkZ) {
		int minScan = -64;
		int maxScan = 160;
		if (level.dimension() == Level.NETHER) {
			minScan = 0;
			maxScan = 127;
		} else if (level.dimension() == Level.END) {
			minScan = 0;
			maxScan = 255;
		}

		int machines = 0;
		int storage = 0;
		int built = 0;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int startX = chunkX << 4;
		int startZ = chunkZ << 4;

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = minScan; y <= maxScan; y++) {
					SCAN_POS.set(startX + x, y, startZ + z);
					BlockState state = level.getBlockState(SCAN_POS);
					if (state.isAir()) {
						continue;
					}

					Block block = state.getBlock();
					if (NATURAL.contains(block)) {
						continue;
					}

					if (MACHINES.contains(block)) {
						machines++;
					} else if (STORAGE.contains(block) || block instanceof ShulkerBoxBlock) {
						storage++;
					} else if (BUILT.contains(block)
						|| state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)
						|| state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)
						|| state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)
						|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)) {
						built++;
					} else {
						continue;
					}

					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}

		if (machines + storage + built == 0) {
			return null;
		}

		int points = Math.min(85, machines * 2) + Math.min(70, storage * 5) + Math.min(55, built / 3);
		int percent = Math.min(99, points * 100 / 150);
		if (percent < 10) {
			return null;
		}
		return new BaseChunk(chunkX, chunkZ, machines, storage, built, minY, maxY, percent);
	}

	/** Zeichnet jeden Treffer als Säule, Farbe nach Wahrscheinlichkeit. */
	public static void draw(Vec3 eye, Vec3 tracerStart, double max, double maxSq) {
		for (BaseChunk chunk : CHUNKS.values()) {
			if (chunk.percent() < Config.susThreshold) {
				continue;
			}

			double x0 = chunk.chunkX() * 16.0;
			double z0 = chunk.chunkZ() * 16.0;
			Vec3 center = new Vec3(x0 + 8.0, (chunk.minY() + chunk.maxY()) / 2.0, z0 + 8.0);
			if (max > 0 && center.distanceToSqr(eye) > maxSq) {
				continue;
			}

			AABB box = new AABB(x0, chunk.minY(), z0, x0 + 16.0, chunk.maxY() + 12.0, z0 + 16.0);
			int color = colorFor(chunk.percent());
			Gizmos.cuboid(box, GizmoStyle.stroke(color)).setAlwaysOnTop();

			if (Config.tracers) {
				Gizmos.line(tracerStart, center, color).setAlwaysOnTop();
			}
		}
	}

	/** Zeigt über der Hotbar den nächsten Treffer mit Prozent, Richtung und Entfernung. */
	private static void showNearest(Minecraft minecraft, LocalPlayer player) {
		BaseChunk best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BaseChunk chunk : CHUNKS.values()) {
			if (chunk.percent() < Config.susThreshold) {
				continue;
			}
			double dx = chunk.chunkX() * 16.0 + 8.0 - player.getX();
			double dz = chunk.chunkZ() * 16.0 + 8.0 - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = chunk;
			}
		}

		if (best == null) {
			return;
		}

		double dx = best.chunkX() * 16.0 + 8.0 - player.getX();
		double dz = best.chunkZ() * 16.0 + 8.0 - player.getZ();
		int index = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0);
		String direction = DIRECTIONS[((index % 8) + 8) % 8];

		String label = best.percent() >= 80 ? "Sehr wahrscheinlich Basis"
			: best.percent() >= 60 ? "Wahrscheinlich Basis" : "Verdächtiger Chunk";

		String text = label + " " + best.percent() + "% | " + direction + " " + (int) bestDistance + " m"
			+ " | Maschinen " + best.machines() + " | Lager " + best.storage() + " | Gebaut " + best.built();
		minecraft.gui.setOverlayMessage(Component.literal(text), false);
	}

	private static int colorFor(int percent) {
		if (percent >= 80) {
			return RED;
		}
		return percent >= 60 ? ORANGE : YELLOW;
	}

	/** Entfernt Ergebnisse von Chunks, die weit weg sind. */
	private static void purge(int playerChunkX, int playerChunkZ) {
		CHUNKS.keySet().removeIf(key -> {
			int chunkX = (int) (key >> 32);
			int chunkZ = (int) (long) key;
			return Math.abs(chunkX - playerChunkX) > RADIUS + 1 || Math.abs(chunkZ - playerChunkZ) > RADIUS + 1;
		});
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
