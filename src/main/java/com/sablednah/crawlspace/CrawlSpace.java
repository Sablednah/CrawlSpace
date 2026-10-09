package com.sablednah.crawlspace;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.sablednah.crawlspace.neoforge.Bosses;
import com.sablednah.crawlspace.neoforge.Builds;
import com.sablednah.crawlspace.neoforge.CrawlConfig;
import com.sablednah.crawlspace.neoforge.Triggers;
import com.sablednah.crawlspace.neoforge.worldgen.CrawlWorldgen;
import com.sablednah.crawlspace.neoforge.CrawlCommands;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * CrawlSpace: procedural roguelike dungeons.
 *
 * <p>Server-side only. The planner ({@code plan} package) has no Minecraft in
 * it at all; this class and the {@code neoforge} package are the only parts a
 * version port touches; {@code build} (blueprints by role) is pure too.</p>
 */
@Mod(CrawlSpace.MODID)
public final class CrawlSpace {

    public static final String MODID = "crawlspace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrawlSpace(IEventBus modBus, ModContainer container) {
        CrawlWorldgen.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, CrawlConfig.SPEC);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CrawlCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddServerReloadListenersEvent e) ->
                e.addListener(com.sablednah.crawlspace.neoforge.ThemeData.ID, com.sablednah.crawlspace.neoforge.ThemeData.INSTANCE));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Builds.tick();
            com.sablednah.crawlspace.neoforge.Crumbles.tick();
            com.sablednah.crawlspace.neoforge.Restamp.tick(e.getServer());
            Bosses.tick(e.getServer().overworld().getGameTime());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            com.sablednah.crawlspace.neoforge.Crumbles.clear();
            com.sablednah.crawlspace.neoforge.Restamp.clear();
            Builds.clear();
            Bosses.clear();
        });
        NeoForge.EVENT_BUS.addListener(Bosses::onJoin);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Bestiary::onHurt);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Bestiary::onTarget);
        NeoForge.EVENT_BUS.addListener(Triggers::onUse);
        NeoForge.EVENT_BUS.addListener(Triggers::onBreak);
        NeoForge.EVENT_BUS.addListener(Triggers::onTravel);
        NeoForge.EVENT_BUS.addListener(Triggers::onTick);
        NeoForge.EVENT_BUS.addListener(Triggers::onLogout);
        NeoForge.EVENT_BUS.addListener(Triggers::onSpawnCheck);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onLeftClick);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onBreak);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onPlace);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onExplode);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onPiston);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onLogout);
        NeoForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Restamp::onLoad);
    }
}
