package storagefinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Einstellungen, gespeichert in config/storagefinder.json. */
public final class Config {
	/** Auswahl für die Reichweite im Menü; 0 = unbegrenzt. */
	public static final int[] DISTANCES = {32, 64, 128, 256, 0};

	/** Ab so vielen Lager-Blöcken in einem Chunk gilt er als verdächtig. */
	public static final int[] THRESHOLDS = {4, 8, 12, 20};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("storagefinder.json");

	public static boolean enabled = true;
	public static boolean tracers = false;
	public static int maxDistance = 128;
	public static boolean susChunks = true;
	public static int susThreshold = 8;

	private static final Map<StorageType, Boolean> TYPES = new EnumMap<>(StorageType.class);

	static {
		for (StorageType type : StorageType.values()) {
			TYPES.put(type, true);
		}
	}

	private Config() {
	}

	public static boolean isEnabled(StorageType type) {
		return TYPES.getOrDefault(type, true);
	}

	public static void setEnabled(StorageType type, boolean value) {
		TYPES.put(type, value);
	}

	public static boolean allTypesEnabled() {
		for (boolean value : TYPES.values()) {
			if (!value) {
				return false;
			}
		}
		return true;
	}

	public static void setAllTypes(boolean value) {
		for (StorageType type : StorageType.values()) {
			TYPES.put(type, value);
		}
	}

	public static void cycleDistance() {
		int index = 0;
		for (int i = 0; i < DISTANCES.length; i++) {
			if (DISTANCES[i] == maxDistance) {
				index = i;
				break;
			}
		}
		maxDistance = DISTANCES[(index + 1) % DISTANCES.length];
	}

	public static void cycleThreshold() {
		int index = 0;
		for (int i = 0; i < THRESHOLDS.length; i++) {
			if (THRESHOLDS[i] == susThreshold) {
				index = i;
				break;
			}
		}
		susThreshold = THRESHOLDS[(index + 1) % THRESHOLDS.length];
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			Data data = GSON.fromJson(Files.readString(FILE), Data.class);
			if (data == null) {
				return;
			}
			enabled = data.enabled;
			tracers = data.tracers;
			maxDistance = data.maxDistance;
			susChunks = data.susChunks;
			susThreshold = data.susThreshold;
			if (data.types != null) {
				for (StorageType type : StorageType.values()) {
					Boolean value = data.types.get(type.name());
					if (value != null) {
						TYPES.put(type, value);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[StorageFinder] Konfiguration konnte nicht geladen werden: " + e);
		}
	}

	public static void save() {
		Data data = new Data();
		data.enabled = enabled;
		data.tracers = tracers;
		data.maxDistance = maxDistance;
		data.susChunks = susChunks;
		data.susThreshold = susThreshold;
		for (StorageType type : StorageType.values()) {
			data.types.put(type.name(), isEnabled(type));
		}
		try {
			Files.writeString(FILE, GSON.toJson(data));
		} catch (IOException e) {
			System.err.println("[StorageFinder] Konfiguration konnte nicht gespeichert werden: " + e);
		}
	}

	private static final class Data {
		boolean enabled = true;
		boolean tracers = false;
		int maxDistance = 128;
		boolean susChunks = true;
		int susThreshold = 8;
		Map<String, Boolean> types = new LinkedHashMap<>();
	}
}
