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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    /** On each foe, the key of the room it belongs to: a foe that chased you out of the room still ends its job. */
    private static final String OF = "crawlspace_objective_of_";
    /** When each tomb was last struck: one blow a second (Sable, 2026-10-12: it could be spam-clicked). */
    private static final Map<Long, Long> STRUCK = new HashMap<>();
    static final String RELIC = "crawlspace_relic";
    private static final int STARTED = 1200;
    private static final int DONE = 1201;
    /** Fountain stones broken: at stone.above(BROKEN). */
    private static final int BROKEN = 1202;
    private static final String[] NAMES = {"the Fighting Pit", "the Firechasm", "the Fountain of Light", "the Tomb Chamber", "the Idol Chamber"};
    /** Where each room's way out is: part of its set piece, and named so nobody hunts for it. */
    private static final String[] WAY_OUT = {"under the gong on the pit's far rim", "in the dragon's plinth", "in the fountain's rim",
            "at the head of the tomb's plinth", "in the idol's dais"};
    /** Marker: the room's guards, from the level below, have come. */
    private static final int GUARDED = 1203;
    /** Marker: the Eye-Thief is dead. He does not come back; a lost eye turns up again instead. */
    private static final int THIEF_DEAD = 1204;
    /** Each room's jobs, by type then job: the easiest first. */
    private static final String[][] JOBS = {
            {"Drop into the pit, and its champion rises to meet you.",
                    "Its guardian waits in the pit, and will not come out until you go in, or strike it from the rim."},
            {"Take the relic from its pedestal and throw it into the fire.",
                    "The Eye-Thief has the dragon's eye. Take it from him, and set it in the dragon's head."},
            {"Break the four corrupted stones in its rim. Each will cost you.",
                    "One stone of the fountain's rim holds its light. Find it, collect a vial, and pour it into the dry basin by the wall."},
            {"Lift the lid, and lay what rises to rest.",
                    "Break the tomb: twenty blows. While it stands, the dead keep coming."},
            {"Its shaman wakes! Kill the shaman.",
                    "Its shaman is summoning something. Kill him before the summoning ends, or he becomes it."}};
    static final String THIEF = "crawlspace_eye_thief";
    static final String EYE = "crawlspace_dragon_eye";
    static final String VIAL = "crawlspace_vial";
    private static final int SUMMONING_SECONDS = 45;
    private static final int TOMB_BLOWS = 20;
    /** Per objective (its trigger's key): when a summoning began, and its bar; when the tomb last sent its dead. */
    private static final Map<Long, Long> SINCE = new HashMap<>();
    private static final Map<Long, net.minecraft.server.level.ServerBossEvent> BARS = new HashMap<>();
    /** When each objective's way out appeared: a click within two seconds of it is not taken as leaving. */
    private static final Map<Long, Long> OPENED = new HashMap<>();

    private static String job(Trigger t) {
        return JOBS[t.targets()[0][0]][t.targets()[0][1]];
    }

    /** For /crawlspace info full: the room's name and which job it set, e.g. "the Fountain of Light (job 2)". */
    static String describe(Trigger t) {
        return NAMES[t.targets()[0][0]] + " (job " + (t.targets()[0][1] + 1) + ")";
    }

    /** For /crawlspace info full: what the job asks. */
    static String jobText(Trigger t) {
        return job(t);
    }
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
                        ? "Its work is done. The glowing portal frame " + WAY_OUT[type] + " takes you to the surface."
                        : job(t) + " When it is done, a way out opens " + WAY_OUT[type] + ".")).withStyle(ChatFormatting.GOLD));
            }
            if (type == Blueprinter.FOUNTAIN) {
                spray(level, k);
            }
            if (done || player.isCreative() || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
                continue;
            }
            BlockPos anchor = at(site, t.targets()[2]);
            if (!state.hasFired(k.above(GUARDED))) {
                // Its guards come from the level below, as in Warhammer Quest: an objective room is a level harder.
                state.fire(k.above(GUARDED));
                int below = Math.min(t.level() + 1, site.built().plan().levels().size() - 1);
                BlockPos frame = at(site, t.targets()[1]);
                BlockPos from = type == Blueprinter.FIRECHASM ? anchor
                        : new BlockPos((frame.getX() + k.getX()) / 2, k.getY() - 1, (frame.getZ() + k.getZ()) / 2);
                int made = Bestiary.band(level, site, below, standAt(level, from), 2 + t.level() / 2, player);
                if (made > 0) {
                    Triggers.tell(player, "Its guardians come for you, from deeper down!");
                }
            }
            boolean started = state.hasFired(k.above(STARTED));
            // A foe that vanished without dying (Peaceful, lava, a despawn) would leave the job started for ever:
            // with nothing of it left in the room, the job can be begun again.
            int job = t.targets()[0][1];
            boolean foeJob = type == Blueprinter.PIT || type == Blueprinter.IDOL || type == Blueprinter.TOMB && job == 0
                    || type == Blueprinter.FIRECHASM && job == 1;
            boolean thiefDead = type == Blueprinter.FIRECHASM && state.hasFired(k.above(THIEF_DEAD));
            if (thiefDead && !eyeAbout(level, k)) {
                // The eye was lost (into the lava, say): it turns up again where the thief stood, never a new thief.
                BlockPos at = standAt(level, anchor.above());
                level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                        special(Items.HEART_OF_THE_SEA, "The Dragon's Eye", EYE)));
                Triggers.tell(player, "The dragon's eye glints on the far side, where the thief fell.");
            }
            if (started && foeJob && !thiefDead && level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(k).inflate(128),
                    m -> m.isAlive() && m.getTags().contains(OF + k.asLong())).isEmpty()
                    && !(type == Blueprinter.FIRECHASM && eyeAbout(level, k))) {
                state.unfire(k.above(STARTED));
                started = false;
            }
            if (job == 1) {
                second(level, site, t, player, state, k, type, anchor, started);
                continue;
            }
            if (type == Blueprinter.PIT && !started && player.getY() < anchor.getY() + 1.5
                    && Math.abs(player.getX() - anchor.getX()) < 6 && Math.abs(player.getZ() - anchor.getZ()) < 6) {
                state.fire(k.above(STARTED));
                foe(level, site, t, EntityType.ZOMBIE, "The Pit Champion", anchor, player, 2);
            } else if (type == Blueprinter.IDOL && !started) {
                state.fire(k.above(STARTED));
                Mob shaman = foe(level, site, t, EntityType.EVOKER, "The Shaman", anchor.relative(net.minecraft.core.Direction.UP, 0), player, 1);
                for (int g = 0; g < 2 && shaman != null; g++) {
                    Entity e = EntityType.VINDICATOR.create(level);
                    if (e instanceof Mob m) {
                        m.moveTo(shaman.getX() + (g == 0 ? 1.5 : -1.5), shaman.getY(), shaman.getZ(), 0, 0);
                        m.finalizeSpawn(level, level.getCurrentDifficultyAt(anchor), MobSpawnType.EVENT, null, null);
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

    /** Whether a dragon's eye is about: dropped near the room, or carried by anyone. */
    private static boolean eyeAbout(ServerLevel level, BlockPos k) {
        if (!level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(k).inflate(32), e -> flag(e.getItem(), EYE)).isEmpty()) {
            return true;
        }
        for (ServerPlayer p : level.players()) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (flag(p.getInventory().getItem(i), EYE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 1.20.1 has no fire-resistant item component (1.20.5): the relic is made
     * fire-proof as an item entity instead, the moment one appears, so dropped
     * in lava it floats there long enough to be seen to go in. An ordinary item
     * burned up within a tick, before the once-a-second check could find it.
     */
    public static void onItemJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof ItemEntity item && flag(item.getItem(), RELIC)) {
            item.setInvulnerable(true);
        }
    }

    /** Whether the relic is about: dropped near the room (not yet in the fire), or carried by anyone. */
    private static boolean relicAbout(ServerLevel level, BlockPos k) {
        if (!level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(k).inflate(32), e -> flag(e.getItem(), RELIC)).isEmpty()) {
            return true;
        }
        for (ServerPlayer p : level.players()) {
            if (carries(p, RELIC)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a vial of light is about: dropped near the room, or carried by anyone. */
    private static boolean vialAbout(ServerLevel level, BlockPos k) {
        if (!level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(k).inflate(32), e -> flag(e.getItem(), VIAL)).isEmpty()) {
            return true;
        }
        for (ServerPlayer p : level.players()) {
            if (carries(p, VIAL)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player carries one of our special items, anywhere in their inventory. */
    private static boolean carries(ServerPlayer player, String key) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (flag(player.getInventory().getItem(i), key)) {
                return true;
            }
        }
        return false;
    }

    /** Takes one of our special items from anywhere in the inventory: nobody should have to hold it to hand it over. */
    private static boolean take(ServerPlayer player, String key) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (flag(s, key)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean flag(ItemStack stack, String key) {
        net.minecraft.nbt.CompoundTag data = ItemNbt.data(stack);
        return data != null && data.getBoolean(key);
    }

    /**
     * One of our quest items, on an inert base: the vial was a bottle o' enchanting
     * and the eye an eye of ender, so one threw for free XP and the other flew off
     * after a stronghold (Sable, 2026-10-12). Dragon's breath and a heart of the sea
     * do nothing when used.
     */
    private static ItemStack special(net.minecraft.world.item.Item item, String name, String key) {
        ItemStack s = new ItemStack(item);
        ItemNbt.name(s, Component.literal(name).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(key, true);
        ItemNbt.data(s, tag);
        ItemNbt.glint(s);
        return s;
    }

    /** The second job of each room, run from the tick while a player is in it. */
    private static void second(ServerLevel level, Site site, Trigger t, ServerPlayer player, CrawlState state, BlockPos k, int type,
            BlockPos anchor, boolean started) {
        switch (type) {
            case Blueprinter.PIT -> {
                if (!started) {
                    state.fire(k.above(STARTED));
                    Mob g = foe(level, site, t, EntityType.ZOMBIE, "The Pit Guardian", anchor, null, 3);
                    if (g != null) {
                        g.setNoAi(true);
                    }
                }
                // It comes for whoever drops in.
                if (player.getY() < anchor.getY() + 1.5 && Math.abs(player.getX() - anchor.getX()) < 6 && Math.abs(player.getZ() - anchor.getZ()) < 6) {
                    rouse(level, k, player);
                }
            }
            case Blueprinter.FIRECHASM -> {
                if (!started) {
                    state.fire(k.above(STARTED));
                    Mob thief = foe(level, site, t, EntityType.PILLAGER, "The Eye-Thief", anchor, player, 1);
                    if (thief != null) {
                        thief.removeTag(FOE); // keeps OF: while he lives, the job stays started
                        thief.addTag(THIEF);
                    }
                }
            }
            case Blueprinter.TOMB -> {
                // While it stands, the dead keep coming: a band every twenty seconds once the first blow is struck.
                if (started && level.getGameTime() - SINCE.getOrDefault(k.asLong(), 0L) > 400) {
                    SINCE.put(k.asLong(), level.getGameTime());
                    // They carry knockback, to drive whoever is breaking the tomb away from it (Sable, 2026-10-12).
                    Bestiary.band(level, site, t.level(), standAt(level, anchor.above().offset(2, 0, 0)), 2, player, ObjectiveRooms::shove);
                    Triggers.tell(player, "The dead answer the tomb's call!");
                }
            }
            case Blueprinter.FOUNTAIN -> {
                // Light rising off the water until someone carries a vial of it: the fountain is the thing to use.
                if (!carries(player, VIAL)) {
                    level.sendParticles(ParticleTypes.END_ROD, anchor.getX() + 0.5, anchor.getY() + 0.9, anchor.getZ() + 0.5,
                            4, 0.8, 0.1, 0.8, 0.03);
                    // And now and then a glint on the one stone that gives it: there to be found, not shown.
                    if (t.targets().length > 3 && level.getRandom().nextInt(3) == 0) {
                        BlockPos src = at(site, t.targets()[3]);
                        level.sendParticles(ParticleTypes.GLOW, src.getX() + 0.5, src.getY() + 1.05, src.getZ() + 0.5, 1, 0.2, 0, 0.2, 0);
                    }
                }
            }
            case Blueprinter.IDOL -> {
                if (!started) {
                    state.fire(k.above(STARTED));
                    Mob shaman = foe(level, site, t, EntityType.EVOKER, "The Shaman", anchor, player, 1);
                    if (shaman != null) {
                        shaman.setNoAi(true);
                    }
                    SINCE.put(k.asLong(), level.getGameTime());
                    var bar = new net.minecraft.server.level.ServerBossEvent(Component.literal("The summoning").withStyle(ChatFormatting.DARK_PURPLE),
                            net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
                    BARS.put(k.asLong(), bar);
                }
                summoning(level, k, player);
            }
            default -> {
            }
        }
    }

    /** The idol's summoning: a bar running down; at its end, the shaman becomes what he was calling. */
    private static void summoning(ServerLevel level, BlockPos k, ServerPlayer player) {
        var bar = BARS.get(k.asLong());
        Long since = SINCE.get(k.asLong());
        java.util.List<Mob> shamans = level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(k).inflate(32),
                m -> m.isAlive() && m.getTags().contains(FOE) && m.isNoAi());
        if (shamans.isEmpty()) {
            if (bar != null) {
                bar.removeAllPlayers();
                BARS.remove(k.asLong());
            }
            return;
        }
        Mob shaman = shamans.get(0);
        // A restart forgets when it began: the summoning is then simply over.
        long elapsed = since == null ? SUMMONING_SECONDS * 20L : level.getGameTime() - since;
        if (bar != null) {
            bar.addPlayer(player);
            bar.setProgress(Math.max(0f, 1f - elapsed / (SUMMONING_SECONDS * 20f)));
        }
        level.sendParticles(ParticleTypes.WITCH, shaman.getX(), shaman.getY() + 2, shaman.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
        if (elapsed < SUMMONING_SECONDS * 20L) {
            return;
        }
        if (bar != null) {
            bar.removeAllPlayers();
            BARS.remove(k.asLong());
        }
        shaman.setNoAi(false);
        var max = shaman.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(max.getBaseValue() * 3);
            shaman.setHealth(shaman.getMaxHealth());
        }
        shaman.setCustomName(Component.literal("The Ascended Shaman").withStyle(ChatFormatting.DARK_PURPLE));
        Bosses.track(level, shaman, net.minecraft.world.BossEvent.BossBarColor.PURPLE);
        shaman.setTarget(player);
        level.playSound(null, shaman.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.2f);
        Triggers.tell(player, "Too late: the summoning is done, and the shaman has become it!");
    }

    /** The pit's guardian comes out: someone dropped in, or struck it. */
    private static void rouse(ServerLevel level, BlockPos k, ServerPlayer player) {
        for (Mob g : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(k).inflate(32),
                m -> m.isAlive() && m.getTags().contains(FOE) && m.isNoAi() && m.getType() == EntityType.ZOMBIE)) {
            g.setNoAi(false);
            if (player != null) {
                g.setTarget(player);
            }
            level.playSound(null, g.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 1f);
        }
    }

    /** Striking the pit's guardian rouses it; the channelling shaman can be struck. */
    public static void onHurt(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.getEntity() instanceof Mob m && m.getTags().contains(FOE) && m.isNoAi() && m.getType() == EntityType.ZOMBIE
                && m.level() instanceof ServerLevel level) {
            ServerPlayer p = e.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
            rouse(level, m.blockPosition(), p);
        }
    }

    private static String tag(ItemStack stack) {
        net.minecraft.nbt.CompoundTag data = ItemNbt.data(stack);
        return data != null && data.getBoolean(RELIC) ? RELIC : null;
    }

    private static Mob foe(ServerLevel level, Site site, Trigger t, EntityType<?> type, String name, BlockPos at, ServerPlayer player, double health) {
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mob.addTag(Powers.NOROLL);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        Powers.elite(mob, t.level() + 2, level.getRandom(), 1);
        var max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(max.getBaseValue() * health + 8.0 * t.level());
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
        mob.setCustomNameVisible(true);
        mob.addTag(FOE);
        mob.addTag(OF + key(site, t).asLong());
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
        if (part != Part.OBJ_RELIC && part != Part.OBJ_TOMB && part != Part.EXIT_FRAME && part != Part.OBJ_DRAGON && part != Part.OBJ_BASIN
                && part != Part.OBJ_RIM && part != Part.OBJ_GLOW) {
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
            int job = t.targets()[0][1];
            if (part == Part.EXIT_FRAME) {
                // Only there once the job is done; a click carried over from handing it in is not a request to leave.
                Long opened = OPENED.get(k.asLong());
                if (!done) {
                    return false;
                } else if (opened != null && level.getGameTime() - opened < 40) {
                    return true;
                } else {
                    surface(level, player, site);
                }
            } else if (part == Part.OBJ_BASIN && !done) {
                if (take(player, VIAL)) {
                    level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), 3);
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                    complete(level, site, t, player);
                } else {
                    Triggers.tell(player, "The basin is dry. Collect a vial from the fountain.");
                }
            } else if ((part == Part.OBJ_RIM || part == Part.OBJ_GLOW) && type == Blueprinter.FOUNTAIN && job == 1 && !done) {
                if (carries(player, VIAL)) {
                    Triggers.tell(player, "You already carry a Vial of Light. Pour it into the dry basin by the wall.");
                    return true;
                }
                if (vialAbout(level, k)) {
                    Triggers.tell(player, "The fountain has given its light: someone carries the vial, or it lies nearby.");
                    return true;
                }
                // Only one stone of the rim gives it (Sable, 2026-10-14). Every other wakes a guardian.
                if (t.targets().length > 3 && !pos.equals(at(site, t.targets()[3]))) {
                    Long last = STRUCK.get(k.asLong());
                    if (last == null || level.getGameTime() - last >= 20) {
                        STRUCK.put(k.asLong(), level.getGameTime());
                        int below = Math.min(t.level() + 1, site.built().plan().levels().size() - 1);
                        Bestiary.band(level, site, below, standAt(level, pos.above()), 1, player, ObjectiveRooms::shove);
                        level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 1f, 0.6f);
                    }
                    Triggers.tell(player, "Only cold stone, and something rises from the water! The light is in another stone of the rim.");
                    return true;
                }
                ItemStack vial = special(Items.DRAGON_BREATH, "Vial of Light", VIAL);
                if (!player.getInventory().add(vial)) {
                    level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), vial));
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
                Triggers.tell(player, "You collect a Vial of Light from the fountain. Now pour it into the dry basin by the wall.");
            } else if (part == Part.OBJ_DRAGON && type == Blueprinter.FIRECHASM && job == 1 && !done) {
                if (take(player, EYE)) {
                    level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
                    complete(level, site, t, player);
                } else {
                    Triggers.tell(player, "The dragon's eye socket is empty. The Eye-Thief has it.");
                }
            } else if (part == Part.OBJ_TOMB && type == Blueprinter.TOMB && job == 1 && !done) {
                Long last = STRUCK.get(k.asLong());
                if (last != null && level.getGameTime() - last < 20) {
                    return true; // one blow a second
                }
                STRUCK.put(k.asLong(), level.getGameTime());
                state.fire(k.above(STARTED));
                String key = "tomb:" + k.asLong();
                int blows = state.get(key) == null ? 0 : Integer.parseInt(state.get(key)) + 1;
                state.put(key, Integer.toString(blows));
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f + blows * 0.02f);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
                        pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.1);
                // Each blow calls one to three of the dead, up to twelve about the tomb (Sable, 2026-10-14).
                int about = level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(pos).inflate(16),
                        m -> m.isAlive() && m.getTags().contains(Bestiary.KIN)).size();
                int more = Math.min(1 + level.getRandom().nextInt(3), 12 - about);
                if (more > 0 && blows < TOMB_BLOWS) {
                    Bestiary.band(level, site, t.level(), standAt(level, pos.above().offset(2, 0, 0)), more, player, ObjectiveRooms::shove);
                }
                if (blows >= TOMB_BLOWS) {
                    // The sarcophagus and what lies on it.
                    for (int n = 3; n < t.targets().length; n++) {
                        BlockPos b = at(site, t.targets()[n]);
                        level.setBlock(b.above(), Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                    }
                    complete(level, site, t, player);
                } else {
                    player.displayClientMessage(Component.literal("The tomb cracks: " + (TOMB_BLOWS - blows) + " more blows.")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            } else if (part == Part.OBJ_RELIC && job == 0 && !done) {
                // One relic: it gave one a click (Sable, 2026-10-15). Again only if it is lost (burned up, say).
                if (carries(player, RELIC)) {
                    Triggers.tell(player, "You already carry the relic. Throw it into the fire (drop it into the lava).");
                    return true;
                }
                if (relicAbout(level, k)) {
                    Triggers.tell(player, "The pedestal is empty: someone carries the relic, or it lies nearby.");
                    return true;
                }
                ItemStack relic = new ItemStack(Items.HEART_OF_THE_SEA);
                ItemNbt.name(relic, Component.literal("The Relic").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(RELIC, true);
                ItemNbt.data(relic, tag);
                // Fire-proof, like netherite: dropped in lava it floats there long enough to be seen to go in. An
                // ordinary item burned up within a tick, before the once-a-second check could find it.
                // (fire-proof as an item entity in 1.20.1: onItemJoin)
                if (!player.getInventory().add(relic)) {
                    level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), relic));
                }
                Triggers.tell(player, "You take the relic. Throw it into the fire (drop it into the lava).");
            } else if (part == Part.OBJ_TOMB && job == 0 && !done && !state.hasFired(k.above(STARTED))) {
                state.fire(k.above(STARTED));
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5f, 0.5f);
                foe(level, site, t, EntityType.WITHER_SKELETON, "The Tomb Lord", pos.above(2), player, 2);
            }
            return true;
        }
        return false;
    }

    /** A foe of an objective falls: its room's job is done. The Eye-Thief drops the dragon's eye instead. */
    static void died(ServerLevel level, Mob mob) {
        if (mob.getTags().contains(THIEF)) {
            for (String tag : mob.getTags()) {
                if (tag.startsWith(OF)) {
                    CrawlState.of(level).fire(BlockPos.of(Long.parseLong(tag.substring(OF.length()))).above(THIEF_DEAD));
                }
            }
            mob.spawnAtLocation(special(Items.HEART_OF_THE_SEA, "The Dragon's Eye", EYE));
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(mob) < 24 * 24) {
                    Triggers.tell(p, "The Eye-Thief drops the dragon's eye. Set it in the dragon's head.");
                }
            }
            return;
        }
        if (!mob.getTags().contains(FOE)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        if (site == null) {
            return;
        }
        long of = Long.MIN_VALUE;
        for (String tag : mob.getTags()) {
            if (tag.startsWith(OF)) {
                of = Long.parseLong(tag.substring(OF.length()));
            }
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            // Its own room's job, wherever it fell: the ascended shaman chased Sable out of reach of the old
            // 24-block test, his job stayed open, and a new shaman rose each time he died.
            if (t.kind() == Trigger.Kind.OBJECTIVE && (of == Long.MIN_VALUE ? key(site, t).closerThan(mob.blockPosition(), 24)
                    : key(site, t).asLong() == of)) {
                ServerPlayer near = level.getNearestPlayer(mob, 32) instanceof ServerPlayer p ? p : null;
                complete(level, site, t, near);
                return;
            }
        }
    }

    /** Breaking a fountain's corrupted stone: it costs, and the fourth completes the job. */
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
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
            // The stone is cleansed, not broken: it is the fountain's rim, and a hole in it let the water out
            // (Sable, 2026-10-13). A cleansed stone holds.
            e.setCanceled(true);
            if (state.hasFired(pos.above(BROKEN))) {
                return;
            }
            state.fire(pos.above(BROKEN));
            level.setBlock(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f);
            player.hurt(level.damageSources().magic(), 4f);
            // Each stone calls its guardians, from the level below, with knockback to drive you off the rim.
            int below = Math.min(t.level() + 1, site.built().plan().levels().size() - 1);
            Bestiary.band(level, site, below, standAt(level, pos.above()), 2, player, ObjectiveRooms::shove);
            int left = 0;
            for (int k = 3; k < t.targets().length; k++) {
                left += state.hasFired(at(site, t.targets()[k]).above(BROKEN)) ? 0 : 1;
            }
            level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
            if (left == 0) {
                complete(level, site, t, player);
            } else {
                Triggers.tell(player, "The corruption bites as it is cleansed, and its guardians come! " + left + " more.");
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
        // The summoning's bar is taken down by the summoning's own tick, which stops once the job is done: so here
        // (Sable, 2026-10-15: it stayed up after the shaman died).
        var bar = BARS.remove(k.asLong());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        SINCE.remove(k.asLong());
        BlockPos frame = at(site, t.targets()[1]);
        BlockPos anchor = at(site, t.targets()[2]);
        // The way out appears now, lit, facing away from the room as a placed frame would.
        int dx = anchor.getX() - frame.getX();
        int dz = anchor.getZ() - frame.getZ();
        net.minecraft.core.Direction in = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
                : (dz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
        level.setBlock(frame, Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(EndPortalFrameBlock.FACING, in.getOpposite()).setValue(EndPortalFrameBlock.HAS_EYE, true), 3);
        OPENED.put(k.asLong(), level.getGameTime());
        BlockPos chest = standAt(level, anchor.above());
        if (level.getBlockState(chest).isAir()) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.setLootTable(level, level.getRandom(), chest,
                    new net.minecraft.resources.ResourceLocation(CrawlSpace.MODID, "chests/tier" + Math.min(5, 3 + t.level() / 2)));
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
                        + (tablets == 1 ? "" : "s") + ", and a way out opens " + WAY_OUT[t.targets()[0][0]]
                        + ": use the glowing portal frame to return to the surface.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        CrawlSpace.LOGGER.info("CrawlSpace: objective ({}) at {} done; {} tablet(s)", NAMES[t.targets()[0][0]], k, tablets);
    }

    /**
     * The nearest place to {@code near} where something can stand: air with air
     * above and something solid below, within three blocks. A chest or a band of
     * guards set where the anchor is (a pedestal, a tomb) would be inside it.
     */
    private static BlockPos standAt(ServerLevel level, BlockPos near) {
        BlockPos best = near;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-3, -2, -3), near.offset(3, 2, 3))) {
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                double d = p.distSqr(near);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /** A tomb's dead: knockback on whatever they hold (punch on a bow), and a sword with it if they hold nothing. */
    private static void shove(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = mob.getMainHandItem();
        if (held.isEmpty()) {
            held = new ItemStack(Items.STONE_SWORD);
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held);
        }
        boolean bow = held.is(Items.BOW) || held.is(Items.CROSSBOW);
        held.enchant(bow ? net.minecraft.world.item.enchantment.Enchantments.PUNCH_ARROWS
                : net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, 2);
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
    }

    /** The fountain's raised bowl, spilling light and water. */
    private static void spray(ServerLevel level, BlockPos k) {
        // The trigger sits a block above the room's floor at its middle: the bowl is a block above that.
        double x = k.getX() + 0.5;
        double y = k.getY() + 2.0;
        double z = k.getZ() + 0.5;
        level.sendParticles(ParticleTypes.FALLING_WATER, x, y, z, 12, 1.0, 0.1, 1.0, 0);
        level.sendParticles(ParticleTypes.SPLASH, x, k.getY() - 0.1, z, 10, 1.6, 0, 1.6, 0);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.6, z, 2, 0.3, 0.3, 0.3, 0.02);
    }

    /**
     * Stepping on the firechasm's bridge: a plank gives way a moment later, and
     * comes back after ten seconds. A rotten one gives way at once, unless a
     * perception mod lets the player get across it first (LegendQuest: DEX).
     */
    static void plank(ServerLevel level, ServerPlayer player, BlockPos pos, boolean rotten) {
        if (Crumbles.busy(pos)) {
            return;
        }
        if (rotten) {
            boolean quick = com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(p -> p.recovers(player, 1)).orElse(false);
            if (!quick) {
                Crumbles.start(level, java.util.List.of(pos), 1, 200, null, Crumbles.PLANKS);
                Triggers.tell(player, "The plank is rotten! It snaps under you.");
                return;
            }
            Triggers.tell(player, "The plank is rotten, but you are across it before it snaps.");
        }
        Crumbles.start(level, java.util.List.of(pos), 6, 200, null, Crumbles.PLANKS);
    }

    /**
     * Fallen into the firechasm: caught at the edge (a perception mod's DEX), or
     * into the fire and out again, burning, at the near foot of the bridge. Not
     * a death: the chasm is a barrier, and the fire is its price.
     */
    static boolean chasm(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(feet.getX() - o.getX(), feet.getY() - o.getY(), feet.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.OBJ_CHASM && Blueprint.part(code) != Part.OBJ_LAVA) {
            return false;
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.OBJECTIVE || t.targets()[0][0] != Blueprinter.FIRECHASM || t.targets().length < 4
                    || !roomOf(site, t).contains(feet.getX() - o.getX(), feet.getZ() - o.getZ())) {
                continue;
            }
            BlockPos foot = at(site, t.targets()[3]);
            boolean caught = Blueprint.part(code) == Part.OBJ_CHASM
                    && com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(p -> p.recovers(player, t.level())).orElse(false);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.resetFallDistance();
            player.teleportTo(level, foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, player.getYRot(), player.getXRot());
            if (caught) {
                Triggers.tell(player, "You catch the edge as you fall, and haul yourself back up.");
            } else {
                player.hurt(level.damageSources().lava(), 6f);
                player.setRemainingFireTicks(80);
                level.playSound(null, foot, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1f, 0.8f);
                Triggers.tell(player, "You plunge into the fire, and drag yourself out burning. The bridge will mend in a moment.");
            }
            return true;
        }
        return false;
    }

    /** The quick exit: to the ground by the entrance, north of the tower. */
    private static void surface(ServerLevel level, ServerPlayer player, Site site) {
        BlockPos o = site.origin();
        int x = o.getX();
        int z = o.getZ() - com.sablednah.crawlspace.build.Towers.REACH - 3;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.8, 0.3, 0.1);
        player.teleportTo(level, x + 0.5, y, z + 0.5, 0f, 0f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.8f);
        Triggers.tell(player, "Out, into the open air, by the dungeon's entrance.");
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void clear() {
        TOLD.clear();
        BARS.values().forEach(net.minecraft.server.level.ServerBossEvent::removeAllPlayers);
        BARS.clear();
        SINCE.clear();
        OPENED.clear();
        STRUCK.clear();
    }
}
