package com.github.klainstom.data;

import net.minestom.server.instance.Instance;

import java.util.HashMap;
import java.util.Map;

public class AfkstomData {
    private static final Map<Instance, Integer> instanceAfkTimeout = new HashMap<>();
    private static int defaultTimeout = -1;

    /**
     * Sets the amount of ticks a player needs to do nothing to be considered as AFK
     * @param instance The instance this value is applicable for
     * @param timeout  The amount of ticks. Negative values never time out, null resets to standard
     */
    public static void setInstanceTimeout(Instance instance, Integer timeout) {
        if (timeout == null) {
            instanceAfkTimeout.remove(instance);
        } else {
            instanceAfkTimeout.put(instance, timeout);
        }
    }

    /**
     * Sets the amount of ticks a player needs to do nothing to be considered as AFK
     * @param timeout  The amount of ticks. Negative values never time out, null resets to standard
     */
    public static void setDefaultTimeout(int timeout) {
        defaultTimeout = timeout;
    }

    /**
     * Gets the timeout ticks for an instance
     * @param instance The instance
     */
    public static int getTimeoutForInstance(Instance instance) {
        return instanceAfkTimeout.getOrDefault(instance, defaultTimeout);
    }
}