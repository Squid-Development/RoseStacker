package dev.rosewood.rosestacker.gui;

import dev.rosewood.guiframework.GuiFactory;
import dev.rosewood.guiframework.GuiFramework;
import dev.rosewood.guiframework.gui.GuiContainer;
import dev.rosewood.guiframework.gui.GuiSize;
import dev.rosewood.guiframework.gui.GuiString;
import dev.rosewood.guiframework.gui.screen.GuiScreen;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.SpawnerSpawnManager;
import dev.rosewood.rosestacker.stack.StackedSpawner;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings.InvalidSpawnCondition;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings.SpawnConditions;
import dev.rosewood.rosestacker.utils.StringPlaceholders;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class StackedSpawnerGui {

    private final LocaleManager localeManager;
    private final StackedSpawner stackedSpawner;
    private final GuiFramework guiFramework;
    private GuiContainer guiContainer;

    public StackedSpawnerGui(StackedSpawner stackedSpawner) {
        RoseStacker roseStacker = RoseStacker.getInstance();

        this.localeManager = roseStacker.getManager(LocaleManager.class);
        this.stackedSpawner = stackedSpawner;
        this.guiFramework = GuiFramework.instantiate(roseStacker);
        this.guiContainer = null;
    }

    public void openFor(Player player) {
        if (this.isInvalid())
            this.buildGui();
        this.guiContainer.openFor(player);
    }

    private void buildGui() {
        this.guiContainer = GuiFactory.createContainer()
                .setTickRate(Setting.SPAWNER_GUI_TICK_UPDATE_RATE.getInt());

        SpawnerStackSettings stackSettings = this.stackedSpawner.getStackSettings();

        GuiScreen mainScreen = GuiFactory.createScreen(this.guiContainer, GuiSize.ROWS_THREE)
                .setTitle(this.localeManager.getLocaleMessage("gui-stacked-spawner-title", StringPlaceholders.single("name", stackSettings.getDisplayName())))
                .addButtonAt(11, GuiFactory.createButton())
                .addButtonAt(13, GuiFactory.createButton())
                .addButtonAt(15, GuiFactory.createButton());

        List<Integer> fillerSlots = IntStream.range(0, GuiSize.ROWS_THREE.getNumSlots()).boxed().collect(Collectors.toList());
        fillerSlots.removeAll(Arrays.asList(7, 8, 9, 11, 13, 15, 17, 18, 19));

        Material filler = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_BORDER_MATERIAL.getString());
        Material corner = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_BORDER_CORNER_MATERIAL.getString());
        Material accent = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_BORDER_ACCENT_MATERIAL.getString());
        Material info = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_SPAWNER_STATS_MATERIAL.getString());
        Material spawner = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_CENTRAL_MATERIAL.getString());
        Material conditionsValid = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_VALID_SPAWN_CONDITIONS_MATERIAL.getString());
        Material conditionsInvalid = GuiHelper.parseMaterial(Setting.SPAWNER_GUI_INVALID_SPAWN_CONDITIONS_MATERIAL.getString());

        ItemStack fillerItem = new ItemStack(filler);
        ItemMeta fillerItemMeta = fillerItem.getItemMeta();
        if (fillerItemMeta != null) {
            fillerItemMeta.setDisplayName(" ");
            fillerItemMeta.addItemFlags(ItemFlag.values());
            fillerItem.setItemMeta(fillerItemMeta);
        }

        for (int slot : fillerSlots)
            mainScreen.addItemStackAt(slot, fillerItem);

        ItemStack cornerItem = fillerItem.clone();
        cornerItem.setType(corner);

        for (int slot : Arrays.asList(8, 18))
            mainScreen.addItemStackAt(slot, cornerItem);

        ItemStack accentItem = fillerItem.clone();
        accentItem.setType(accent);

        for (int slot : Arrays.asList(7, 9, 17, 19))
            mainScreen.addItemStackAt(slot, accentItem);

        mainScreen.addButtonAt(11, GuiFactory.createButton()
                .setIcon(info)
                .setName(this.getString("stats"))
                .setLoreSupplier(() -> {
                    List<GuiString> lore = new ArrayList<>();

                    SpawnConditions spawnConditions = stackSettings.getSpawnConditions();
                    List<String> biomes = spawnConditions.getSpawnBiomes().isEmpty() ? Collections.singletonList("ANY") : spawnConditions.getSpawnBiomes().stream().map(Enum::name).collect(Collectors.toList());
                    List<String> blocks = spawnConditions.getSpawnBlocks().contains(Material.AIR) ? Collections.singletonList("ANY") : spawnConditions.getSpawnBlocks().stream().map(Enum::name).collect(Collectors.toList());

                    lore.add(this.getString("min-spawn-delay", StringPlaceholders.single("delay", stackSettings.getMinSpawnDelay())));
                    lore.add(this.getString("max-spawn-delay", StringPlaceholders.single("delay", stackSettings.getMaxSpawnDelay())));
                    lore.add(this.getString("disabled-mob-ai", StringPlaceholders.single("disabled", GuiHelper.getBoolean(this.localeManager, stackSettings.isMobAIDisabled()))));
                    lore.add(this.getString("max-nearby-entities", StringPlaceholders.single("max", stackSettings.getMaxNearbyEntities())));
                    lore.add(this.getString("player-activation-range", StringPlaceholders.single("range", stackSettings.getPlayerActivationRange())));
                    lore.add(this.getString("spawn-range", StringPlaceholders.single("range", stackSettings.getSpawnRange())));

                    CreatureSpawner creatureSpawner = (CreatureSpawner) this.stackedSpawner.getSpawner().getBlock().getState();
                    if (Setting.SPAWNER_SPAWN_COUNT_STACK_SIZE_RANDOMIZED.getBoolean()) {
                        int maxSpawnAmount = creatureSpawner.getSpawnCount();
                        int minSpawnAmount = maxSpawnAmount / stackSettings.getSpawnCountStackSizeMultiplier();

                        lore.add(this.getString("min-spawn-amount", StringPlaceholders.single("amount", minSpawnAmount)));
                        lore.add(this.getString("max-spawn-amount", StringPlaceholders.single("amount", minSpawnAmount)));
                    } else {
                        lore.add(this.getString("spawn-amount", StringPlaceholders.single("amount", creatureSpawner.getSpawnCount())));
                    }

                    lore.add(GuiFactory.createString());
                    lore.add(this.getString("spawn-conditions"));
                    if (biomes.size() == 1) {
                        lore.add(this.getString("valid-biomes-single", StringPlaceholders.single("biome", biomes.get(0))));
                    } else {
                        lore.add(this.getString("valid-biomes-list"));
                        for (String biome : biomes)
                            lore.add(this.getString("valid-biomes-list-item", StringPlaceholders.single("biome", biome)));
                    }
                    if (blocks.size() == 1) {
                        lore.add(this.getString("valid-spawn-blocks-single", StringPlaceholders.single("block", blocks.get(0))));
                    } else {
                        lore.add(this.getString("valid-spawn-blocks-list"));
                        for (String block : blocks)
                            lore.add(this.getString("valid-spawn-blocks-list-item", StringPlaceholders.single("block", block)));
                    }
                    lore.add(this.getString("valid-light-level", StringPlaceholders.single("level", GuiHelper.getLightLevel(this.localeManager, spawnConditions.getRequiredLightLevel()))));

                    return lore;
                }));
        mainScreen.addButtonAt(13, GuiFactory.createButton()
                .setIcon(spawner)
                .setNameSupplier(() -> GuiFactory.createString(RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("spawner-stack-display", StringPlaceholders.builder("amount", this.stackedSpawner.getStackSize())
                        .addPlaceholder("name", stackSettings.getDisplayName()).build())))
                .setLoreSupplier(() -> {
                    CreatureSpawner creatureSpawner = (CreatureSpawner) this.stackedSpawner.getSpawner().getBlock().getState();
                    return Collections.singletonList(this.getString("time-until-next-spawn", StringPlaceholders.single("time", creatureSpawner.getDelay() - SpawnerSpawnManager.DELAY_THRESHOLD + 1)));
                }
            ));
        mainScreen.addButtonAt(15, GuiFactory.createButton()
                .setIconSupplier(() -> GuiFactory.createIcon(this.stackedSpawner.getLastInvalidConditions().isEmpty() ? conditionsValid : conditionsInvalid))
                .setNameSupplier(() -> {
                    if (this.stackedSpawner.getLastInvalidConditions().isEmpty()) {
                        return this.getString("valid-spawn-conditions");
                    } else {
                        return this.getString("invalid-spawn-conditions");
                    }
                })
                .setLoreSupplier(() -> {
                    List<InvalidSpawnCondition> invalidConditions = this.stackedSpawner.getLastInvalidConditions();
                    if (invalidConditions.isEmpty())
                        return Collections.singletonList(this.getString("entities-can-spawn"));

                    List<GuiString> lore = new ArrayList<>();
                    lore.add(this.getString("conditions-preventing-spawns"));

                    if (invalidConditions.contains(InvalidSpawnCondition.ENTITY_CAP))
                        lore.add(this.getString("condition-nearby-entities"));
                    if (invalidConditions.contains(InvalidSpawnCondition.SPAWN_BIOME))
                        lore.add(this.getString("condition-biome"));
                    if (invalidConditions.contains(InvalidSpawnCondition.SPAWN_BLOCK))
                        lore.add(this.getString("condition-spawn-blocks"));
                    if (invalidConditions.contains(InvalidSpawnCondition.LIGHT_LEVEL))
                        lore.add(this.getString("condition-light-level"));

                    return lore;
                }));

        this.guiContainer.addScreen(mainScreen);

        this.guiFramework.getGuiManager().registerGui(this.guiContainer);
    }

    private GuiString getString(String key, StringPlaceholders placeholders) {
        return GuiFactory.createString(this.localeManager.getLocaleMessage("gui-stacked-spawner-" + key, placeholders));
    }

    private GuiString getString(String key) {
        return this.getString(key, StringPlaceholders.empty());
    }

    public void kickOutViewers() {
        if (this.guiContainer != null)
            this.guiContainer.closeViewers();
    }

    private boolean isInvalid() {
        return this.guiContainer == null || !this.guiFramework.getGuiManager().getActiveGuis().contains(this.guiContainer);
    }

}
