package com.sablednah.crawlspace.api;

import java.util.Optional;

/** CrawlSpace's door for other mods. */
public final class CrawlSpaceApi {

    private static volatile Perception perception;

    private CrawlSpaceApi() {
    }

    /** Lets an RPG mod decide who notices traps and secret doors, and who disarms traps. Last one in wins. */
    public static void setPerception(Perception p) {
        perception = p;
    }

    public static Optional<Perception> perception() {
        return Optional.ofNullable(perception);
    }
}
