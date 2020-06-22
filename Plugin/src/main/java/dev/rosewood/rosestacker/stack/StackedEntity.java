package dev.rosewood.rosestacker.stack;

import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.event.AsyncEntityDeathEvent;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.NMSUtil;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.utils.StackerUtils;
import dev.rosewood.rosestacker.utils.StringPlaceholders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;

public class StackedEntity extends Stack {

    private LivingEntity entity;
    private String originalCustomName;
    private List<byte[]> serializedStackedEntities;

    private EntityStackSettings stackSettings;

    public StackedEntity(int id, LivingEntity entity, List<byte[]> serializedStackedEntities, String originalCustomName) {
        super(id);

        this.entity = entity;
        this.serializedStackedEntities = serializedStackedEntities;

        if (this.entity != null) {
            this.originalCustomName = originalCustomName;
            this.stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getEntityStackSettings(this.entity);

            if (Bukkit.isPrimaryThread())
                this.updateDisplay();
        }
    }

    public StackedEntity(LivingEntity entity, List<byte[]> serializedStackedEntities) {
        this(-1, entity, serializedStackedEntities, null);
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    public void updateEntity() {
        LivingEntity entity = (LivingEntity) Bukkit.getEntity(this.entity.getUniqueId());
        if (entity == null || entity == this.entity)
            return;

        this.entity = entity;
        this.updateDisplay();
    }

    public String getOriginalCustomName() {
        return this.originalCustomName;
    }

    public void updateOriginalCustomName() {
        this.originalCustomName = this.entity.getCustomName();
        this.updateDisplay();
    }

    public void increaseStackSize(LivingEntity entity) {
        this.increaseStackSize(entity, true);
    }

    public void increaseStackSize(LivingEntity entity, boolean updateDisplay) {
        Runnable task = () -> {
            byte[] nbtData = NMSUtil.getHandler().getEntityAsNBT(entity, Setting.ENTITY_SAVE_ATTRIBUTES.getBoolean());
            if (Setting.ENTITY_STACK_TO_BOTTOM.getBoolean()) {
                this.serializedStackedEntities.add(nbtData);
            } else {
                this.serializedStackedEntities.add(0, nbtData);
            }

            if (updateDisplay)
                this.updateDisplay();
        };

        // For some reason, a VillagerAcquireTradeEvent is called when saving a villager's nbt.
        // Since we usually do this async and the event isn't allowed to be async, Spigot throws a fit.
        // We switch over to a non-async thread specifically for villagers because of this.
        if (!Bukkit.isPrimaryThread() && entity instanceof Merchant) {
            Bukkit.getScheduler().runTask(RoseStacker.getInstance(), task);
        } else {
            task.run();
        }
    }

    public void increaseStackSize(List<byte[]> entityNBTStrings) {
        if (Setting.ENTITY_STACK_TO_BOTTOM.getBoolean()) {
            this.serializedStackedEntities.addAll(entityNBTStrings);
        } else {
            this.serializedStackedEntities.addAll(0, entityNBTStrings);
        }
        this.updateDisplay();
    }

    public void decreaseStackSize() {
        LivingEntity oldEntity = this.entity;
        Location location = this.entity.getLocation();
        this.entity = null; // Null it first so the CreatureSpawnEvent doesn't conflict with this Stack
        this.entity = NMSUtil.getHandler().spawnEntityFromNBT(this.serializedStackedEntities.remove(0), location);
        this.updateOriginalCustomName();
        RoseStacker.getInstance().getManager(StackManager.class).updateStackedEntityKey(oldEntity, this.entity);
    }

    public List<byte[]> getStackedEntityNBT() {
        return Collections.unmodifiableList(this.serializedStackedEntities);
    }

    /**
     * Drops all loot and experience for all internally-stacked entities.
     * Does not include loot for the current entity.
     *
     * @param existingLoot The loot from this.entity, nullable
     * @param droppedExp The exp dropped from this.entity
     */
    public void dropStackLoot(Collection<ItemStack> existingLoot, int droppedExp) {
        LivingEntity thisEntity = this.entity;
        Collection<ItemStack> loot = new ArrayList<>();
        if (existingLoot != null)
            loot.addAll(existingLoot);

        Bukkit.getScheduler().runTaskAsynchronously(RoseStacker.getInstance(), () -> {
            boolean callEvents = Setting.ENTITY_TRIGGER_DEATH_EVENT_FOR_ENTIRE_STACK_KILL.getBoolean();
            int fireTicks = thisEntity.getFireTicks(); // Propagate fire ticks so meats cook as you would expect
            int totalExp = droppedExp;
            NMSHandler nmsHandler = NMSUtil.getHandler();
            for (byte[] entityNBT : this.serializedStackedEntities) {
                LivingEntity entity = nmsHandler.getNBTAsEntity(thisEntity.getType(), thisEntity.getLocation(), entityNBT);
                if (entity != null) {
                    entity.setFireTicks(fireTicks);
                    Collection<ItemStack> entityLoot = StackerUtils.getEntityLoot(entity, thisEntity.getKiller(), thisEntity.getLocation());
                    if (callEvents) {
                        EntityDeathEvent deathEvent = new AsyncEntityDeathEvent(entity, new ArrayList<>(entityLoot), droppedExp);
                        Bukkit.getPluginManager().callEvent(deathEvent);
                        totalExp += deathEvent.getDroppedExp();
                        loot.addAll(deathEvent.getDrops());
                    } else {
                        loot.addAll(entityLoot);
                        totalExp += droppedExp;
                    }
                }
            }

            int finalTotalExp = totalExp;
            World world = this.entity.getLocation().getWorld();
            if (world != null) {
                Bukkit.getScheduler().runTask(RoseStacker.getInstance(), () -> {
                    RoseStacker.getInstance().getManager(StackManager.class).preStackItems(loot, this.entity.getLocation());
                    if (Setting.ENTITY_DROP_ACCURATE_EXP.getBoolean() && finalTotalExp > 0) {
                        ExperienceOrb experienceOrb = world.spawn(this.entity.getLocation(), ExperienceOrb.class);
                        experienceOrb.setExperience(finalTotalExp);
                    }
                });
            }
        });
    }

    /**
     * @return true if this entity should stay stacked, otherwise false
     */
    public boolean shouldStayStacked() {
        if (this.serializedStackedEntities.isEmpty())
            return true;

        LivingEntity entity = NMSUtil.getHandler().getNBTAsEntity(this.entity.getType(), this.entity.getLocation(), this.serializedStackedEntities.get(0));
        StackedEntity stackedEntity = new StackedEntity(entity, Collections.emptyList());
        return this.stackSettings.canStackWith(this, stackedEntity, true);
    }

    public StackedEntity split() {
        if (this.serializedStackedEntities.isEmpty())
            throw new IllegalStateException();

        StackManager stackManager = RoseStacker.getInstance().getManager(StackManager.class);

        LivingEntity oldEntity = this.entity;
        stackManager.setEntityStackingTemporarilyDisabled(true);
        this.entity = NMSUtil.getHandler().spawnEntityFromNBT(this.serializedStackedEntities.remove(0), oldEntity.getLocation());
        stackManager.setEntityStackingTemporarilyDisabled(false);
        this.stackSettings.applyUnstackProperties(this.entity, oldEntity);
        stackManager.updateStackedEntityKey(oldEntity, this.entity);
        this.updateDisplay();
        return new StackedEntity(oldEntity, Collections.synchronizedList(new LinkedList<>()));
    }

    public EntityStackSettings getStackSettings() {
        return this.stackSettings;
    }

    @Override
    public int getStackSize() {
        return this.serializedStackedEntities.size() + 1;
    }

    @Override
    public Location getLocation() {
        return this.entity.getLocation();
    }

    @Override
    public void updateDisplay() {
        if (!Setting.ENTITY_DISPLAY_TAGS.getBoolean())
            return;

        if (this.getStackSize() == 1 && this.originalCustomName != null) {
            this.entity.setCustomNameVisible(true);
            this.entity.setCustomName(this.originalCustomName);
            System.out.println(this.originalCustomName);
        } else if (this.getStackSize() > 1 || Setting.ENTITY_DISPLAY_TAGS_SINGLE.getBoolean()) {
            String displayString;
            if (this.originalCustomName != null && Setting.ENTITY_DISPLAY_TAGS_CUSTOM_NAME.getBoolean()) {
                displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("entity-stack-display-custom-name", StringPlaceholders.builder("amount", this.getStackSize())
                        .addPlaceholder("name", this.originalCustomName).build());
            } else {
                displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("entity-stack-display", StringPlaceholders.builder("amount", this.getStackSize())
                        .addPlaceholder("name", this.stackSettings.getDisplayName()).build());
            }

            this.entity.setCustomNameVisible(!Setting.ENTITY_DISPLAY_TAGS_HOVER.getBoolean());
            this.entity.setCustomName(displayString);
        } else {
            this.entity.setCustomNameVisible(false);
            this.entity.setCustomName(null);
        }
    }

}
