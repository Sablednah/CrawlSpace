package com.sablednah.crawlspace.neoforge;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * Restocking (Sable's ruling, 2026-10-12): every few hours of real time
 * (`play.restockHours`, 12) a dungeon fills again, so a server's dungeon stays
 * worth the trip, daily-ish but at no fixed hour. Never while anyone else is
 * inside: it happens as someone arrives.
 *
 * <p>It is lazy, so unloaded chunks do not matter. Its rooms, lairs and cube
 * forget they woke and wake again as a player comes near; its mimics and
 * trapped chests are armed again; and each chest refills the next time it is
 * opened, from the same table it was first filled from. A Lootr chest is
 * Lootr's to refill. What is done once stays done: vaults, puzzles, seals,
 * the tomb, and the altar's count.</p>
 */
public final class Restocks {

    private Restocks() {
    }

    private static String atKey(Site site) {
        return "restockAt:" + site.origin().asLong();
    }

    private static String epochKey(Site site) {
        return "epoch:" + site.origin().asLong();
    }

    static int epoch(CrawlState state, Site site) {
        String e = state.get(epochKey(site));
        return e == null ? 0 : Integer.parseInt(e);
    }

    /** From Triggers' tick: a player in a dungeon. The first visit starts its clock; a due one restocks it. */
    static void check(ServerLevel level, ServerPlayer player, Site site) {
        int hours = CrawlConfig.restockHours();
        if (hours <= 0) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        long now = System.currentTimeMillis();
        String at = state.get(atKey(site));
        if (at == null) {
            state.put(atKey(site), Long.toString(now));
            return;
        }
        if (now - Long.parseLong(at) < hours * 3_600_000L) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p != player && !p.isSpectator() && site.contains(p.blockPosition())) {
                return; // occupied: not under someone's feet
            }
        }
        restock(level, site);
        player.sendSystemMessage(Component.literal("The dungeon has stirred since anyone was last here: its rooms are full again, and its chests too.")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Fills a dungeon again now. Returns how many rooms will wake again. */
    static int restock(ServerLevel level, Site site) {
        CrawlState state = CrawlState.of(level);
        state.put(atKey(site), Long.toString(System.currentTimeMillis()));
        state.put(epochKey(site), Integer.toString(epoch(state, site) + 1));
        BlockPos o = site.origin();
        int rooms = 0;
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind().isEncounter() || t.kind() == Trigger.Kind.CUBE) {
                BlockPos p = o.offset(t.x(), t.y(), t.z());
                if (state.hasFired(p)) {
                    state.unfire(p);
                    rooms++;
                }
            }
        }
        // Mimics and trapped chests, armed again.
        site.built().blueprint().forEachColumn(col -> {
            for (int k = 0; k < col.codes().length; k++) {
                int code = col.codes()[k];
                if (code != 0 && Blueprint.part(code) == Part.CHEST) {
                    state.unfire(o.offset(col.x(), col.y0() + k, col.z()).above(150));
                }
            }
        });
        CrawlSpace.LOGGER.info("CrawlSpace: the dungeon at {} restocked (epoch {}): {} room(s) will wake again", o, epoch(state, site), rooms);
        return rooms;
    }

    /** Opening a dungeon chest: if the dungeon has restocked since it was last filled, it fills again first. */
    static void refill(ServerLevel level, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0) {
            return;
        }
        String table = Site.tableFor(site.built(), code);
        if (table == null || !(level.getBlockState(pos).getBlock() instanceof ChestBlock || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARREL))
                || !(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) {
            return; // not ours to refill: a Lootr chest, or a chest someone put there
        }
        CrawlState state = CrawlState.of(level);
        int epoch = epoch(state, site);
        String key = "chest:" + pos.asLong();
        String filled = state.get(key);
        if (epoch == 0 || filled != null && Integer.parseInt(filled) >= epoch) {
            return;
        }
        state.put(key, Integer.toString(epoch));
        chest.clearContent();
        chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, table)), level.getRandom().nextLong());
    }
}
