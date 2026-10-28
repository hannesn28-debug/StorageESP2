package storagefinder;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Alle Block-Arten, die der Finder anzeigen kann.
 * Die Zuordnung läuft über die Registry-ID des BlockEntity-Typs (z. B. "chest"),
 * damit keine einzelnen Klassen importiert werden müssen.
 */
public enum StorageType {
	CHEST("Kisten", 0xFFFFA500, "chest"),
	TRAPPED_CHEST("Redstone-Kisten", 0xFFFF3030, "trapped_chest"),
	ENDER_CHEST("Endertruhen", 0xFFAA00FF, "ender_chest"),
	BARREL("Fässer", 0xFFC08040, "barrel"),
	SHULKER("Shulkerboxen", 0xFFFF55FF, "shulker_box"),
	HOPPER("Trichter", 0xFFAAAAAA, "hopper"),
	DISPENSER("Werfer", 0xFF55AAFF, "dispenser"),
	DROPPER("Spender", 0xFF5577FF, "dropper"),
	FURNACE("Öfen", 0xFFFF7755, "furnace", "blast_furnace", "smoker"),
	BREWING("Brauständer", 0xFFFFFF55, "brewing_stand"),
	SPAWNER("Spawner", 0xFF00FF55, "mob_spawner", "trial_spawner"),
	VAULT("Tresore", 0xFFFFD700, "vault"),
	CRAFTER("Crafter", 0xFF99CCFF, "crafter"),
	BOOKSHELF("Bücherregale", 0xFFDDBB88, "chiseled_bookshelf"),
	POT("Dekotöpfe", 0xFFCC6644, "decorated_pot"),
	DEBRIS("Antiker Schrott", 0xFFE0A070),
	AMETHYST("Amethyst-Cluster", 0xFFD070FF);

	private static final Map<String, StorageType> BY_ID = new HashMap<>();

	static {
		for (StorageType type : values()) {
			for (String id : type.ids) {
				BY_ID.put(id, type);
			}
		}
	}

	public final String label;
	public final int color;
	private final String[] ids;

	StorageType(String label, int color, String... ids) {
		this.label = label;
		this.color = color;
		this.ids = ids;
	}

	/** Registry-Name des BlockEntity-Typs (z. B. "chest") oder ein leerer Text. */
	public static String idOf(BlockEntity blockEntity) {
		var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
		return key == null ? "" : key.getPath();
	}

	/** Liefert die Art des BlockEntity oder null, wenn er nicht angezeigt werden soll. */
	public static StorageType of(BlockEntity blockEntity) {
		var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
		if (key == null) {
			return null;
		}
		return BY_ID.get(key.getPath());
	}
}
