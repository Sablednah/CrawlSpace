package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Feeling;
import com.sablednah.crawlspace.plan.LevelPlan;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Arriving on a level: its number and theme as a title, and its feeling, if it
 * has one, underneath, the first time a player reaches it this session. A
 * hunted level lets its hunter loose on the first arrival of anyone, ever.
 */
public final class Arrivals {

    /** Per player: the levels already announced to them this session, as "x,y,z/level". */
    private static final Map<UUID, Set<String>> SEEN = new HashMap<>();

    private Arrivals() {
    }

    /** The level whose band holds {@code feet}, or -1: a level runs from four below its floor to eight above. */
    static int levelAt(Site site, BlockPos feet) {
        DungeonPlan plan = site.built().plan();
        int py = feet.getY() - site.origin().getY();
        for (int i = 0; i < plan.levels().size(); i++) {
            int fy = Blueprinter.floorY(plan, i);
            if (py >= fy - 4 && py <= fy + 8) {
                return i;
            }
        }
        return -1;
    }

    /** From Triggers' tick, once a second, for a player inside a dungeon. */
    static void tick(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        int i = levelAt(site, feet);
        if (i < 0) {
            return;
        }
        String key = site.origin().toShortString() + "/" + i;
        if (!SEEN.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(key)) {
            return;
        }
        LevelPlan lp = site.built().plan().levels().get(i);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("Level " + (i + 1) + ": " + lp.theme.name()).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(lp.feeling == Feeling.NONE ? Component.empty()
                : Component.literal(lp.feeling.message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        if (lp.feeling == Feeling.HUNTED && !player.isCreative() && !player.isSpectator()) {
            // Once per level, ever: kept with the dungeon's other fired triggers, under a key no block uses.
            BlockPos marker = site.origin().offset(0, Blueprinter.floorY(site.built().plan(), i) - 1000, 0);
            CrawlState state = CrawlState.of(level);
            if (!state.hasFired(marker)) {
                state.fire(marker);
                Bestiary.hunter(level, site, i, player);
            }
        }
    }

    public static void forget(UUID player) {
        SEEN.remove(player);
    }
}
