package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The dungeon clock (Warhammer Quest's power phase: "the Wizard rolls a 1").
 * Every {@code play.eventSeconds} each level with someone on it rolls a die,
 * and on a 1 something happens: a wandering band, a sound in the dark, the
 * light going out, a curse, a stranger. Fighting makes noise: for fifteen
 * seconds after anyone on the level hurts or is hurt, a 2 counts too. Nothing
 * shows the clock; an event says what it is as it happens.
 */
public final class Clock {

    private enum Event {
        BAND(4, 0), SPOOR(3, 0), DARK(2, 1), SWARM(2, 1), CURSE(1, 2), KNIGHT(1, 0), STRANGER(1, 1), ROCKS(1, 2), GLINT(1, 0);

        final int weight;
        final int fromLevel;

        Event(int weight, int fromLevel) {
            this.weight = weight;
            this.fromLevel = fromLevel;
        }
    }

    /** Per level in play, "x,y,z/level": the next turn, and until when it is noisy. */
    private static final Map<String, long[]> TURNS = new HashMap<>();
    private static final long NOISE = 15 * 20;

    private Clock() {
    }

    /** Fighting is noise: a player hurting or being hurt inside a dungeon makes that level noisy for a while. */
    public static void onHurt(LivingIncomingDamageEvent e) {
        ServerPlayer p = e.getEntity() instanceof ServerPlayer v ? v
                : e.getSource().getEntity() instanceof ServerPlayer a ? a : null;
        if (p == null || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        String key = key(level, p);
        if (key != null) {
            TURNS.computeIfAbsent(key, k -> new long[] {level.getGameTime() + period(), 0})[1] = level.getGameTime() + NOISE;
        }
    }

    private static long period() {
        return CrawlConfig.eventSeconds() * 20L;
    }

    private static String key(ServerLevel level, ServerPlayer p) {
        Site site = Dungeons.at(level, p.blockPosition()).orElse(null);
        if (site == null || site.planner() != Site.PLANNER_VERSION) {
            return null;
        }
        int li = Arrivals.levelAt(site, p.blockPosition());
        return li < 0 ? null : site.origin().toShortString() + "/" + li;
    }

    public static void tick(MinecraftServer server) {
        if (CrawlConfig.eventSeconds() <= 0 || server.getTickCount() % 20 != 0) {
            return;
        }
        Map<String, List<ServerPlayer>> here = new HashMap<>();
        Map<String, Site> sites = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator() || !(p.level() instanceof ServerLevel level)) {
                continue;
            }
            Site site = Dungeons.at(level, p.blockPosition()).orElse(null);
            if (site == null || site.planner() != Site.PLANNER_VERSION) {
                continue;
            }
            int li = Arrivals.levelAt(site, p.blockPosition());
            if (li < 0) {
                continue;
            }
            String k = site.origin().toShortString() + "/" + li;
            here.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
            sites.put(k, site);
        }
        TURNS.keySet().retainAll(here.keySet());
        here.forEach((k, players) -> {
            ServerLevel level = (ServerLevel) players.get(0).level();
            long now = level.getGameTime();
            long[] t = TURNS.computeIfAbsent(k, x -> new long[] {now + period(), 0});
            if (now < t[0]) {
                return;
            }
            t[0] = now + period();
            int roll = 1 + level.getRandom().nextInt(6);
            if (roll == 1 || roll == 2 && now < t[1]) {
                Site site = sites.get(k);
                int li = Integer.parseInt(k.substring(k.lastIndexOf('/') + 1));
                happen(level, site, li, players);
            }
        });
    }

    /** The events' names, for the command. */
    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Event e : Event.values()) {
            out.add(e.name().toLowerCase());
        }
        return out;
    }

    /** For /crawlspace event: makes one happen now, to the player who asked, on their level. */
    static boolean force(ServerLevel level, ServerPlayer player, String name) {
        Site site = Dungeons.at(level, player.blockPosition()).orElse(null);
        int li = site == null ? -1 : Arrivals.levelAt(site, player.blockPosition());
        if (li < 0) {
            return false;
        }
        Event event;
        try {
            event = Event.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        happen(level, site, li, List.of(player), event);
        return true;
    }

    private static void happen(ServerLevel level, Site site, int li, List<ServerPlayer> players) {
        happen(level, site, li, players, null);
    }

    private static void happen(ServerLevel level, Site site, int li, List<ServerPlayer> players, Event forced) {
        RandomSource random = level.getRandom();
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        List<Event> deck = new ArrayList<>();
        int total = 0;
        for (Event e : Event.values()) {
            if (li >= e.fromLevel && !(peaceful && (e == Event.BAND || e == Event.SWARM))) {
                deck.add(e);
                total += e.weight;
            }
        }
        int r = random.nextInt(total);
        Event event = deck.get(0);
        for (Event e : deck) {
            r -= e.weight;
            if (r < 0) {
                event = e;
                break;
            }
        }
        if (forced != null) {
            event = forced;
        }
        ServerPlayer who = players.get(random.nextInt(players.size()));
        switch (event) {
            case BAND -> {
                BlockPos at = roomOutOfSight(level, site, li, players);
                if (at == null) {
                    return;
                }
                int n = Bestiary.band(level, site, li, at, 2 + random.nextInt(2), who);
                if (n > 0) {
                    level.playSound(null, at, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 2.5f, 0.7f);
                    all(players, "Footsteps, " + direction(who, at) + ", coming closer.");
                }
            }
            case SPOOR -> {
                BlockPos at = roomOutOfSight(level, site, li, players);
                if (at == null) {
                    return;
                }
                SoundEvent[] sounds = {SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_AMBIENT, SoundEvents.SPIDER_AMBIENT, SoundEvents.RAVAGER_AMBIENT};
                level.playSound(null, at, sounds[random.nextInt(sounds.length)], SoundSource.HOSTILE, 3f, 0.6f);
                all(players, "Something moves in the dark, " + direction(who, at) + ".");
            }
            case DARK -> {
                for (ServerPlayer p : players) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 20, 0));
                }
                level.playSound(null, who.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.AMBIENT, 1.5f, 0.6f);
                all(players, "A cold draught snuffs the light!");
            }
            case SWARM -> {
                int n = Bestiary.swarm(level, who, li);
                if (n > 0) {
                    Triggers.tell(who, "A swarm boils out of the cracks!");
                }
            }
            case CURSE -> {
                who.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 * 20, 0));
                who.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60 * 20, 0));
                level.sendParticles(ParticleTypes.SOUL, who.getX(), who.getY() + 1, who.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                level.playSound(null, who.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 1.5f, 0.6f);
                Triggers.tell(who, "A voice in the stone: \"Trespasser.\" A curse settles on you.");
            }
            case KNIGHT -> {
                who.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 90 * 20, 0));
                level.sendParticles(ParticleTypes.END_ROD, who.getX(), who.getY() + 1, who.getZ(), 16, 0.5, 0.8, 0.5, 0.01);
                Triggers.tell(who, "A ghostly knight nods to you: \"Monsters near. Be ready.\"");
            }
            case STRANGER -> {
                if (Bestiary.stranger(level, who)) {
                    Triggers.tell(who, "A cloaked stranger steps out of the dark, and offers to trade.");
                }
            }
            case ROCKS -> {
                Triggers.rockfall(level, who);
            }
            case GLINT -> {
                Bestiary.glint(level, who, li);
                Triggers.tell(who, "Something glints among the old bones at your feet.");
            }
        }
    }

    /**
     * The middle of a room on the level at least 16 blocks from every player,
     * nearest them: where a band appears or a sound comes from. Never a
     * puzzle room or a secret one.
     */
    static BlockPos roomOutOfSight(ServerLevel level, Site site, int li, List<ServerPlayer> players) {
        LevelPlan lp = site.built().plan().levels().get(li);
        BlockPos o = site.origin();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Room r : lp.rooms) {
            if (r.role == Role.PUZZLE || r.role == Role.SECRET) {
                continue;
            }
            for (int d = 0; d <= Math.max(r.w, r.h) / 2; d++) {
                int x = r.centerX() + d;
                int z = r.centerZ();
                if (lp.cell(x, z) != com.sablednah.crawlspace.plan.Cell.FLOOR || lp.region(x, z) != r.id) {
                    continue;
                }
                int y = o.getY() + com.sablednah.crawlspace.build.Blueprinter.floorY(site.built().plan(), li) + lp.height(x, z);
                BlockPos c = new BlockPos(o.getX() + x, y, o.getZ() + z);
                if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir() || level.getBlockState(c.below()).isAir()) {
                    continue;
                }
                double nearest = Double.MAX_VALUE;
                for (ServerPlayer p : players) {
                    nearest = Math.min(nearest, Math.sqrt(p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5)));
                }
                if (nearest >= 16 && nearest < bestD) {
                    bestD = nearest;
                    best = c;
                }
                break;
            }
        }
        return best;
    }

    /** "to the north", "to the south-east": the way to {@code at} from where the player stands. */
    static String direction(ServerPlayer p, BlockPos at) {
        double dx = at.getX() + 0.5 - p.getX();
        double dz = at.getZ() + 0.5 - p.getZ();
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        int i = (int) Math.floorMod(Math.round(a / 45.0), 8);
        return "to the " + names[i];
    }

    private static void all(List<ServerPlayer> players, String text) {
        for (ServerPlayer p : players) {
            Triggers.tell(p, text);
        }
    }

    public static void clear() {
        TURNS.clear();
    }
}
