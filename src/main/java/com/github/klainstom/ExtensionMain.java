package com.github.klainstom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.extensions.Extension;

// TODO: 12.08.21 move to actual extension package
public class ExtensionMain extends Extension {
    @Override
    public void initialize() {
        MinecraftServer.LOGGER.info("$name$ initialize.");

    }

    @Override
    public void terminate() {
        MinecraftServer.LOGGER.info("$name$ terminate.");

    }
}
