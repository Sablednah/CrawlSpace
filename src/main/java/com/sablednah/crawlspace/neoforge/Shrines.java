package com.sablednah.crawlspace.neoforge;

import java.util.HashSet;
import java.util.Set;

import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Shrines (Diablo's; Pixel Dungeon's wells): a shrine room's altar, used,
 * gives that shrine's gift once to each player, and says what it is: "Shrine
 * of Sight". Which shrine it is comes from the dungeon's dice, so the same
 * room is always the same shrine. One in eight is cursed.
 */
public final class Shrines {

    private enum Kind {
        SIGHT("Shrine of Sight", "every monster near you glows, even through walls", 3),
        VIGOUR("Shrine of Vigour", "your wounds close", 3),
        SWIFTNESS("Shrine of Swiftness", "your feet and hands are quick", 3),
        WARDING("Shrine of Warding", "a ward settles on you", 3),
        FORTUNE("Shrine of Fortune", "luck is with you, and something falls at your feet", 2),
        CURSED("A cursed shrine", "the dead answer", 2);

        final String name;
        final String gift;
        final int weight;

        Kind(String name, String gift, int weight) {
            this.name = name;
            this.gift = gift;
            this.weight = weight;
        }
    }

    private static final Set<String> USED = new HashSet<>();

    private Shrines() {
    }

    /** Using a block: if it is a shrine room's altar, its gift. Returns whether it was one. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0 || com.sablednah.crawlspace.build.Blueprint.part(code) != com.sablednah.crawlspace.build.Part.ALTAR) {
            return false;
        }
        int li = Arrivals.levelAt(site, pos);
        if (li < 0) {
            return false;
        }
        com.sablednah.crawlspace.plan.LevelPlan lp = site.built().plan().levels().get(li);
        Room r = lp.room(lp.region(pos.getX() - o.getX(), pos.getZ() - o.getZ()));
        if (r == null || r.role != Role.SHRINE) {
            return false;
        }
        Kind kind = kindOf(site, li, r);
        if (!USED.add(player.getUUID() + "@" + pos.asLong())) {
            Triggers.tell(player, kind.name + " is quiet. It has given you what it had.");
            return true;
        }
        switch (kind) {
            case SIGHT -> {
                for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(40), m -> m instanceof Enemy)) {
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 90 * 20, 0));
                }
            }
            case VIGOUR -> {
                player.heal(player.getMaxHealth());
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120 * 20, 1));
            }
            case SWIFTNESS -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 180 * 20, 1));
                player.addEffect(new MobEffectInstance(MobEffects.HASTE, 180 * 20, 0));
            }
            case WARDING -> {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 180 * 20, 0));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 180 * 20, 0));
            }
            case FORTUNE -> {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 300 * 20, 0));
                Bestiary.glint(level, player, li);
            }
            case CURSED -> {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 10 * 20, 0));
                Bestiary.ambush(level, site, li, player);
            }
        }
        boolean bad = kind == Kind.CURSED;
        level.sendParticles(bad ? ParticleTypes.SOUL : ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                30, 0.4, 0.6, 0.4, 0.2);
        level.playSound(null, pos, bad ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, bad ? 0.6f : 1.4f);
        player.displayClientMessage(Component.literal(kind.name + ": " + kind.gift + ".")
                .withStyle(bad ? ChatFormatting.DARK_RED : ChatFormatting.AQUA), true);
        return true;
    }

    private static Kind kindOf(Site site, int li, Room r) {
        com.sablednah.crawlspace.plan.Dice dice = com.sablednah.crawlspace.plan.Dice.of(site.seed(), li, r.id, 0x5A1L);
        int total = 0;
        for (Kind k : Kind.values()) {
            total += k.weight;
        }
        int roll = dice.nextInt(total);
        for (Kind k : Kind.values()) {
            roll -= k.weight;
            if (roll < 0) {
                return k;
            }
        }
        return Kind.SIGHT;
    }

    public static void clear() {
        USED.clear();
    }
}
