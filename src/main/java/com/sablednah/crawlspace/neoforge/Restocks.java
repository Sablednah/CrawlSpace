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
 * the tomb, and the altar's count. Broken chests and barrels come back, and
 * doors are shut again, as a player comes near ({@link #mend}).</p>
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
        Heroic.clear(level, site); // an ordinary restock ends a heroic run
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

    /** Per blueprint: where its chests, barrels and doors are, {x, y, z, code}, relative to the origin. */
    private static final java.util.Map<Site.Built, java.util.List<int[]>> FIXTURES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static java.util.List<int[]> fixtures(Site.Built built) {
        return FIXTURES.computeIfAbsent(built, b -> {
            java.util.List<int[]> out = new java.util.ArrayList<>();
            b.blueprint().forEachColumn(col -> {
                for (int k = 0; k < col.codes().length; k++) {
                    int code = col.codes()[k];
                    if (code == 0) {
                        continue;
                    }
                    Part part = Blueprint.part(code);
                    if (part == Part.CHEST || part == Part.HOARD_CHEST || part == Part.BARREL || part == Part.DOOR_LOWER) {
                        out.add(new int[] {col.x(), col.y0() + k, col.z(), code});
                    }
                }
            });
            return out;
        });
    }

    /**
     * After a restock, a dungeon is put back as it was found (Sable, 2026-10-14:
     * a puzzle's chest he broke was still gone): a chest or barrel that was
     * broken comes back, and every wooden door is shut. Lazy, as the rest of a
     * restock is: each one is mended as a player comes within 24 blocks, once
     * per restock, so unloaded chunks are never touched. Only air is filled, so
     * a block a player put in the gap is left alone.
     */
    static void mend(ServerLevel level, ServerPlayer player, Site site) {
        CrawlState state = CrawlState.of(level);
        int epoch = epoch(state, site);
        if (epoch == 0) {
            return;
        }
        BlockPos o = site.origin();
        for (int[] f : fixtures(site.built())) {
            BlockPos p = o.offset(f[0], f[1], f[2]);
            if (Math.abs(p.getX() - player.getBlockX()) > 24 || Math.abs(p.getZ() - player.getBlockZ()) > 24
                    || Math.abs(p.getY() - player.getBlockY()) > 12) {
                continue;
            }
            String key = "mend:" + p.asLong();
            String done = state.get(key);
            if (done != null && Integer.parseInt(done) >= epoch) {
                continue;
            }
            state.put(key, Integer.toString(epoch));
            int code = f[3];
            net.minecraft.world.level.block.state.BlockState now = level.getBlockState(p);
            if (Blueprint.part(code) == Part.DOOR_LOWER) {
                if (now.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
                    door.setOpen(null, level, now, p, false);
                } else if (now.isAir() && level.getBlockState(p.above()).isAir()) {
                    int upper = site.built().blueprint().get(f[0], f[1] + 1, f[2]);
                    level.setBlock(p, site.state(site.built(), code, p), 2);
                    if (upper != 0) {
                        level.setBlock(p.above(), site.state(site.built(), upper, p.above()), 2);
                    }
                }
            } else if (now.isAir()) {
                level.setBlock(p, site.state(site.built(), code, p), 3);
                site.afterPlace(site.built(), level, p, code, level.getRandom());
                state.put("chest:" + p.asLong(), Integer.toString(epoch)); // freshly filled: no refill on top
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        6, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    /** Opening a dungeon chest: if the dungeon has restocked since it was last filled, it fills again first. */
    static void refill(ServerLevel level, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0) {
            return;
        }
        String table = Site.tableFor(site.built(), code);
        if (table != null && table.startsWith("chests/tier") && Heroic.has(level, pos, Tablets.Boon.LOOT)) {
            table = "chests/tier" + Math.min(5, Integer.parseInt(table.substring("chests/tier".length())) + 1);
        }
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
        chest.setLootTable(new net.minecraft.resources.ResourceLocation(CrawlSpace.MODID, table), level.getRandom().nextLong());
    }
}
