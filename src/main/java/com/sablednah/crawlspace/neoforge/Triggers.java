package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Watches the dungeon's triggers: ordinary blocks and floor tiles, so a lever
 * needs no redstone behind it and a vanilla client needs nothing installed.
 * Levers open their level's locked doors; using a secret wall opens it; a
 * hidden tile springs its trap once.
 */
public final class Triggers {

    /** How often a player's dungeon is looked up again, in ticks. */
    private static final int RECHECK = 40;

    private record Here(Site site, long until) {
    }

    private static final Map<UUID, Here> HERE = new HashMap<>();

    private Triggers() {
    }

    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null) {
            return;
        }
        // The trap's floor tile, or something standing on the trap's own cell (a corridor stair, a rug).
        if (player.isShiftKeyDown() && (disarm(level, player, site, pos.above()) || disarm(level, player, site, pos))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        int code = site.built().blueprint().get(pos.getX() - site.origin().getX(), pos.getY() - site.origin().getY(),
                pos.getZ() - site.origin().getZ());
        if (code != 0 && com.sablednah.crawlspace.build.Blueprint.part(code) == com.sablednah.crawlspace.build.Part.HOARD_CHEST
                && !player.isSpectator() && inPuzzleRoom(site, pos)) {
            Powers.bless(level, player, pos);
        }
        Trigger bars = portcullisAt(site, pos);
        if (bars != null) {
            usePortcullis(level, player, site, bars, e.getItemStack());
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        Trigger t = triggerAt(site, pos);
        if (t == null) {
            if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.IRON_BARS) && droppedAt(site, pos)) {
                tell(player, "It will not budge. Somewhere in the room, a winch raises it.");
            } else if (level.getBlockState(pos).getBlock() instanceof DoorBlock door && onewayDoor(site, pos)
                    && !door.isOpen(level.getBlockState(pos))) {
                tell(player, "It will not open from this side.");
            }
            return;
        }
        switch (t.kind()) {
            case ONEWAY -> {
                BlockState lever = level.getBlockState(pos);
                if (!(lever.getBlock() instanceof LeverBlock)) {
                    return;
                }
                boolean open = !lever.getValue(LeverBlock.POWERED);
                int[] target = t.targets()[0];
                BlockPos door = site.origin().offset(target[0], target[1], target[2]);
                BlockState ds = level.getBlockState(door);
                if (ds.getBlock() instanceof DoorBlock db && db.isOpen(ds) != open) {
                    db.setOpen(null, level, ds, door, open);
                    tell(player, open ? "The iron door swings open: a way back." : "The iron door swings shut.");
                }
            }
            case WINCH -> {
                if (level.getBlockState(site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]))
                        .is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
                    raise(level, site, t.targets());
                    tell(player, "The winch creaks round: the portcullis rises.");
                }
            }
            case LEVER -> {
                BlockState lever = level.getBlockState(pos);
                if (!(lever.getBlock() instanceof LeverBlock)) {
                    return;
                }
                // The event comes before the lever flips, so the doors follow the state it is about to take.
                boolean open = !lever.getValue(LeverBlock.POWERED);
                int moved = 0;
                for (int[] target : t.targets()) {
                    BlockPos door = site.origin().offset(target[0], target[1], target[2]);
                    BlockState ds = level.getBlockState(door);
                    if (ds.getBlock() instanceof DoorBlock db && db.isOpen(ds) != open) {
                        db.setOpen(null, level, ds, door, open);
                        moved++;
                    }
                }
                if (moved > 0) {
                    tell(player, open ? "Somewhere on this level, an iron door grinds open."
                            : "Somewhere on this level, an iron door slams shut.");
                }
            }
            case SECRET -> {
                CrawlState state = CrawlState.of(level);
                if (state.hasFired(pos)) {
                    return;
                }
                for (int[] target : t.targets()) {
                    BlockPos wall = site.origin().offset(target[0], target[1], target[2]);
                    level.destroyBlock(wall, false);
                    state.fire(wall);
                }
                tell(player, "The wall gives way: a secret door!");
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            default -> {
            }
        }
    }

    /** Whether {@code pos} is inside one of the dungeon's puzzle rooms. */
    private static boolean inPuzzleRoom(Site site, BlockPos pos) {
        int li = Arrivals.levelAt(site, pos);
        if (li < 0) {
            return false;
        }
        com.sablednah.crawlspace.plan.LevelPlan lp = site.built().plan().levels().get(li);
        com.sablednah.crawlspace.plan.Room r = lp.room(lp.region(pos.getX() - site.origin().getX(), pos.getZ() - site.origin().getZ()));
        return r != null && r.role == com.sablednah.crawlspace.plan.Role.PUZZLE;
    }

    /** The keyed portcullis whose bars are at {@code pos}: its trigger is on the lower bar. */
    private static Trigger portcullisAt(Site site, BlockPos pos) {
        Trigger t = triggerAt(site, pos);
        if (t != null && t.kind() == Trigger.Kind.PORTCULLIS) {
            return t;
        }
        t = triggerAt(site, pos.below());
        return t != null && t.kind() == Trigger.Kind.PORTCULLIS ? t : null;
    }

    /** Whether {@code pos} is either half of a one-way door. */
    private static boolean onewayDoor(Site site, BlockPos pos) {
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.ONEWAY) {
                BlockPos lower = o.offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
                if (lower.equals(pos) || lower.above().equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code pos} is one of the bars of a portcullis that drops behind you. */
    private static boolean droppedAt(Site site, BlockPos pos) {
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.PORTCULLIS_TRAP) {
                for (int[] b : t.targets()) {
                    if (o.offset(b[0], b[1], b[2]).equals(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Using a locked portcullis: with this level's key in hand it rises and the
     * key is spent; with another key, the game says whose it is; with none, it
     * names where the key is.
     */
    private static void usePortcullis(ServerLevel level, ServerPlayer player, Site site, Trigger t, ItemStack held) {
        BlockPos lower = site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
        if (!level.getBlockState(lower).is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
            return;
        }
        String id = Keys.idOf(held);
        if (Keys.id(site, t.level()).equals(id)) {
            if (!player.isCreative()) {
                held.shrink(1);
            }
            raise(level, site, t.targets());
            tell(player, "The key turns. The portcullis grinds up.");
        } else if (id != null && id.startsWith(Keys.id(site, 0).substring(0, Keys.id(site, 0).lastIndexOf('/') + 1))) {
            tell(player, "That key is for the portcullis on level " + (Keys.levelOf(id) + 1) + ".");
        } else if (id != null) {
            tell(player, "That key belongs to another dungeon.");
        } else {
            tell(player, "Locked. Its key is in a chest somewhere on this level.");
        }
    }

    /** A portcullis rises: its bars go from the bottom up, a few ticks apart. */
    private static void raise(ServerLevel level, Site site, int[][] bars) {
        BlockPos o = site.origin();
        BlockPos first = o.offset(bars[0][0], bars[0][1], bars[0][2]);
        level.playSound(null, first, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1f, 0.6f);
        level.playSound(null, first, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.7f);
        for (int k = 0; k < bars.length; k++) {
            BlockPos p = o.offset(bars[k][0], bars[k][1], bars[k][2]);
            Crumbles.later(level, 1 + 6 * k, () -> {
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    level.playSound(null, p, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 0.8f);
                }
            });
        }
    }

    /** How long a dropped portcullis stays down before its counterweight lifts it anyway, in ticks. */
    private static final int PORTCULLIS_RESET = 2400;

    /**
     * The portcullis slams down over the archway behind {@code player}. Anyone
     * standing in the archway is pushed into the room first, never into the
     * bars. It names the way out as it falls, and lifts itself in the end.
     */
    private static void drop(ServerLevel level, Site site, Trigger t, ServerPlayer player) {
        BlockPos o = site.origin();
        BlockPos inside = o.offset(t.x(), t.y(), t.z());
        java.util.List<BlockPos> bars = new java.util.ArrayList<>();
        for (int[] b : t.targets()) {
            bars.add(o.offset(b[0], b[1], b[2]));
        }
        for (net.minecraft.world.entity.LivingEntity mob : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(bars.get(0)).expandTowards(0, 2, 0))) {
            mob.teleportTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        }
        for (BlockPos p : bars) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        for (BlockPos p : bars) {
            level.setBlock(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
        }
        level.playSound(null, bars.get(0), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.5f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, bars.get(0).getX() + 0.5, bars.get(0).getY() + 0.2,
                bars.get(0).getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.02);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(inside, 12)) {
                tell(p, "Clang! A portcullis slams down behind you. A winch in this room raises it.");
            }
        }
        Crumbles.later(level, PORTCULLIS_RESET, () -> {
            if (level.getBlockState(bars.get(0)).is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
                raise(level, site, t.targets());
            }
        });
    }

    /**
     * Sneak-using the floor tile of a trap you know about, or its plate or
     * wire: try to disarm it.
     */
    private static boolean disarm(ServerLevel level, ServerPlayer player, Site site, BlockPos trapPos) {
        Trigger t = triggerAt(site, trapPos);
        if (t != null && t.kind() == Trigger.Kind.PIT_EDGE) {
            trapPos = site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
            t = triggerAt(site, trapPos);
        }
        if (t == null || !t.kind().isTrap()) {
            return false;
        }
        if (CrawlState.of(level).hasFired(trapPos) || !known(player, trapPos)) {
            return false;
        }
        attempt(level, player, t, trapPos);
        return true;
    }

    /**
     * One go at disarming: the perception mod's roll, or without one the
     * configured chance. Success takes the plate or wire away; failure springs
     * the trap on whoever tried. Either way it is used up.
     */
    private static void attempt(ServerLevel level, ServerPlayer player, Trigger t, BlockPos trapPos) {
        boolean ok = com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(p -> p.disarms(player, t.level()))
                .orElseGet(() -> level.getRandom().nextDouble() < CrawlConfig.disarmChance());
        CrawlState.of(level).fire(trapPos);
        if (ok) {
            if (isTrapBlock(level.getBlockState(trapPos))) {
                level.removeBlock(trapPos, false);
            }
            level.playSound(null, trapPos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 1f, 0.8f);
            tell(player, t.kind() == Trigger.Kind.PIT ? "You wedge the loose stones: this floor will hold now."
                    : t.kind() == Trigger.Kind.PORTCULLIS_TRAP ? "You jam the portcullis's chain: it will not fall."
                    : "You disarm the " + com.sablednah.crawlspace.plan.TrapKind.valueOf(t.kind().name()).description + ".");
        } else if (t.kind() != Trigger.Kind.PORTCULLIS_TRAP && t.kind() != Trigger.Kind.PIT
                && t.kind() != Trigger.Kind.DARTS && t.kind() != Trigger.Kind.GAS) {
            Dungeons.at(level, trapPos).ifPresent(site -> spring(level, site, t, player, trapPos));
        } else if (t.kind() == Trigger.Kind.PORTCULLIS_TRAP) {
            Dungeons.at(level, trapPos).ifPresent(site -> drop(level, site, t, player));
            tell(player, "You set it off! The portcullis slams down!");
        } else if (t.kind() == Trigger.Kind.PIT) {
            Dungeons.at(level, trapPos).ifPresent(site -> pitfall(level, site, t));
            tell(player, "You set it off! The floor cracks under your feet!");
        } else if (t.kind() == Trigger.Kind.DARTS) {
            darts(level, player, trapPos);
            tell(player, "You set it off! Darts fly from the wall!");
        } else {
            gas(level, player, trapPos);
            tell(player, "You set it off! Poison gas seeps from the floor: step out of the cloud!");
        }
    }

    /** The newer traps (darts, gas, pits and portcullises have their own): each says what happened as it happens. */
    private static void spring(ServerLevel level, Site site, Trigger t, ServerPlayer player, BlockPos at) {
        switch (t.kind()) {
            case ALARM -> alarm(level, site, t, player);
            case WEBS -> webs(level, player);
            case ROCKFALL -> rockfall(level, player);
            case FROST -> {
                player.setTicksFrozen(player.getTicksRequiredToFreeze() + 160);
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(),
                        60, 1.2, 0.8, 1.2, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.BLOCKS, 1.5f, 0.6f);
                tell(player, "A freezing mist rolls over you! Keep moving.");
            }
            case FIRE -> {
                player.igniteForSeconds(4);
                BlockPos feet = player.blockPosition();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos p = feet.relative(d);
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                        level.setBlock(p, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
                level.playSound(null, feet, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.8f);
                tell(player, "Flames roar up from the floor!");
            }
            case SUMMON -> {
                int n = Bestiary.ambush(level, site, t.level(), player);
                level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
                tell(player, n > 0 ? "Hidden panels grind open: an ambush!" : "Something grinds in the walls, and nothing comes.");
            }
            default -> darts(level, player, at);
        }
    }

    /** For /crawlspace trap: springs a trap of {@code kind} where the player stands, as if they had stepped on one. */
    static boolean springHere(ServerLevel level, ServerPlayer player, Trigger.Kind kind) {
        Site site = Dungeons.at(level, player.blockPosition()).orElse(null);
        int li = site == null ? -1 : Arrivals.levelAt(site, player.blockPosition());
        if (li < 0) {
            return false;
        }
        BlockPos rel = player.blockPosition().subtract(site.origin());
        Trigger t = new Trigger(kind, rel.getX(), rel.getY(), rel.getZ(), li, new int[0][]);
        switch (kind) {
            case DARTS -> darts(level, player, player.blockPosition());
            case GAS -> gas(level, player, player.blockPosition());
            default -> spring(level, site, t, player, player.blockPosition());
        }
        return true;
    }

    /** An alarm: a bell, and every sleeping room within 24 blocks of it on this level wakes, its monsters set on you. */
    private static void alarm(ServerLevel level, Site site, Trigger t, ServerPlayer player) {
        BlockPos o = site.origin();
        BlockPos at = o.offset(t.x(), t.y(), t.z());
        level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3f, 0.8f);
        level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2f, 1f);
        CrawlState state = CrawlState.of(level);
        int woke = 0;
        for (Trigger e : site.built().blueprint().triggers()) {
            BlockPos p = o.offset(e.x(), e.y(), e.z());
            if (!e.kind().isEncounter() || e.level() != t.level() || state.hasFired(p) || p.distSqr(at) > 24 * 24) {
                continue;
            }
            state.fire(p);
            if (Bestiary.wake(level, site, e) != null) {
                woke++;
            }
        }
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(28), m -> m.getTags().contains(Bestiary.KIN) && m.getTarget() == null)) {
            m.setTarget(player);
        }
        tell(player, woke > 0 ? "A bell clangs! The rooms around you wake." : "A bell clangs, and echoes. Nothing answers... yet.");
    }

    /** Cobwebs burst round you, and you are slowed. */
    private static void webs(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0 || dy == 0) && level.getBlockState(p).isAir() && level.getRandom().nextFloat() < 0.6f) {
                        level.setBlock(p, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3);
                    }
                }
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
        level.playSound(null, feet, SoundEvents.SPIDER_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.6f);
        tell(player, "Sticky webs burst from the walls! Cut your way out.");
    }

    /** Stalactites drop from the ceiling on to you and round you. */
    private static void rockfall(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        int dropped = 0;
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPos column = feet.offset(d[0], 0, d[1]);
            // The first air under the ceiling, up to six above.
            BlockPos top = null;
            for (int y = 2; y <= 6; y++) {
                if (!level.getBlockState(column.above(y)).isAir()) {
                    top = column.above(y - 1);
                    break;
                }
            }
            if (top == null || !level.getBlockState(top).isAir() || (d[0] != 0 || d[1] != 0) && level.getRandom().nextBoolean()) {
                continue;
            }
            net.minecraft.world.entity.item.FallingBlockEntity f = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, top,
                    net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
            f.dropItem = false;
            f.setHurtsEntities(2f, 12);
            dropped++;
        }
        level.playSound(null, feet, SoundEvents.POINTED_DRIPSTONE_FALL, SoundSource.BLOCKS, 1.5f, 0.8f);
        if (dropped == 0) {
            player.hurt(level.damageSources().fallingBlock(player), 4f);
        }
        tell(player, "The ceiling cracks: stalactites fall!");
    }

    private static boolean isTrapBlock(net.minecraft.world.level.block.state.BlockState st) {
        return st.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock
                || st.getBlock() instanceof net.minecraft.world.level.block.TripWireBlock;
    }

    /**
     * Breaking a real trap's plate or wire is a try at disarming it, never a
     * free way round it; breaking a decoy says so. Creative players break
     * things as usual.
     */
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || !isTrapBlock(e.getState())) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null) {
            return;
        }
        Trigger t = triggerAt(site, pos);
        if (t == null) {
            return;
        }
        if (t.kind() == Trigger.Kind.DECOY) {
            tell(player, "Nothing happens: it was a decoy.");
            return;
        }
        if (!t.kind().isTrap() || CrawlState.of(level).hasFired(pos)) {
            return;
        }
        e.setCanceled(true);
        level.removeBlock(pos, false);
        attempt(level, player, t, pos);
    }

    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 2 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos feet = player.blockPosition();
        BlockPos prev = PREV.put(player.getUUID(), feet);
        Here here = HERE.get(player.getUUID());
        long now = level.getGameTime();
        if (here == null || now >= here.until() || (here.site() != null && !here.site().contains(feet))) {
            here = new Here(Dungeons.at(level, feet).orElse(null), now + RECHECK);
            HERE.put(player.getUUID(), here);
        }
        Protection.tick(level, player, here.site(), feet);
        if (here.site() == null) {
            return;
        }
        if (player.tickCount % 20 == 0 && here.site().planner() == Site.PLANNER_VERSION) {
            Arrivals.tick(level, player, here.site(), feet);
        }
        if (player.tickCount % 20 == 0) {
            final Site site = here.site();
            if (CrawlConfig.hints()) {
                hints(level, player, site, null);
            } else {
                com.sablednah.crawlspace.api.CrawlSpaceApi.perception().ifPresent(p -> {
                    notice(level, player, site, p);
                    hints(level, player, site, NOTICED.getOrDefault(player.getUUID(), java.util.Map.of()));
                });
            }
        }
        if (player.tickCount % 10 == 0 && !player.isCreative()) {
            wake(level, player, here.site());
        }
        if (!player.isCreative() && puzzle(level, player, here.site(), feet)) {
            return;
        }
        // Hints show however you move; traps want a foot on the tile.
        if (!player.onGround()) {
            return;
        }
        Trigger t = triggerAt(here.site(), feet);
        if (t != null && t.kind() == Trigger.Kind.PIT_EDGE) {
            t = triggerAt(here.site(), here.site().origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]));
        }
        if (t == null || !t.kind().isTrap()) {
            return;
        }
        BlockPos at = here.site().origin().offset(t.x(), t.y(), t.z());
        CrawlState state = CrawlState.of(level);
        if (state.hasFired(at)) {
            return;
        }
        if (t.kind() == Trigger.Kind.PORTCULLIS_TRAP && !walkingIn(here.site(), t, prev, feet)) {
            return; // leaving the room by the arch: it falls only behind someone going in
        }
        state.fire(at);
        switch (t.kind()) {
            case DARTS -> darts(level, player, feet);
            case GAS -> gas(level, player, feet);
            case PORTCULLIS_TRAP -> drop(level, here.site(), t, player);
            case PIT -> pitfall(level, here.site(), t);
            default -> spring(level, here.site(), t, player, feet);
        }
    }

    /** Per player: where their feet were at the last look, to tell going into a room from coming out. */
    private static final Map<UUID, BlockPos> PREV = new HashMap<>();

    /** Whether a player on a portcullis trap's tile got there from the archway's side: closer to it a moment ago. */
    private static boolean walkingIn(Site site, Trigger t, BlockPos prev, BlockPos feet) {
        if (prev == null) {
            return false;
        }
        BlockPos door = site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
        int before = Math.max(Math.abs(prev.getX() - door.getX()), Math.abs(prev.getZ() - door.getZ()));
        int now = Math.max(Math.abs(feet.getX() - door.getX()), Math.abs(feet.getZ() - door.getZ()));
        return before < now;
    }

    /** How near a room's monsters wake, in blocks. */
    private static final int WAKE_RANGE = 10;

    /**
     * Wakes any room whose monsters have not appeared yet once a player comes
     * near. Creative players are ignored, so building and testing does not use
     * a room up. On peaceful nothing wakes, and the room stays asleep for when
     * the difficulty changes.
     */
    private static void wake(ServerLevel level, ServerPlayer player, Site site) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (!t.kind().isEncounter()) {
                continue;
            }
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (Math.abs(p.getX() - player.getX()) > WAKE_RANGE || Math.abs(p.getZ() - player.getZ()) > WAKE_RANGE
                    || Math.abs(p.getY() - player.getY()) > 5 || state.hasFired(p)) {
                continue;
            }
            state.fire(p);
            String message = Bestiary.wake(level, site, t);
            if (message != null) {
                tell(player, message);
            }
        }
    }

    private static final DustParticleOptions RED = new DustParticleOptions(0xD8322A, 0.9f);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x3FD24A, 0.9f);
    /** How near a hint has to be before it shows, in blocks. */
    private static final int HINT_RANGE = 9;

    /**
     * Faint dust, sent to this player only, near the things a perception skill
     * would find: red over an unsprung trap, green at a secret wall not yet
     * opened and over a treasure room's hoard.
     */
    /** Per player: hidden things already asked about, and whether they were noticed. Memory only: a restart re-rolls. */
    private static final Map<UUID, Map<Long, Boolean>> NOTICED = new HashMap<>();
    /** How near a hidden thing has to be before a player gets a chance to notice it. */
    private static final int NOTICE_RANGE = 5;

    /**
     * Asks the registered perception about each hidden thing a player has
     * just come near: once per player per thing, so walking past again does
     * not reroll. What they notice they are told about, and shown from then on.
     */
    private static void notice(ServerLevel level, ServerPlayer player, Site site, com.sablednah.crawlspace.api.Perception perception) {
        CrawlState state = CrawlState.of(level);
        Map<Long, Boolean> asked = NOTICED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            boolean trap = t.kind().isTrap();
            boolean secret = t.kind() == Trigger.Kind.SECRET && t.targets().length > 0 && t.targets()[0][1] == t.y();
            if (!trap && !secret) {
                continue;
            }
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (asked.containsKey(p.asLong()) || state.hasFired(p) || p.distSqr(player.blockPosition()) > NOTICE_RANGE * NOTICE_RANGE) {
                continue;
            }
            boolean seen = perception.notices(player, trap ? com.sablednah.crawlspace.api.Perception.Hidden.TRAP
                    : com.sablednah.crawlspace.api.Perception.Hidden.SECRET_DOOR, t.level());
            asked.put(p.asLong(), seen);
            if (seen) {
                String found = trap ? reveal(level, site, t, p) : null;
                tell(player, !trap ? "Something about this wall is not right..."
                        : t.kind() == Trigger.Kind.PORTCULLIS_TRAP
                        ? "You notice deep grooves under the arch ahead: a portcullis hangs over it. Sneak and use the floor before it to jam it."
                        : found == null ? "You notice a trap in the floor ahead. Sneak and use it to disarm it."
                        : "You spot " + found + " ahead: a trap. Break it, or sneak and use it, to try to disarm it.");
            }
        }
    }

    /**
     * A noticed trap's plate or wire appears in the world, for everyone, once
     * somebody has spotted it. Returns what it is ("a pressure plate"), or null
     * for a trap with nothing to show (one on a step).
     */
    private static String reveal(ServerLevel level, Site site, Trigger t, BlockPos p) {
        int code = site.built().blueprint().get(t.x(), t.y(), t.z());
        if (code == 0) {
            return null;
        }
        com.sablednah.crawlspace.build.Part part = com.sablednah.crawlspace.build.Blueprint.part(code);
        boolean wire = part == com.sablednah.crawlspace.build.Part.TRAP_WIRE;
        if (!wire && part != com.sablednah.crawlspace.build.Part.TRAP_PLATE) {
            return null;
        }
        if (level.getBlockState(p).isAir()) {
            String theme = site.built().plan().levels().get(t.level()).theme.name();
            level.setBlock(p, Palettes.trapBlock(theme, wire), 3);
        }
        return wire ? "a tripwire" : "a pressure plate";
    }

    /** Whether this player knows about the hidden thing at {@code pos}: hints show all, else only what they noticed. */
    private static boolean known(ServerPlayer player, BlockPos pos) {
        if (CrawlConfig.hints() || CrawlConfig.trapsVisible()) {
            return true;
        }
        Boolean seen = NOTICED.getOrDefault(player.getUUID(), Map.of()).get(pos.asLong());
        return seen != null && seen;
    }

    /**
     * @param noticed null to show every hint (hints on), else only the hidden
     *                things this player noticed (treasure is not hidden, and
     *                with a perception mod in charge it is not pointed out)
     */
    private static void hints(ServerLevel level, ServerPlayer player, Site site, Map<Long, Boolean> noticed) {
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (Math.abs(p.getX() - player.getX()) > HINT_RANGE || Math.abs(p.getZ() - player.getZ()) > HINT_RANGE
                    || Math.abs(p.getY() - player.getY()) > 4) {
                continue;
            }
            if (noticed != null && !Boolean.TRUE.equals(noticed.get(p.asLong()))) {
                continue;
            }
            switch (t.kind()) {
                case DARTS, GAS, PORTCULLIS_TRAP, ALARM, WEBS, ROCKFALL, FROST, FIRE, SUMMON -> {
                    if (!state.hasFired(p)) {
                        level.sendParticles(player, RED, false, false, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 3, 0.3, 0.02, 0.3, 0);
                    }
                }
                case SECRET -> {
                    // One puff per doorway, from its lower block, spilling out of the wall's faces.
                    if (t.targets().length > 0 && t.targets()[0][1] == t.y() && !state.hasFired(p)) {
                        level.sendParticles(player, GREEN, false, false, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0);
                    }
                }
                case TREASURE -> level.sendParticles(player, GREEN, false, false, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0);
                default -> {
                }
            }
        }
    }

    /**
     * No natural monster spawns inside a dungeon built by command: its rooms'
     * own monsters are the danger, and dark rooms otherwise fill with whatever
     * the night brings (five creepers once, which blew the lair's boss half to
     * death before anyone arrived). Generated dungeons get the same through
     * their structure's spawn_overrides. Spawners are untouched.
     */
    public static void onSpawnCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getSpawnType() != net.minecraft.world.entity.EntitySpawnReason.NATURAL
                || e.getEntityType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = e.getLevel().getLevel();
        for (Site s : CrawlState.of(level).built()) {
            if (s.contains(e.getPos())) {
                e.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
                return;
            }
        }
    }

    /** Per player: the last puzzle-room path block they stood on, for a caught stumble. */
    private static final Map<UUID, BlockPos> LAST_SAFE = new HashMap<>();

    /**
     * Puzzle rooms: a foot on the void (in it, or on it if a datapack made it
     * solid) sends the player back. A perception mod may let them catch
     * themselves, and they are put on the last path block they stood on;
     * otherwise, or without one, they go to the restart block at the centre.
     * Returns true when it moved them.
     */
    private static boolean puzzle(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        BlockPos o = site.origin();
        com.sablednah.crawlspace.build.Blueprint bp = site.built().blueprint();
        int fx = feet.getX() - o.getX();
        int fy = feet.getY() - o.getY();
        int fz = feet.getZ() - o.getZ();
        int below = bp.get(fx, fy - 1, fz);
        com.sablednah.crawlspace.build.Part under = below == 0 ? null : com.sablednah.crawlspace.build.Blueprint.part(below);
        if (player.onGround() && under != null) {
            if (under == com.sablednah.crawlspace.build.Part.CRUMBLE && !level.getBlockState(feet.below()).isAir()) {
                Crumbles.start(level, java.util.List.of(feet.below()), 5, 100, null);
            }
            if (!unsafe(under)) {
                LAST_SAFE.put(player.getUUID(), feet);
            }
        }
        Trigger room = nearestPuzzle(site, fx, fy, fz);
        // Fallen: in the void, on it, or below the paths of a puzzle room (through a crumbled block,
        // off a tipped dripleaf, or into a leap of faith's sunken void).
        // In the void, or below the paths: never just standing near an edge with your centre over the
        // next column. Standing ON it counts only where a datapack made the void a solid block.
        boolean fell = isVoid(bp.get(fx, fy, fz))
                || isVoid(below) && !level.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.END_PORTAL)
                // By height, not block: a dripleaf's top is a sixteenth below the path, so standing on one
                // put the feet in the layer below, and that sent Sable back before the leaf ever tipped.
                || room != null && player.getY() < o.getY() + room.y() - 0.5
                        && fx >= room.targets()[0][0] && fx <= room.targets()[1][0]
                        && fz >= room.targets()[0][2] && fz <= room.targets()[1][2];
        if (!fell) {
            return false;
        }
        Trigger centre = room;
        if (centre == null) {
            return false;
        }
        BlockPos safe = LAST_SAFE.get(player.getUUID());
        boolean caught = safe != null && safe.distSqr(feet) <= 9
                && com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(p -> p.recovers(player, centre.level())).orElse(false);
        BlockPos to = caught ? safe : o.offset(centre.x(), centre.y(), centre.z());
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.2);
        level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, caught ? 1.4f : 0.8f);
        tell(player, caught ? "You catch yourself at the edge of the void."
                : "The void throws you back to the centre. Find the path to a door, or to the chest.");
        return true;
    }

    private static boolean isVoid(int code) {
        return code != 0 && com.sablednah.crawlspace.build.Blueprint.part(code) == com.sablednah.crawlspace.build.Part.VOID;
    }

    /** Floor that is no place to be put back on: it is about to go, or already has. */
    private static boolean unsafe(com.sablednah.crawlspace.build.Part p) {
        return switch (p) {
            case VOID, AIR, CRUMBLE, DRIPLEAF, PIT_TILE -> true;
            default -> false;
        };
    }

    /** The restart point of the puzzle room around a blueprint position: within its level, the nearest. */
    private static Trigger nearestPuzzle(Site site, int x, int y, int z) {
        Trigger best = null;
        int bestD = 20 * 20;
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.PUZZLE || Math.abs(t.y() - y) > 3) {
                continue;
            }
            int d = (t.x() - x) * (t.x() - x) + (t.z() - z) * (t.z() - z);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    /**
     * The void is a real end portal block, for its look, so nothing inside a
     * dungeon may use one to leave: mobs and dropped items would otherwise
     * fall through to the End. Players never get this far; the tick above
     * moves them first.
     */
    public static void onTravel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent e) {
        if (e.getDimension() != net.minecraft.world.level.Level.END || !(e.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (Dungeons.at(level, e.getEntity().blockPosition()).isEmpty()) {
            return;
        }
        e.setCanceled(true);
        // A thrown item that misses the path comes back to whoever threw it: how a leap of faith is found.
        if (e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && item.getOwner() instanceof ServerPlayer thrower && thrower.level() == level
                && thrower.distanceToSqr(item) < 32 * 32) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, item.getX(), item.getY() + 0.3, item.getZ(), 12, 0.1, 0.2, 0.1, 0.3);
            item.teleportTo(thrower.getX(), thrower.getY() + 0.5, thrower.getZ());
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.playSound(null, thrower.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        HERE.remove(e.getEntity().getUUID());
        LAST_SAFE.remove(e.getEntity().getUUID());
        NOTICED.remove(e.getEntity().getUUID());
        PREV.remove(e.getEntity().getUUID());
        Arrivals.forget(e.getEntity().getUUID());
    }

    private static Trigger triggerAt(Site site, BlockPos pos) {
        BlockPos o = site.origin();
        return site.built().blueprint().triggerAt(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
    }

    /**
     * Three darts from the face of the nearest wall, from just above head
     * height and angled down at the chest. A narrow corridor's wall is one
     * block away, and darts fired level from it start inside the player and
     * miss: the first version did exactly that.
     */
    private static void darts(ServerLevel level, ServerPlayer player, BlockPos feet) {
        Direction from = null;
        int best = Integer.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 4; k++) {
                if (!level.getBlockState(feet.relative(d, k).above()).isAir()) {
                    if (k < best) {
                        best = k;
                        from = d;
                    }
                    break;
                }
            }
        }
        double sx = feet.getX() + 0.5;
        double sy = feet.getY() + 2.4;
        double sz = feet.getZ() + 0.5;
        if (from != null) {
            sx += from.getStepX() * (best - 0.55);
            sz += from.getStepZ() * (best - 0.55);
        }
        for (int i = 0; i < 3; i++) {
            Arrow arrow = new Arrow(level, sx, sy, sz, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(player.getX() - sx, player.getY() + 1.1 - sy, player.getZ() - sz, 1.5f, 4f);
            level.addFreshEntity(arrow);
        }
        level.playSound(null, sx, sy, sz, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1f, 1.2f);
        tell(player, "Click. Darts fly from the wall!");
    }

    private static void gas(ServerLevel level, ServerPlayer player, BlockPos feet) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
        cloud.setRadius(2.5f);
        cloud.setDuration(140);
        cloud.setRadiusPerTick(-0.01f);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        level.addFreshEntity(cloud);
        level.playSound(null, feet, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
        tell(player, "Hiss. Poison gas seeps from the floor: step out of the cloud!");
    }

    /**
     * A pit trap goes: its nine tiles crack and crumble away together, fast,
     * into the spikes. A perception mod may let whoever is on it leap clear,
     * back to the last solid floor they stood on. Once anyone who fell has
     * landed, a ladder appears up one wall so nobody is stuck down there.
     */
    private static void pitfall(ServerLevel level, Site site, Trigger t) {
        BlockPos o = site.origin();
        java.util.List<BlockPos> tiles = new java.util.ArrayList<>();
        for (int[] c : t.targets()) {
            tiles.add(o.offset(c[0], c[1] - 1, c[2]));
        }
        BlockPos centre = o.offset(t.x(), t.y(), t.z());
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(centre, 6)) {
                tell(p, "The floor cracks under your feet!");
            }
        }
        Crumbles.start(level, tiles, 3, -1, () -> {
            for (ServerPlayer p : level.players()) {
                BlockPos f = p.blockPosition();
                if (p.isCreative() || p.isSpectator() || Math.abs(f.getX() - centre.getX()) > 1
                        || Math.abs(f.getZ() - centre.getZ()) > 1 || Math.abs(f.getY() - centre.getY()) > 1) {
                    continue;
                }
                BlockPos safe = LAST_SAFE.get(p.getUUID());
                boolean clear = safe != null && safe.closerThan(f, 4)
                        && com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(q -> q.recovers(p, t.level())).orElse(false);
                if (clear) {
                    p.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
                    tell(p, "You leap clear as the floor falls away!");
                } else {
                    tell(p, "The floor gives way!");
                }
            }
            Crumbles.later(level, 40, () -> {
                net.minecraft.world.level.block.state.BlockState ladder = net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(centre.offset(0, -dy, -1), ladder, 3);
                }
            });
        });
    }

    private static void tell(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
