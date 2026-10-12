package com.sablednah.crawlspace.neoforge;

import com.sablednah.crawlspace.CrawlSpace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Heroic re-entry (Sable's ruling, 2026-10-12). Using a replay tablet inside
 * a dungeon reads it: the dungeon restocks at once, even with people in it, so
 * a party can retry together, and until its next ordinary restock it carries
 * the tablet's boon and bane, for everyone. A second tablet replaces the
 * first's traits.
 */
public final class Heroic {

    private static final String APPLIED = "crawlspace_heroic";

    private Heroic() {
    }

    private static String key(Site site) {
        return "traits:" + site.origin().asLong();
    }

    /** The dungeon's boon and bane, as "BOON:BANE", or null. */
    static String traits(ServerLevel level, Site site) {
        return CrawlState.of(level).get(key(site));
    }

    static boolean has(ServerLevel level, BlockPos pos, Enum<?> trait) {
        Site site = Dungeons.at(level, pos).orElse(null);
        String t = site == null ? null : traits(level, site);
        return t != null && (t.startsWith(trait.name() + ":") || t.endsWith(":" + trait.name()));
    }

    /** An ordinary restock ends a heroic run. */
    static void clear(ServerLevel level, Site site) {
        CrawlState.of(level).put(key(site), null);
    }

    /** Using an item: a replay tablet, read inside a dungeon. */
    public static void onUseItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = e.getItemStack();
        String traits = Tablets.traitsOf(stack);
        if (traits == null) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        Site site = Dungeons.at(level, player.blockPosition()).orElse(null);
        if (site == null) {
            Triggers.tell(player, "A replay tablet is read inside a dungeon: it wakes that dungeon again, harder.");
            return;
        }
        stack.shrink(1);
        CrawlState.of(level).put(key(site), traits);
        int rooms = Restocks.restock(level, site);
        Tablets.Boon boon = Tablets.Boon.valueOf(traits.substring(0, traits.indexOf(':')));
        Tablets.Bane bane = Tablets.Bane.valueOf(traits.substring(traits.indexOf(':') + 1));
        for (ServerPlayer p : level.players()) {
            if (site.contains(p.blockPosition())) {
                p.sendSystemMessage(Component.literal("The tablet crumbles as it is read. The dungeon wakes again, harder (" + rooms
                        + " rooms full again): ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("+ " + boon.text + ". ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("- " + bane.text + ".").withStyle(ChatFormatting.RED)));
            }
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.5, player.getZ(), 60, 0.6, 0.8, 0.6, 0.6);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.PLAYERS, 1f, 0.6f);
        CrawlSpace.LOGGER.info("CrawlSpace: a replay tablet ({}) woke the dungeon at {}", traits, site.origin());
    }

    /** A dungeon monster joining a heroic dungeon: its bane, once. */
    static void strengthen(ServerLevel level, Mob mob) {
        if (mob.entityTags().contains(APPLIED)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        String t = site == null ? null : traits(level, site);
        if (t == null) {
            return;
        }
        mob.addTag(APPLIED);
        if (t.endsWith(":" + Tablets.Bane.TOUGH.name())) {
            var a = mob.getAttribute(Attributes.MAX_HEALTH);
            if (a != null) {
                a.addOrReplacePermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "heroic_tough"), 0.5,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                mob.setHealth(mob.getMaxHealth());
            }
        }
        if (t.endsWith(":" + Tablets.Bane.FIERCE.name())) {
            var a = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (a != null) {
                a.addOrReplacePermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "heroic_fierce"), 0.5,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** Twice the experience from a dungeon monster, with the XP boon. */
    public static void onExperience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent e) {
        if (e.getEntity().entityTags().contains(Powers.NOXP)) {
            e.setDroppedExperience(0); // a numbered monster killed out of order
            return;
        }
        if (e.getEntity() instanceof Mob mob && mob.entityTags().contains(Bestiary.KIN) && mob.level() instanceof ServerLevel level
                && has(level, mob.blockPosition(), Tablets.Boon.XP)) {
            e.setDroppedExperience(e.getDroppedExperience() * 2);
        }
    }

    /**
     * The bottom boss falls: a heroic run is over (Sable, 2026-10-12). Found by
     * the bottom level's lair or altar near where it died (the arena's pit is
     * below the lair), so a mid-level boss does not end it.
     */
    private static void finale(ServerLevel level, Mob mob) {
        if (!mob.entityTags().contains(Bosses.TAG) || mob.entityTags().contains(Decoys.TAG)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        if (site == null || traits(level, site) == null) {
            return;
        }
        int bottom = site.built().plan().levels().size() - 1;
        for (com.sablednah.crawlspace.build.Trigger t : site.built().blueprint().triggers()) {
            if ((t.kind() == com.sablednah.crawlspace.build.Trigger.Kind.BOSS || t.kind() == com.sablednah.crawlspace.build.Trigger.Kind.ALTAR)
                    && t.level() == bottom
                    && site.origin().offset(t.x(), t.y(), t.z()).closerThan(mob.blockPosition(), 48)) {
                clear(level, site);
                for (ServerPlayer p : level.players()) {
                    if (site.contains(p.blockPosition())) {
                        p.sendSystemMessage(Component.literal("The heroic run is won: the dungeon's boon and bane fade with its master.")
                                .withStyle(ChatFormatting.AQUA));
                    }
                }
                CrawlSpace.LOGGER.info("CrawlSpace: the heroic run at {} ended with its bottom boss", site.origin());
                return;
            }
        }
    }

    /** With the gold boon, a dungeon monster carries some. The bottom boss ends a heroic run. */
    static void died(ServerLevel level, Mob mob) {
        finale(level, mob);
        if (mob.entityTags().contains(Bestiary.KIN) && has(level, mob.blockPosition(), Tablets.Boon.GOLD)) {
            mob.spawnAtLocation(level, new ItemStack(Items.GOLD_NUGGET, 2 + level.getRandom().nextInt(5)));
            if (level.getRandom().nextFloat() < 0.2f) {
                mob.spawnAtLocation(level, new ItemStack(Items.GOLD_INGOT));
            }
        }
    }
}
