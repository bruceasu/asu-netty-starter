package io.netty.util;

import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.transport.NativeSupport;

/**
 * OsUtils.
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-12-21 16:47
 */
public class OsUtils extends me.asu.util.OsUtils {
    // 指定是否使用netty实现的epoll，前提是操作系统要支持（Liunx 2.6+）
    // JDK1.7+ 在Liunx 2.6+也会使用epoll.
    // Netty的 epoll transport使用 epoll edge-triggered 而 java的 nio 使用
    // level-triggered.
    // 另外netty epoll transport 暴露了更多的nio没有的配置参数， 如 TCP_CORK, SO_REUSEADDR等等
    // 使用native socket transport很简单，只需将相应的类替换即可。
    // NioEventLoopGroup → EpollEventLoopGroup
    // NioEventLoop → EpollEventLoop
    // NioServerSocketChannel → EpollServerSocketChannel
    // NioSocketChannel → EpollSocketChannel

    public static MultithreadEventLoopGroup getEventLoopGroup(boolean useNettyEPoll) {
        Class cls = loadClass("io.netty.channel.epoll.EpollEventLoopGroup");

        if (useNettyEPoll && NativeSupport.isNativeEPollAvailable() && cls != null) {
            try {
                return (MultithreadEventLoopGroup) cls.newInstance();
            } catch (Exception e) {
                return new NioEventLoopGroup();
            }
        } else {
            return new NioEventLoopGroup();
        }

    }

    public static Class getServerSocketChannelType(boolean useNettyEPoll) {
        Class cls = loadClass("io.netty.channel.epoll.EpollServerSocketChannel");
        if (useNettyEPoll && NativeSupport.isNativeEPollAvailable() && cls != null) {
            return cls.getClass();
        } else {
            return NioServerSocketChannel.class;
        }
    }


    public static Class getSocketChannelChannelType(boolean useNettyEPoll) {
        Class cls = loadClass("io.netty.channel.epoll.EpollSocketChannel");
        if (useNettyEPoll && NativeSupport.isNativeEPollAvailable() && cls != null) {
            return cls.getClass();
        } else {
            return NioSocketChannel.class;
        }
    }

    private static Class loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
