package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.build.Trigger;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Room;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Objective rooms (Warhammer Quest's Adventure Book; Sable's ruling,
 * 2026-10-12). The set pieces are built by Blueprinter.objective; this runs
 * their jobs, the easiest of each to start with:
 * <ul>
 *   <li><b>Fighting Pit:</b> drop in, and its champion rises; kill it.</li>
 *   <li><b>Firechasm:</b> take the relic from its pedestal and throw it into the fire.</li>
 *   <li><b>Fountain of Light:</b> break the four corrupted stones in its rim, each at a price.</li>
 *   <li><b>Tomb Chamber:</b> lift the lid, and lay what rises to rest.</li>
 *   <li><b>Idol Chamber:</b> its shaman wakes as you enter; kill the shaman.</li>
 * </ul>
 * The job done, the room pays (a chest a tier up, and one to three replay
 * tablets), and the end portal frame in its wall takes its eye: using it takes
 * you to the surface by the entrance. Each room tells you its job as you
 * enter, and the frame tells you what it is waiting for.
 */
public final class ObjectiveRooms {

    static final String FOE = "crawlspace_objective_foe";
    static final String RELIC = "crawlspace_relic";
    private static final int STARTED = 1200;
    private static final int DONE = 1201;
    /** Fountain stones broken: at stone.above(BROKEN). */
    private static final int BROKEN = 1202;
    private static final String[] NAMES = {"the Fighting Pit", "the Firechasm", "the Fountain of Light", "the Tomb Chamber", "the Idol Chamber"};
    private static final String[] JOBS = {
            "Drop into the pit, and its champion rises to meet you.",
            "Take the relic from its pedestal and throw it into the fire.",
            "Break the four corrupted stones in its rim. Each will cost you.",
            "Lift the lid, and lay what rises to rest.",
            "Its shaman wakes! Kill the shaman."};
    private static final Map<UUID, Set<Long>> TOLD = new HashMap<>();

    private ObjectiveRooms() {
    }

    private static BlockPos at(Site site, int[] p) {
        return site.origin().offset(p[0], p[1], p[2]);
    }

    private static BlockPos key(Site site, Trigger t) {
        return site.origin().offset(t.x(), t.y(), t.z());
    }

    private static Room roomOf(Site site, Trigger t) {
        LevelPlan lp = site.built().plan().levels().get(t.level());
        return lp.room(lp.region(t.x(), t.z()));
    }

    private static boolean inRoom(Site site, Trigger t, BlockPos p) {
        Room r = roomOf(site, t);
        BlockPos o = site.origin();
        return r != null && r.contains(p.getX() - o.getX(), p.getZ() - o.getZ()) && Math.abs(p.getY() - (o.getY() + t.y())) <= 5;
    }

    /** From Triggers' tick: each objective room near the player. */
    static void tick(ServerLevel level, ServerPlayer player, Site site) {
        if (player.isSpectator()) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.OBJECTIVE || !inRoom(site, t, player.blockPosition())) {
                continue;
            }
            int type = t.targets()[0][0];
            BlockPos k = key(site, t);
            boolean done = state.hasFired(k.above(DONE));
            if (TOLD.computeIfAbsent(player.getUUID(), x -> new HashSet<>()).add(k.asLong())) {
                player.sendSystemMessage(Component.literal(capital(NAMES[type]) + ". " + (done
                        ? "Its work is done. The portal frame in the wall takes you to the surface."
                        : JOBS[type] + " When it is done, the portal frame in the wall is a way out.")).withStyle(ChatFormatting.GOLD));
            }
            if (done || player.isCreative() || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                continue;
            }
            BlockPos anchor = at(site, t.targets()[2]);
            boolean started = state.hasFired(k.above(STARTED));
            // A foe that vanished without dying (Peaceful, lava, a despawn) would leave the job started for ever:
            // with nothing of it left in the room, the job can be begun again.
            if (started && type != Blueprinter.FIRECHASM && type != Blueprinter.FOUNTAIN && level.getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(k).inflate(32), m -> m.isAlive() && m.entityTags().contains(FOE)).isEmpty()) {
                state.unfire(k.above(STARTED));
                started = false;
            }
            if (type == Blueprinter.PIT && !started && player.getY() < anchor.getY() + 1.5
                    && Math.abs(player.getX() - anchor.getX()) < 6 && Math.abs(player.getZ() - anchor.getZ()) < 6) {
                state.fire(k.above(STARTED));
                foe(level, site, t, EntityType.ZOMBIE, "The Pit Champion", anchor, player, 2);
            } else if (type == Blueprinter.IDOL && !started) {
                state.fire(k.above(STARTED));
                Mob shaman = foe(level, site, t, EntityType.EVOKER, "The Shaman", anchor.relative(net.minecraft.core.Direction.UP, 0), player, 1);
                for (int g = 0; g < 2 && shaman != null; g++) {
                    Entity e = EntityType.VINDICATOR.create(level, EntitySpawnReason.EVENT);
                    if (e instanceof Mob m) {
                        m.snapTo(shaman.getX() + (g == 0 ? 1.5 : -1.5), shaman.getY(), shaman.getZ(), 0, 0);
                        m.finalizeSpawn(level, level.getCurrentDifficultyAt(anchor), EntitySpawnReason.EVENT, null);
                        m.addTag(Bestiary.KIN);
                        m.setPersistenceRequired();
                        level.addFreshEntity(m);
                        m.setTarget(player);
                    }
                }
            } else if (type == Blueprinter.FIRECHASM) {
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24),
                        e -> RELIC.equals(tag(e.getItem())) && (e.isInLava() || e.isOnFire()))) {
                    item.discard();
                    complete(level, site, t, player);
                    break;
                }
            }
        }
    }

    private static String tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(RELIC, false) ? RELIC : null;
    }

    private static Mob foe(ServerLevel level, Site site, Trigger t, EntityType<?> type, String name, BlockPos at, ServerPlayer player, double health) {
        Entity e = type.create(level, EntitySpawnReason.EVENT);
        if (!(e instanceof Mob mob)) {
            return null;
        }
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mob.addTag(Powers.NOROLL);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        Powers.elite(mob, t.level() + 2, level.getRandom(), 1);
        var max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(max.getBaseValue() * health + 8.0 * t.level());
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
        mob.setCustomNameVisible(true);
        mob.addTag(FOE);
        mob.addTag(Bestiary.KIN);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        Powers.track(level, mob);
        mob.setTarget(player);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, at, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5f, 0.7f);
        return mob;
    }

    /** Using a block in an objective room: the relic's pedestal, the tomb, the exit frame. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0) {
            return false;
        }
        Part part = Blueprint.part(code);
        if (part != Part.OBJ_RELIC && part != Part.OBJ_TOMB && part != Part.EXIT_FRAME) {
            return false;
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.OBJECTIVE || !inRoom(site, t, pos)) {
                continue;
            }
            CrawlState state = CrawlState.of(level);
            BlockPos k = key(site, t);
            boolean done = state.hasFired(k.above(DONE));
            int type = t.targets()[0][0];
            if (part == Part.EXIT_FRAME) {
                if (!done) {
                    Triggers.tell(player, "The way out is closed. " + JOBS[type]);
                } else {
                    surface(level, player, site);
                }
            } else if (part == Part.OBJ_RELIC && !done) {
                ItemStack relic = new ItemStack(Items.HEART_OF_THE_SEA);
                relic.set(DataComponents.CUSTOM_NAME, Component.literal("The Relic").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(RELIC, true);
                relic.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                // Fire-proof, like netherite: dropped in lava it floats there long enough to be seen to go in. An
                // ordinary item burned up within a tick, before the once-a-second check could find it.
                relic.set(DataComponents.DAMAGE_RESISTANT, new net.minecraft.world.item.component.DamageResistant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.tags.DamageTypeTags.IS_FIRE)));
                if (!player.getInventory().add(relic)) {
                    level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), relic));
                }
                Triggers.tell(player, "You take the relic. Throw it into the fire (drop it into the lava).");
            } else if (part == Part.OBJ_TOMB && !done && !state.hasFired(k.above(STARTED))) {
                state.fire(k.above(STARTED));
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
                foe(level, site, t, EntityType.WITHER_SKELETON, "The Tomb Lord", pos.above(), player, 2);
            }
            return true;
        }
        return false;
    }

    /** A foe of an objective falls: its room's job is done. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.entityTags().contains(FOE)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        if (site == null) {
            return;
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.OBJECTIVE && key(site, t).closerThan(mob.blockPosition(), 24)) {
                ServerPlayer near = level.getNearestPlayer(mob, 32) instanceof ServerPlayer p ? p : null;
                complete(level, site, t, near);
                return;
            }
        }
    }

    /** Breaking a fountain's corrupted stone: it costs, and the fourth completes the job. */
    public static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null) {
            return;
        }
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.OBJ_CORRUPT) {
            return;
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.OBJECTIVE || !inRoom(site, t, pos)) {
                continue;
            }
            CrawlState state = CrawlState.of(level);
            state.fire(pos.above(BROKEN));
            player.hurtServer(level, level.damageSources().magic(), 4f);
            int left = 0;
            for (int k = 3; k < t.targets().length; k++) {
                left += state.hasFired(at(site, t.targets()[k]).above(BROKEN)) ? 0 : 1;
            }
            level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
            if (left == 0) {
                complete(level, site, t, player);
            } else {
                Triggers.tell(player, "The corruption bites as it breaks. " + left + " more.");
            }
            return;
        }
    }

    private static void complete(ServerLevel level, Site site, Trigger t, ServerPlayer player) {
        CrawlState state = CrawlState.of(level);
        BlockPos k = key(site, t);
        if (state.hasFired(k.above(DONE))) {
            return;
        }
        state.fire(k.above(DONE));
        BlockPos frame = at(site, t.targets()[1]);
        if (level.getBlockState(frame).is(Blocks.END_PORTAL_FRAME)) {
            level.setBlock(frame, level.getBlockState(frame).setValue(EndPortalFrameBlock.HAS_EYE, true), 3);
        }
        BlockPos anchor = at(site, t.targets()[2]);
        BlockPos chest = anchor.above();
        for (int d = 1; d <= 3 && !level.getBlockState(chest).isAir(); d++) {
            chest = anchor.offset(d, 1, 0);
        }
        if (level.getBlockState(chest).isAir()) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest,
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 3 + t.level() / 2))));
        }
        int tablets = 1 + level.getRandom().nextInt(3);
        for (int n = 0; n < tablets; n++) {
            level.addFreshEntity(new ItemEntity(level, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, Tablets.make(level.getRandom())));
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 40, 0.5, 0.8, 0.5, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, frame.getX() + 0.5, frame.getY() + 1, frame.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, frame, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5f, 1f);
        level.playSound(null, chest, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        for (ServerPlayer p : level.players()) {
            if (inRoom(site, t, p.blockPosition())) {
                p.sendSystemMessage(Component.literal("Objective complete! A chest appears, with " + tablets + " replay tablet"
                        + (tablets == 1 ? "" : "s") + ", and the portal frame in the wall takes its eye: it will take you to the surface.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        CrawlSpace.LOGGER.info("CrawlSpace: objective ({}) at {} done; {} tablet(s)", NAMES[t.targets()[0][0]], k, tablets);
    }

    /** The quick exit: to the ground by the entrance, north of the tower. */
    private static void surface(ServerLevel level, ServerPlayer player, Site site) {
        BlockPos o = site.origin();
        int x = o.getX();
        int z = o.getZ() - com.sablednah.crawlspace.build.Towers.REACH - 3;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.8, 0.3, 0.1);
        player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), 0f, 0f, false);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        Triggers.tell(player, "Out, into the open air, by the dungeon's entrance.");
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void clear() {
        TOLD.clear();
    }
}
