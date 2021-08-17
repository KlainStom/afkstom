package com.github.klainstom.afkstom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.extensions.Extension;
import net.minestom.server.instance.ChunkGenerator;
import net.minestom.server.instance.ChunkPopulator;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biomes.Biome;

import java.time.Duration;
import java.util.*;

public class ExtensionMain extends Extension {
    public static final InstanceContainer AFK_INSTANCE;

    private final Map<Player, Instance> lastPlayerInstance = new HashMap<>();

    static {
        DimensionType afkDimensionType = DimensionType
                .builder(NamespaceID.from("afkstom", "afk"))
                .natural(false)
                .fixedTime(-6000L).build();
        MinecraftServer.getDimensionTypeManager().addDimension(afkDimensionType);
        AFK_INSTANCE = MinecraftServer.getInstanceManager()
                .createInstanceContainer(afkDimensionType);
    }

    @Override
    public void initialize() {
        MinecraftServer.LOGGER.info("$name$ initialize.");
        AFK_INSTANCE.setChunkGenerator(new EmptyGenerator());
        AFK_INSTANCE.enableAutoChunkLoad(false);
        AFK_INSTANCE.loadChunk(0,0);

        getEventNode().addListener(AddEntityToInstanceEvent.class, event -> {
            if (!AFK_INSTANCE.equals(event.getInstance())) return;
            if (!(event.getEntity() instanceof Player)) return;
            final Player player = (Player) event.getEntity();
            player.setRespawnPoint(new Pos(8.5, 1, 8.5));
            player.setAutoViewable(false);
            player.setGameMode(GameMode.ADVENTURE);
        });

        getEventNode().addListener(EntityDamageEvent.class, event -> {
            if (!AFK_INSTANCE.equals(event.getEntity().getInstance())) return;
            if (!(event.getEntity() instanceof Player)) return;
            if (event.getDamageType().equals(DamageType.VOID)) {
                event.getEntity().teleport(((Player) event.getEntity()).getRespawnPoint());
                event.setCancelled(true);
            }
        });

        getEventNode().addListener(NowAfkEvent.class, event -> {
            lastPlayerInstance.put(event.getPlayer(), event.getAfkInstance());
            event.getPlayer().showTitle(Title.title(Component.text("You are AFK"), Component.empty(),
                    Title.Times.of(Duration.ZERO, Duration.ofDays(30), Duration.ZERO)));
            if (!AFK_INSTANCE.equals(event.getAfkInstance()))
                event.getPlayer().setInstance(AFK_INSTANCE);
        });

        getEventNode().addListener(PlayerMoveEvent.class, event -> {
            if (!lastPlayerInstance.containsKey(event.getPlayer())) return;
            MinecraftServer.getGlobalEventHandler().call(
                    new NoLongerAfkEvent(event.getPlayer(), lastPlayerInstance.get(event.getPlayer())));
            lastPlayerInstance.remove(event.getPlayer());
        });
    }

    @Override
    public void terminate() {
        MinecraftServer.LOGGER.info("$name$ terminate.");
        MinecraftServer.getInstanceManager().unregisterInstance(AFK_INSTANCE);
    }

    private static class EmptyGenerator implements ChunkGenerator {

        @Override public void generateChunkData(ChunkBatch batch, int chunkX, int chunkZ) {
            batch.setBlock(8, 0, 8, Block.STONE);
        }

        @Override public void fillBiomes(Biome[] biomes, int chunkX, int chunkZ) {
            Arrays.fill(biomes, Biome.PLAINS);
        }

        @Override public List<ChunkPopulator> getPopulators() {
            return null;
        }
    }
}
