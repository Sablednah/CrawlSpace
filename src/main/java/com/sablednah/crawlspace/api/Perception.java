package com.sablednah.crawlspace.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * How a character finds what a dungeon hides. CrawlSpace asks; an RPG mod
 * (LegendQuest, or any other) answers with its own rules and dice. Register
 * one with {@link CrawlSpaceApi#setPerception}; CrawlSpace never imports the
 * mod that provides it.
 *
 * <p>With none registered, CrawlSpace's own hints show everything to
 * everyone (config {@code play.hints}), and a spotted trap disarms
 * reliably.</p>
 */
public interface Perception {

    /** What there is to notice. */
    enum Hidden { TRAP, SECRET_DOOR }

    /**
     * Whether this player notices a hidden thing, asked once per player per
     * thing, when they first come within a few blocks of it. A player who
     * notices it is shown it from then on.
     *
     * @param depth the dungeon level, from 0 at the top: deeper is harder
     */
    boolean notices(ServerPlayer player, Hidden what, int depth);

    /**
     * Whether this player disarms a trap they have spotted, asked when they
     * sneak and use its floor tile. A failure springs the trap on them.
     */
    boolean disarms(ServerPlayer player, int depth);

    /**
     * Whether this player catches themselves stepping off a puzzle room's
     * maze, asked each time they touch the void. On a pass they are put back
     * on the last path block they stood on; on a fail, or with no answer, the
     * void sends them to the room's restart block at its centre.
     *
     * <p>Optional: a mod that registered before this existed keeps working,
     * and nobody catches themselves.</p>
     */
    default boolean recovers(ServerPlayer player, int depth) {
        return false;
    }

    /*
     * The five below arrived in CrawlSpace 0.3.0, all optional. With no
     * answer, each one is a no, and play is as it is without an RPG mod.
     */

    /**
     * Whether this player sees that a chest is a mimic before it bites, asked
     * the first time they open one. On a pass they are warned and the chest
     * stays shut; opening it again is their choice.
     */
    default boolean spotsMimic(ServerPlayer player, int depth) {
        return false;
    }

    /**
     * Whether this player finds and draws a trapped chest's needle as they open
     * it. On a pass the trap never springs.
     */
    default boolean disarmsChest(ServerPlayer player, int depth) {
        return false;
    }

    /**
     * Whether this player reads a trap's tell (the scorch, the pebbles, the
     * frost), asked once per player per tell as they come near. On a pass they
     * are told what it warns of. Without an answer the tell is still there to
     * be read by eye.
     */
    default boolean readsTell(ServerPlayer player, int depth) {
        return false;
    }

    /**
     * Whether this player reads a shrine's omen before using it, asked once per
     * player per shrine as they come near. On a pass they are told which
     * shrine it is, and whether it is cursed.
     */
    default boolean readsOmen(ServerPlayer player, int depth) {
        return false;
    }

    /**
     * Whether this player slips a sentry's eye, asked the first time a sentry
     * would see them. On a pass that sentry does not see them again.
     */
    default boolean evades(ServerPlayer player, int depth) {
        return false;
    }
}
