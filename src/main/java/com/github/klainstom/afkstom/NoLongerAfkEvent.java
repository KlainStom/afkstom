package com.github.klainstom.afkstom;

import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

public class NoLongerAfkEvent implements PlayerEvent {
    private final Player player;
    private final Instance lastInstance;

    public NoLongerAfkEvent(Player player, Instance instance) {
        super();
        this.player = player;
        this.lastInstance = instance;
    }

    @Override
    public @NotNull Player getPlayer() {
        return this.player;
    }

    public Instance getLastInstance() {
        return lastInstance;
    }
}
