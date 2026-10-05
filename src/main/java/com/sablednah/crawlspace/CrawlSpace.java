package com.sablednah.crawlspace;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * CrawlSpace: procedural roguelike dungeons.
 *
 * <p>Server-side only. The planner ({@code plan} package) has no Minecraft in
 * it at all; this class and the {@code neoforge} package are the only parts a
 * version port touches.</p>
 */
@Mod(CrawlSpace.MODID)
public final class CrawlSpace {

    public static final String MODID = "crawlspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrawlSpace(IEventBus modBus) {
    }
}
