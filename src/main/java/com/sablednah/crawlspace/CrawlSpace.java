package com.sablednah.crawlspace;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.sablednah.crawlspace.neoforge.Bosses;
import com.sablednah.crawlspace.neoforge.Builds;
import com.sablednah.crawlspace.neoforge.CrawlConfig;
import com.sablednah.crawlspace.neoforge.Triggers;
import com.sablednah.crawlspace.neoforge.worldgen.CrawlWorldgen;
import com.sablednah.crawlspace.neoforge.CrawlCommands;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;

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

    public CrawlSpace() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CrawlWorldgen.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CrawlConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CrawlCommands.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.AddReloadListenerEvent e) ->
                e.addListener(com.sablednah.crawlspace.neoforge.ThemeData.INSTANCE));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Builds.tick();
            com.sablednah.crawlspace.neoforge.Crumbles.tick();
            com.sablednah.crawlspace.neoforge.Restamp.tick(server);
            com.sablednah.crawlspace.neoforge.Clock.tick(server);
            Bosses.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.Powers.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.Sentries.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.SurvivalRooms.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.Sleepers.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.Forbidden.tick(server.overworld().getGameTime());
            com.sablednah.crawlspace.neoforge.Cubes.tick(server.overworld().getGameTime());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            com.sablednah.crawlspace.neoforge.Crumbles.clear();
            com.sablednah.crawlspace.neoforge.Restamp.clear();
            com.sablednah.crawlspace.neoforge.Clock.clear();
            com.sablednah.crawlspace.neoforge.IceBoards.clear();
            com.sablednah.crawlspace.neoforge.Shrines.clear();
            Builds.clear();
            Bosses.clear();
            com.sablednah.crawlspace.neoforge.Powers.clear();
            com.sablednah.crawlspace.neoforge.Sentries.clear();
            com.sablednah.crawlspace.neoforge.Altars.clear();
            com.sablednah.crawlspace.neoforge.TeleportMazes.clear();
            com.sablednah.crawlspace.neoforge.SurvivalRooms.clear();
            com.sablednah.crawlspace.neoforge.Sleepers.clear();
            com.sablednah.crawlspace.neoforge.Forbidden.clear();
            com.sablednah.crawlspace.neoforge.Cubes.clear();
            com.sablednah.crawlspace.neoforge.Seals.clear();
            com.sablednah.crawlspace.neoforge.ObjectiveRooms.clear();
        });
        MinecraftForge.EVENT_BUS.addListener(Bosses::onJoin);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onJoin);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onHurtAmount);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onDeath);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onDrops);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onGrief);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onSpawnerSpawn);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onSpawnPlacement);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Powers::onSpawnPosition);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Clock::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Bestiary::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Sleepers::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Sleepers::onJoin);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Prisoners::onInteract);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Prisoners::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Cubes::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Cubes::onJoin);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.ObjectiveRooms::onBreak);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.ObjectiveRooms::onHurt);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.ObjectiveRooms::onItemJoin);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Heroic::onUseItem);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Heroic::onExperience);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Bestiary::onTarget);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onUse);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onBreak);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onTravel);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onTick);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onLogout);
        MinecraftForge.EVENT_BUS.addListener(Triggers::onSpawnCheck);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onLeftClick);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onBreak);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onPlace);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onExplode);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onPiston);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Protection::onLogout);
        MinecraftForge.EVENT_BUS.addListener(com.sablednah.crawlspace.neoforge.Restamp::onLoad);
    }
}
