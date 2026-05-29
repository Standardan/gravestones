package io.github.standardan.gravestones.grave;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, tracks, and removes graves, and owns their Display/Interaction
 * entities. Grave records live in graves.yml; the entities are persistent
 * (saved with the world), and each is tagged with its grave id so we can
 * recognise it when clicked.
 */
public final class GraveManager {

    private final Plugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, Grave> graves = new ConcurrentHashMap<>();
    private final File file;
    private YamlConfiguration cfg;

    private int protectSeconds;
    private int expireSeconds;

    public GraveManager(Plugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "grave_id");
        this.file = new File(plugin.getDataFolder(), "graves.yml");
        loadSettings();
    }

    public void loadSettings() {
        protectSeconds = plugin.getConfig().getInt("protection-seconds", 60);
        expireSeconds = plugin.getConfig().getInt("expire-seconds", 0);
    }

    // --- creation -----------------------------------------------------------

    public Grave createGrave(Player player, List<ItemStack> items, int xp) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Location loc = player.getLocation();
        World world = loc.getWorld();
        long now = nowMillis();

        Interaction interaction = world.spawn(loc, Interaction.class, in -> {
            in.setInteractionWidth(1.0f);
            in.setInteractionHeight(1.5f);
            in.setPersistent(true);
            in.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        });
        TextDisplay text = world.spawn(loc.clone().add(0, 1.4, 0), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(true);
            td.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        });

        Grave grave = new Grave(id, player.getUniqueId(), player.getName(), world.getName(),
                loc.getX(), loc.getY(), loc.getZ(), items, xp, now,
                protectSeconds, expireSeconds, text.getUniqueId(), interaction.getUniqueId());
        text.setText(holoText(grave, now));

        graves.put(id, grave);
        save(grave);
        return grave;
    }

    // --- lookup / looting ---------------------------------------------------

    public Grave getById(String id) {
        return graves.get(id);
    }

    /** Read the grave id tagged on an entity (the Interaction hitbox), or null. */
    public String graveIdOf(Entity entity) {
        return entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public List<Grave> gravesOf(UUID owner) {
        List<Grave> mine = new ArrayList<>();
        for (Grave g : graves.values()) {
            if (g.owner().equals(owner)) {
                mine.add(g);
            }
        }
        return mine;
    }

    /** Give a grave's contents to a player and remove it. */
    public void loot(Player player, Grave grave) {
        for (ItemStack item : grave.items()) {
            if (item == null) continue;
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (grave.xp() > 0) {
            player.giveExp(grave.xp());
        }
        removeGrave(grave.id());
    }

    public void removeGrave(String id) {
        Grave grave = graves.remove(id);
        if (grave == null) {
            return;
        }
        removeEntity(grave.textUuid());
        removeEntity(grave.interactionUuid());
        cfg.set("graves." + id, null);
        persist();
    }

    private void removeEntity(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    // --- periodic update ----------------------------------------------------

    /** Refresh hologram countdowns and decay expired graves. Runs every second. */
    public void tick() {
        long now = nowMillis();
        for (Grave grave : new ArrayList<>(graves.values())) {
            World world = Bukkit.getWorld(grave.world());
            if (world == null) {
                continue;
            }
            int cx = (int) Math.floor(grave.x()) >> 4;
            int cz = (int) Math.floor(grave.z()) >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                continue; // can't touch entities in unloaded chunks; handle when it loads
            }
            if (grave.expires() && now >= grave.expireAt()) {
                Location loc = new Location(world, grave.x(), grave.y(), grave.z());
                for (ItemStack item : grave.items()) {
                    if (item != null) world.dropItemNaturally(loc, item);
                }
                removeGrave(grave.id());
                continue;
            }
            if (Bukkit.getEntity(grave.textUuid()) instanceof TextDisplay display) {
                display.setText(holoText(grave, now));
            }
        }
    }

    private String holoText(Grave grave, long now) {
        String header = "§6☠ §e" + grave.ownerName() + "§6's grave";
        String sub;
        if (grave.expires()) {
            sub = "§7decays in §f" + Math.max(0, (grave.expireAt() - now) / 1000) + "s";
        } else if (now < grave.protectUntil()) {
            sub = "§7locked §f" + Math.max(0, (grave.protectUntil() - now) / 1000 + 1) + "s";
        } else {
            sub = "§aunlocked";
        }
        return header + "\n" + sub;
    }

    // --- persistence --------------------------------------------------------

    public void load() {
        graves.clear();
        cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("graves");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection g = section.getConfigurationSection(id);
            if (g == null) continue;
            try {
                List<ItemStack> items = new ArrayList<>();
                for (Object o : g.getList("items", new ArrayList<>())) {
                    if (o instanceof ItemStack stack) items.add(stack);
                }
                graves.put(id, new Grave(id,
                        UUID.fromString(g.getString("owner")),
                        g.getString("ownerName", "?"),
                        g.getString("world", "world"),
                        g.getDouble("x"), g.getDouble("y"), g.getDouble("z"),
                        items, g.getInt("xp"), g.getLong("createdAt"),
                        g.getInt("protectSeconds"), g.getInt("expireSeconds"),
                        UUID.fromString(g.getString("textUuid")),
                        UUID.fromString(g.getString("interactionUuid"))));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping malformed grave entry: " + id);
            }
        }
    }

    private void save(Grave grave) {
        if (cfg == null) {
            cfg = new YamlConfiguration();
        }
        String p = "graves." + grave.id();
        cfg.set(p + ".owner", grave.owner().toString());
        cfg.set(p + ".ownerName", grave.ownerName());
        cfg.set(p + ".world", grave.world());
        cfg.set(p + ".x", grave.x());
        cfg.set(p + ".y", grave.y());
        cfg.set(p + ".z", grave.z());
        cfg.set(p + ".items", grave.items());
        cfg.set(p + ".xp", grave.xp());
        cfg.set(p + ".createdAt", grave.createdAt());
        cfg.set(p + ".protectSeconds", grave.protectSeconds());
        cfg.set(p + ".expireSeconds", grave.expireSeconds());
        cfg.set(p + ".textUuid", grave.textUuid().toString());
        cfg.set(p + ".interactionUuid", grave.interactionUuid().toString());
        persist();
    }

    private void persist() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save graves.yml: " + e.getMessage());
        }
    }

    private long nowMillis() {
        return System.currentTimeMillis();
    }
}
