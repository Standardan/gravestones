package io.github.standardan.gravestones;

import io.github.standardan.gravestones.command.GraveCommand;
import io.github.standardan.gravestones.grave.GraveManager;
import io.github.standardan.gravestones.listener.GraveListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class GraveStonesPlugin extends JavaPlugin {

    private GraveManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new GraveManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(new GraveListener(manager), this);

        PluginCommand command = Objects.requireNonNull(getCommand("graves"), "graves missing from plugin.yml");
        command.setExecutor(new GraveCommand(this, manager));

        // Refresh hologram countdowns and decay expired graves once per second.
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);

        getLogger().info("GraveStones enabled.");
    }

    // No onDisable cleanup: grave entities are persistent (saved with the world)
    // and grave data is written to graves.yml as it changes.
}
