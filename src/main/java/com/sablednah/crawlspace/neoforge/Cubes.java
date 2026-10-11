package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The gelatinous cube (Sable's D&D night, 2026-10-12). A slime of size five,
 * nearly as wide as a corridor, with no AI of its own: it glides along its
 * corridor's centre line and back, never jumping, at a walking pace. It holds
 * what it has swallowed, visible through it: a monster or two frozen in it, and
 * bones and treasure drifting with it. Touching it burns (the slime's own
 * touch) and slows you, as if engulfed. Killed, it does not split; its
 * prisoners fall out hurt and angry, and its treasure drops.
 */
public final class Cubes {

    static final String TAG = "crawlspace_cube";
    static final String HELD = "crawlspace_engulfed";
    /** The cube's visible body: green glass, not a prisoner. */
    static final String BODY = "crawlspace_cube_body";

    private static net.minecraft.nbt.ListTag floats(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) {
            l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        }
        return l;
    }
    /** Blocks a tick: a slow walk. */
    private static final double SPEED = 0.05;

    private static final class Cube {
        final ServerLevel level;
        final UUID id;
        final int[][] beat;
        final int li;
        double at;
        int dir = 1;
        final List<UUID> held = new ArrayList<>();
        final List<double[]> offsets = new ArrayList<>();

        Cube(ServerLevel level, UUID id, int[][] beat, int li, double at) {
            this.level = level;
            this.id = id;
            this.beat = beat;
            this.li = li;
            this.at = at;
        }
    }

    private static final Map<UUID, Cube> CUBES = new HashMap<>();

    private Cubes() {
    }

    /** From Triggers' once-a-second tick: a cube's corridor near a player wakes it, once. */
    static void wake(ServerLevel level, ServerPlayer player, Site site) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || player.isCreative() || player.isSpectator()) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.CUBE) {
                continue;
            }
            BlockPos key = o.offset(t.x(), t.y(), t.z());
            if (state.hasFired(key) || !near(o, t, player.blockPosition())) {
                continue;
            }
            state.fire(key);
            spawn(level, site, t);
        }
    }

    private static boolean near(BlockPos o, Trigger t, BlockPos p) {
        for (int[] c : t.targets()) {
            if (Math.abs(o.getX() + c[0] - p.getX()) <= 20 && Math.abs(o.getZ() + c[2] - p.getZ()) <= 20 && Math.abs(o.getY() + c[1] - p.getY()) <= 5) {
                return true;
            }
        }
        return false;
    }

    private static void spawn(ServerLevel level, Site site, Trigger t) {
        int[][] beat = new int[t.targets().length][];
        BlockPos o = site.origin();
        for (int k = 0; k < beat.length; k++) {
            int[] c = t.targets()[k];
            beat[k] = new int[] {o.getX() + c[0], o.getY() + c[1], o.getZ() + c[2]};
        }
        Slime cube = EntityType.SLIME.create(level, EntitySpawnReason.EVENT);
        if (cube == null) {
            return;
        }
        int mid = beat.length / 2;
        cube.setSize(5, false);
        cube.snapTo(beat[mid][0] + 0.5, beat[mid][1], beat[mid][2] + 0.5, 0, 0);
        cube.setNoAi(true);
        // A slime's solid core hides almost everything inside it, so the slime is the cube's touch and its hits,
        // and what you see is a body of green glass the same size, through which its prisoners show.
        cube.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
        var health = cube.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(60 + 12.0 * t.level());
        }
        cube.setHealth(cube.getMaxHealth());
        cube.setCustomName(Component.literal("Gelatinous Cube").withStyle(ChatFormatting.GREEN));
        cube.setCustomNameVisible(true);
        cube.addTag(TAG);
        cube.addTag(Bestiary.KIN);
        cube.setPersistenceRequired();
        level.addFreshEntity(cube);
        Cube c = new Cube(level, cube.getUUID(), beat, t.level(), mid);
        Display.BlockDisplay body = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.EVENT);
        if (body != null) {
            double w = cube.getBbWidth();
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            net.minecraft.nbt.CompoundTag state = new net.minecraft.nbt.CompoundTag();
            state.putString("Name", "minecraft:lime_stained_glass");
            tag.put("block_state", state);
            net.minecraft.nbt.CompoundTag tf = new net.minecraft.nbt.CompoundTag();
            tf.put("left_rotation", floats(0, 0, 0, 1));
            tf.put("right_rotation", floats(0, 0, 0, 1));
            tf.put("translation", floats((float) (-w / 2), 0, (float) (-w / 2)));
            tf.put("scale", floats((float) w, (float) cube.getBbHeight(), (float) w));
            tag.put("transformation", tf);
            tag.putInt("teleport_duration", 2);
            body.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(), tag));
            body.snapTo(cube.getX(), cube.getY(), cube.getZ(), 0, 0);
            body.addTag(HELD);
            body.addTag(BODY);
            level.addFreshEntity(body);
            c.held.add(body.getUUID());
            c.offsets.add(new double[] {0, 0, 0});
        }
        // What it has swallowed: a monster or two, frozen; bones; and something worth having.
        String theme = site.built().plan().levels().get(t.level()).theme.name();
        int monsters = 1 + level.getRandom().nextInt(2);
        for (int k = 0; k < monsters; k++) {
            Entity e = Bestiary.common(theme, level.getRandom()).create(level, EntitySpawnReason.EVENT);
            if (!(e instanceof Mob m)) {
                continue;
            }
            m.snapTo(cube.getX(), cube.getY() + 0.3, cube.getZ(), level.getRandom().nextFloat() * 360f, 0);
            m.setNoAi(true);
            m.setNoGravity(true);
            m.setSilent(true);
            m.addTag(HELD);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
            c.held.add(m.getUUID());
            c.offsets.add(new double[] {(k == 0 ? -0.5 : 0.6), 0.25, (k == 0 ? 0.3 : -0.4)});
        }
        ItemStack[] things = {new ItemStack(Items.BONE), new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_SWORD)};
        double[][] spots = {{0.7, 0.4, 0.7}, {-0.8, 1.6, -0.6}, {0.2, 1.9, 0.8}, {-0.3, 0.9, -0.9}};
        for (int k = 0; k < things.length; k++) {
            Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
            if (d == null) {
                continue;
            }
            d.getSlot(0).set(things[k]);
            d.snapTo(cube.getX(), cube.getY() + 1, cube.getZ(), level.getRandom().nextFloat() * 360f, 0);
            d.addTag(HELD);
            level.addFreshEntity(d);
            c.held.add(d.getUUID());
            c.offsets.add(spots[k]);
        }
        CUBES.put(cube.getUUID(), c);
        level.playSound(null, cube.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2f, 0.4f);
        CrawlSpace.LOGGER.info("CrawlSpace: a gelatinous cube on a beat of {} cells at {}", beat.length, cube.blockPosition());
    }

    /** Every tick: each cube a step along its beat, its contents with it; now and then, whoever it engulfs. */
    public static void tick(long gameTime) {
        if (CUBES.isEmpty()) {
            return;
        }
        for (Cube c : new ArrayList<>(CUBES.values())) {
            Entity e = c.level.getEntity(c.id);
            if (!(e instanceof Slime cube) || !cube.isAlive()) {
                if (e == null) {
                    continue; // not loaded: it keeps its place in the list until its chunk returns or it dies
                }
                CUBES.remove(c.id);
                continue;
            }
            c.at += c.dir * SPEED;
            if (c.at >= c.beat.length - 1) {
                c.at = c.beat.length - 1;
                c.dir = -1;
            } else if (c.at <= 0) {
                c.at = 0;
                c.dir = 1;
            }
            int k = (int) Math.floor(c.at);
            int n = Math.min(k + 1, c.beat.length - 1);
            double f = c.at - k;
            double x = c.beat[k][0] + (c.beat[n][0] - c.beat[k][0]) * f + 0.5;
            double y = c.beat[k][1] + (c.beat[n][1] - c.beat[k][1]) * f;
            double z = c.beat[k][2] + (c.beat[n][2] - c.beat[k][2]) * f + 0.5;
            float yaw = (float) Math.toDegrees(Math.atan2(-(c.beat[n][0] - c.beat[k][0]) * c.dir, (c.beat[n][2] - c.beat[k][2]) * c.dir));
            cube.snapTo(x, y, z, yaw, 0);
            cube.setYHeadRot(yaw);
            for (int h = 0; h < c.held.size(); h++) {
                Entity held = c.level.getEntity(c.held.get(h));
                if (held != null) {
                    double[] off = c.offsets.get(h);
                    // The body stays square to the corridor; what it holds turns slowly as it drifts.
                    float turn = held.getTags().contains(BODY) ? 0 : held.getYRot() + 0.5f;
                    held.snapTo(x + off[0], y + off[1], z + off[2], turn, 0);
                }
            }
            if (gameTime % 10 == 0) {
                for (ServerPlayer p : c.level.players()) {
                    if (!p.isSpectator() && !p.isCreative() && p.getBoundingBox().intersects(cube.getBoundingBox())) {
                        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
                        p.displayClientMessage(Component.literal("The cube engulfs you! Its acid burns.").withStyle(ChatFormatting.GREEN), true);
                    }
                }
            }
        }
    }

    /**
     * A cube loaded back in after a restart: its beat is found again from the
     * blueprint, and whatever it had swallowed nearby is gathered back into it.
     * Without this it would stand frozen where the server stopped.
     */
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Slime cube) || !cube.getTags().contains(TAG)
                || CUBES.containsKey(cube.getUUID())) {
            return;
        }
        // The chunk is still loading: look for the beat and its contents a tick later.
        Crumbles.later(level, 1, () -> {
            Site site = Dungeons.at(level, cube.blockPosition()).orElse(null);
            if (site == null || !cube.isAlive()) {
                return;
            }
            BlockPos o = site.origin();
            for (Trigger t : site.built().blueprint().triggers()) {
                if (t.kind() != Trigger.Kind.CUBE || !near(o, t, cube.blockPosition())) {
                    continue;
                }
                int[][] beat = new int[t.targets().length][];
                int closest = 0;
                for (int k = 0; k < beat.length; k++) {
                    int[] c = t.targets()[k];
                    beat[k] = new int[] {o.getX() + c[0], o.getY() + c[1], o.getZ() + c[2]};
                    if (cube.distanceToSqr(beat[k][0] + 0.5, beat[k][1], beat[k][2] + 0.5)
                            < cube.distanceToSqr(beat[closest][0] + 0.5, beat[closest][1], beat[closest][2] + 0.5)) {
                        closest = k;
                    }
                }
                Cube c = new Cube(level, cube.getUUID(), beat, t.level(), closest);
                for (Entity held : level.getEntitiesOfClass(Entity.class, cube.getBoundingBox().inflate(1.5), h -> h.getTags().contains(HELD))) {
                    c.held.add(held.getUUID());
                    c.offsets.add(new double[] {held.getX() - cube.getX(), held.getY() - cube.getY(), held.getZ() - cube.getZ()});
                }
                CUBES.put(cube.getUUID(), c);
                return;
            }
        });
    }

    /** A cube never splits. */
    public static void onSplit(net.neoforged.neoforge.event.entity.living.MobSplitEvent e) {
        if (e.getParent().getTags().contains(TAG)) {
            e.setCanceled(true);
        }
    }

    /** Things it holds take no harm until it dies. */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (e.getEntity().getTags().contains(HELD)) {
            e.setCanceled(true);
        }
    }

    /** A cube dies: its prisoners fall out hurt and angry, its treasure spills, and the rest dissolves. */
    static void died(ServerLevel level, Mob mob) {
        Cube c = CUBES.remove(mob.getUUID());
        if (c == null || !mob.getTags().contains(TAG)) {
            return;
        }
        ServerPlayer near = mob.level().getNearestPlayer(mob, 16) instanceof ServerPlayer p ? p : null;
        for (UUID id : c.held) {
            Entity held = level.getEntity(id);
            if (held instanceof Mob m) {
                m.removeTag(HELD);
                m.setNoAi(false);
                m.setNoGravity(false);
                m.setSilent(false);
                m.setHealth(Math.max(1f, m.getMaxHealth() * 0.5f));
                m.addTag(Bestiary.KIN);
                if (near != null) {
                    m.setTarget(near);
                }
            } else if (held != null && held.getTags().contains(BODY)) {
                held.discard();
            } else if (held instanceof Display.ItemDisplay d) {
                ItemStack s = d.getSlot(0).get().copy();
                if (!s.isEmpty()) {
                    mob.spawnAtLocation(level, s);
                }
                d.discard();
            }
        }
        var loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 2 + c.li / 2))));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for (ItemStack s : loot.getRandomItems(params)) {
            mob.spawnAtLocation(level, s);
        }
        level.sendParticles(ParticleTypes.ITEM_SLIME, mob.getX(), mob.getY() + 1, mob.getZ(), 60, 1.2, 1, 1.2, 0.1);
        level.playSound(null, mob.blockPosition(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 2f, 0.4f);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) < 24 * 24) {
                Triggers.tell(p, "The cube collapses. What it held spills out, and some of it is still alive!");
            }
        }
    }

    public static void clear() {
        CUBES.clear();
    }
}
