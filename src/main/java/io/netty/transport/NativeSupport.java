package io.netty.transport;

import io.netty.channel.epoll.Epoll;

/**
 * Netty provides the native socket transport using JNI.
 * This transport has higher performance and produces less garbage.
 */
public final class NativeSupport {

    /**
     * The native socket transport for Linux using JNI.
     */
    public static boolean isNativeEPollAvailable() {
        return Epoll.isAvailable();
    }

}
