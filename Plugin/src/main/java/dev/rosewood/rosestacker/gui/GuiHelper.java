package dev.rosewood.rosestacker.gui;

import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings.LightLevel;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

/* package */ class GuiHelper {

    public static Material parseMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null)
            return Material.BARRIER;
        return material;
    }

    public static String getLightLevel(LocaleManager localeManager, LightLevel lightLevel) {
        switch (lightLevel) {
            case LIGHT:
                return localeManager.getLocaleMessage("gui-stacked-spawner-light");
            case DARK:
                return localeManager.getLocaleMessage("gui-stacked-spawner-dark");
            default:
                return localeManager.getLocaleMessage("gui-stacked-spawner-any");
        }
    }

    public static String getBoolean(LocaleManager localeManager, boolean value) {
        return localeManager.getLocaleMessage("gui-stacked-spawner-" + value);
    }

    public static class GuiStringHelper {
        private List<String> message;

        public GuiStringHelper(List<String> message) {
            this.message = message;
        }

        public String getName() {
            return this.message.get(0);
        }

        public List<String> getLore() {
            if (this.message.size() == 1)
                return Collections.emptyList();
            return this.message.subList(1, this.message.size());
        }
    }

}
