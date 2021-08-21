package com.github.klainstom.afkstom;

import com.github.klainstom.data.AfkstomData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryOpenEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.ItemUpdateStateEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.event.trait.PlayerEvent;
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
    public static final Pos SPAWN_POS = new Pos(8.5, 1, 8.5);

    private static final Set<Class<? extends PlayerEvent>> ACTIVITY_EVENTS = Set.of(
            AdvancementTabEvent.class,
            AsyncPlayerPreLoginEvent.class,
            InventoryCloseEvent.class,
            InventoryOpenEvent.class,
            InventoryPreClickEvent.class,
            ItemDropEvent.class,
            ItemUpdateStateEvent.class,
            PlayerBlockBreakEvent.class,
            PlayerBlockInteractEvent.class,
            PlayerBlockPlaceEvent.class,
            PlayerChangeHeldSlotEvent.class,
            PlayerChatEvent.class,
            PlayerCommandEvent.class,
            PlayerDeathEvent.class,
            PlayerEntityInteractEvent.class,
            PlayerHandAnimationEvent.class,
            PlayerItemAnimationEvent.class,
            PlayerMoveEvent.class,
            PlayerPreEatEvent.class,
            PlayerResourcePackStatusEvent.class,
            PlayerRespawnEvent.class,
            PlayerSettingsChangeEvent.class,
            PlayerStartDiggingEvent.class,
            PlayerStartFlyingEvent.class,
            PlayerStartFlyingWithElytraEvent.class,
            PlayerStartSneakingEvent.class,
            PlayerStartSprintingEvent.class,
            PlayerStopFlyingEvent.class,
            PlayerStopFlyingWithElytraEvent.class,
            PlayerStopSneakingEvent.class,
            PlayerStopSprintingEvent.class,
            PlayerSwapItemEvent.class,
            PlayerUseItemEvent.class,
            PlayerUseItemOnBlockEvent.class
    );

    private final Map<Player, Instance> lastPlayerInstance = new HashMap<>();
    private final Map<Player, Integer> playerTicksUntilAfk = new HashMap<>();



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
        AFK_INSTANCE.setTimeUpdate(null);

        getEventNode().addListener(AddEntityToInstanceEvent.class, event -> {
            if (!AFK_INSTANCE.equals(event.getInstance())) return;
            if (!(event.getEntity() instanceof Player)) return;
            final Player player = (Player) event.getEntity();
            player.setRespawnPoint(SPAWN_POS);
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
                event.getPlayer().setInstance(AFK_INSTANCE, SPAWN_POS);
        });

        getEventNode().addListener(NoLongerAfkEvent.class, event -> {
            event.getPlayer().resetTitle();
            lastPlayerInstance.remove(event.getPlayer());
            if (!AFK_INSTANCE.equals(event.getLastInstance()))
                // TODO: 18.08.21 add position information
                event.getPlayer().setInstance(event.getLastInstance());
        });

        getEventNode().addListener(PlayerTickEvent.class, event -> {
            Player player = event.getPlayer();
            // TODO: 18.08.21 make default timeout ticks configurable
            playerTicksUntilAfk.putIfAbsent(player, AfkstomData.getTimeoutForInstance(player.getInstance()));
            if (playerTicksUntilAfk.get(player) > 0) playerTicksUntilAfk.computeIfPresent(player, (p, t) -> t-1);
            if (playerTicksUntilAfk.get(player) == 0) setNowAfk(player, player.getInstance());
            // TODO: 18.08.21 remove debug message
            player.sendMessage(Component.text(playerTicksUntilAfk.get(player)));
        });

        for (Class<? extends PlayerEvent> eventType : ACTIVITY_EVENTS) {
            // Only reset timer if player actually did something
            getEventNode().addListener(eventType, event -> {
                resetAfkTimer(event.getPlayer(), event.getPlayer().getInstance());
                setNoLongerAfk(event.getPlayer(), event.getPlayer().getInstance());
            });
        }

        // Reset timer on instance change
        getEventNode().addListener(PlayerSpawnEvent.class, event -> {
            if (!AFK_INSTANCE.equals(event.getSpawnInstance()))
                resetAfkTimer(event.getPlayer(), event.getSpawnInstance());
        });

        getEventNode().addListener(PlayerDisconnectEvent.class, event -> {
            playerTicksUntilAfk.remove(event.getPlayer());
            lastPlayerInstance.remove(event.getPlayer());
        });
    }

    private void resetAfkTimer(Player player, Instance instance) {
        // TODO: 18.08.21 make default timeout ticks configurable
        playerTicksUntilAfk.put(player, AfkstomData.getTimeoutForInstance(instance));
    }

    private void setNowAfk(Player player, Instance instance) {
        if (lastPlayerInstance.putIfAbsent(player, instance) == null) {
            MinecraftServer.getGlobalEventHandler().call(
                    new NowAfkEvent(player, instance));
        }
    }

    private void setNoLongerAfk(Player player, Instance instance) {
        // TODO: 18.08.21 use instance field to modify the new instance; e.g. always return to lobby
        if (lastPlayerInstance.containsKey(player)) {
            MinecraftServer.getGlobalEventHandler().call(
                    new NoLongerAfkEvent(player, lastPlayerInstance.get(player)));
        }
    }

    @Override
    public void terminate() {
        MinecraftServer.LOGGER.info("$name$ terminate.");
        AFK_INSTANCE.getPlayers().forEach(player -> player.kick(Component.text("This instance was destroyed.")));
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
