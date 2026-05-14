package org.herolias.plugin.enchantment;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hooks the exact event Hytale fires inside its harvest pickup path.
 *
 * Sickle harvest chain (verified by decompiling the server jar):
 *   Sickle_Attack interaction
 *     → Sickle_Swing_Left/Right
 *     → BreakBlockInteraction with Harvest=true
 *     → BlockHarvestUtils.performPickupByInteraction
 *     → ItemUtils.interactivelyPickupItem  ← FIRES InteractivelyPickupItemEvent for each drop
 *     → Player.giveItem (puts the stack into inventory)
 *
 * The event is cancellable and exposes a mutable ItemStack. We intercept it
 * before the engine deposits the stack, multiply the quantity in place, and
 * the engine then deposits the larger stack. This means:
 *   • One event per drop type (wheat, life essence, seeds, ...) — each is
 *     multiplied independently with no double-counting.
 *   • No echo / recursion problems — we modify the existing stack rather
 *     than depositing a separate one.
 *   • Fires only on the harvest pickup path — picking items off the ground,
 *     /give, crafting, etc. don't trigger this event.
 */
public class EnchantmentPlentifulHarvestSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EnchantmentManager enchantmentManager;

    public EnchantmentPlentifulHarvestSystem(EnchantmentManager enchantmentManager) {
        super(InteractivelyPickupItemEvent.class);
        this.enchantmentManager = enchantmentManager;
        LOGGER.atInfo().log("EnchantmentPlentifulHarvestSystem initialized");
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractivelyPickupItemEvent event) {
        if (event.isCancelled())
            return;

        Entity entity = EntityUtils.getEntity(index, chunk);
        if (!(entity instanceof Player player))
            return;

        Inventory inventory = player.getInventory();
        if (inventory == null)
            return;

        ItemStack held = inventory.getItemInHand();
        if (held == null || held.isEmpty())
            return;
        if (!enchantmentManager.hasEnchantment(held, EnchantmentType.PLENTIFUL_HARVEST))
            return;
        if (enchantmentManager.categorizeItem(held) != ItemCategory.SICKLE)
            return;

        ItemStack original = event.getItemStack();
        if (original == null || original.isEmpty())
            return;
        // Belt-and-suspenders: never multiply the sickle itself
        if (original.getItemId().equals(held.getItemId()))
            return;

        int level = enchantmentManager.getEnchantmentLevel(held, EnchantmentType.PLENTIFUL_HARVEST);
        int extraRolls = rollExtraRolls(level);
        if (extraRolls <= 0)
            return; // unlucky — no bonus this harvest

        int multiplier = 1 + extraRolls;
        int newQty = original.getQuantity() * multiplier;
        ItemStack multiplied = original.withQuantity(newQty);
        if (multiplied == null || multiplied.isEmpty())
            return;

        event.setItemStack(multiplied);

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        PlayerRef playerRefComp = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        EnchantmentEventHelper.fireActivated(playerRefComp, held, EnchantmentType.PLENTIFUL_HARVEST, level);
    }

    /**
     * Mirrors Fortune's roll mechanic: rolls {@code level} times with the
     * configured per-level chance, returns the count of successes. With the
     * default 50% chance:
     *   PH I  → 0 or 1 extra roll (50/50)   → avg drops 1.50× (~ Fortune II)
     *   PH II → 0, 1, or 2 extra rolls
     *           P=25%/50%/25% → avg drops 2.00× (~ Fortune III + a bit)
     */
    private int rollExtraRolls(int level) {
        if (level <= 0)
            return 0;
        double chancePerLevel = EnchantmentType.PLENTIFUL_HARVEST.getEffectMultiplier();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int extras = 0;
        for (int i = 0; i < level; i++) {
            if (random.nextDouble() < chancePerLevel)
                extras++;
        }
        return extras;
    }
}
