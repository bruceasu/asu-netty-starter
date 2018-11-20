package io.netty.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.transport.Config.ConfigGroup;
import io.netty.util.OsUtils;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class TcpAcceptor extends Acceptor {

    /** use native transport */
    private final boolean isNative;

    private final ConfigGroup configGroup = new ConfigGroup();

    public TcpAcceptor(int port) {
        super(new InetSocketAddress(port));
        isNative = false;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress) {
        super(localAddress);
        isNative = false;
        init();
    }

    public TcpAcceptor(int port, int nWorkers) {
        super(new InetSocketAddress(port), nWorkers);
        isNative = false;
        init();
    }

    public TcpAcceptor(int port, int nBosses, int nWorkers) {
        super(new InetSocketAddress(port), nBosses, nWorkers);
        isNative = false;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress, int nWorkers) {
        super(localAddress, nWorkers);
        isNative = false;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress, int nBosses, int nWorkers) {
        super(localAddress, nBosses, nWorkers);
        isNative = false;
        init();
    }

    public TcpAcceptor(int port, boolean isNative) {
        super(new InetSocketAddress(port));
        this.isNative = isNative;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress, boolean isNative) {
        super(localAddress);
        this.isNative = isNative;
        init();
    }

    public TcpAcceptor(int port, int nWorkers, boolean isNative) {
        super(new InetSocketAddress(port), nWorkers);
        this.isNative = isNative;
        init();
    }

    public TcpAcceptor(int port, int nBosses, int nWorkers, boolean isNative) {
        super(new InetSocketAddress(port), nBosses, nWorkers);
        this.isNative = isNative;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress, int nWorkers, boolean isNative) {
        super(localAddress, nWorkers);
        this.isNative = isNative;
        init();
    }

    public TcpAcceptor(SocketAddress localAddress, int nBosses, int nWorkers, boolean isNative) {
        super(localAddress, nBosses, nWorkers);
        this.isNative = isNative;
        init();
    }

    @Override
    protected void setOptions() {
        super.setOptions();

        ServerBootstrap boot = bootstrap();

        // parent options
        ConfigGroup.ParentConfig parent = configGroup.parent();
        boot.option(ChannelOption.SO_BACKLOG, parent.getBacklog());
        boot.option(ChannelOption.SO_REUSEADDR, parent.isReuseAddress());
        if (parent.getRcvBuf() > 0) {
            boot.option(ChannelOption.SO_RCVBUF, parent.getRcvBuf());
        }

        // child options
        ConfigGroup.ChildConfig child = configGroup.child();
        boot.childOption(ChannelOption.SO_REUSEADDR, child.isReuseAddress())
            .childOption(ChannelOption.SO_KEEPALIVE, child.isKeepAlive())
            .childOption(ChannelOption.TCP_NODELAY, child.isTcpNoDelay())
            .childOption(ChannelOption.ALLOW_HALF_CLOSURE, child.isAllowHalfClosure());
        if (child.getRcvBuf() > 0) {
            boot.childOption(ChannelOption.SO_RCVBUF, child.getRcvBuf());
        }
        if (child.getSndBuf() > 0) {
            boot.childOption(ChannelOption.SO_SNDBUF, child.getSndBuf());
        }
        if (child.getLinger() > 0) {
            boot.childOption(ChannelOption.SO_LINGER, child.getLinger());
        }
        if (child.getIpTos() > 0) {
            boot.childOption(ChannelOption.IP_TOS, child.getIpTos());
        }
        boot.option(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(16, 64, 65536));
        boot.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        int bufLowWaterMark = child.getWriteBufferLowWaterMark();
        int bufHighWaterMark = child.getWriteBufferHighWaterMark();
        if (bufLowWaterMark >= 0 && bufHighWaterMark > 0) {
            WriteBufferWaterMark waterMark = new WriteBufferWaterMark(bufLowWaterMark,
                    bufHighWaterMark);
            boot.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark);
        }
    }

    @Override
    public ConfigGroup configGroup() {
        return configGroup;
    }

    @Override
    public void start() throws InterruptedException {
        start(true);
    }

    @Override
    public void start(boolean sync) throws InterruptedException {
        // wait until the server socket is bind succeed.
        ChannelFuture future = null;
        future = bind(localAddress).sync();
        SocketAddress socketAddress = future.channel().localAddress();
        setLocalAddress(socketAddress);
        log.info("TCP 服务启动[{}] {}", getLocalAddress(), (sync ? ", 等待到server socket关闭。" : "。"));
        if (sync) {
            // wait until the server socket is closed.
            future.channel().closeFuture().sync();
        }
    }

    @Override
    public void setIoRatio(int bossIoRatio, int workerIoRatio) {
        EventLoopGroup boss = boss();
        if (boss instanceof EpollEventLoopGroup) {
            ((EpollEventLoopGroup) boss).setIoRatio(bossIoRatio);
        } else if (boss instanceof NioEventLoopGroup) {
            ((NioEventLoopGroup) boss).setIoRatio(bossIoRatio);
        }

        EventLoopGroup worker = worker();
        if (worker instanceof EpollEventLoopGroup) {
            ((EpollEventLoopGroup) worker).setIoRatio(workerIoRatio);
        } else if (worker instanceof NioEventLoopGroup) {
            ((NioEventLoopGroup) worker).setIoRatio(workerIoRatio);
        }
    }

    @Override
    protected EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory) {
        TcpChannelProvider.SocketType socketType = socketType();
        switch (socketType) {
            case NATIVE_EPOLL:
                return new EpollEventLoopGroup(nThreads, tFactory);
            case JAVA_NIO:
                return new NioEventLoopGroup(nThreads, tFactory);
            default:
                throw new IllegalStateException("invalid socket type: " + socketType);
        }
    }

    protected void initChannelFactory() {
        TcpChannelProvider.SocketType socketType = socketType();
        switch (socketType) {
            case NATIVE_EPOLL:
                bootstrap().channelFactory(TcpChannelProvider.NATIVE_EPOLL_ACCEPTOR);
                break;
            case JAVA_NIO:
                bootstrap().channelFactory(TcpChannelProvider.JAVA_NIO_ACCEPTOR);
                break;
            default:
                throw new IllegalStateException("invalid socket type: " + socketType);
        }
    }

    private TcpChannelProvider.SocketType socketType() {
        if (isNative && NativeSupport.isNativeEPollAvailable()) {
            // netty provides the native socket transport for Linux using JNI.
            return TcpChannelProvider.SocketType.NATIVE_EPOLL;
        }
        return TcpChannelProvider.SocketType.JAVA_NIO;
    }

    @Override
    public String toString() {
        return "Socket address:[" + localAddress + ']' + ", socket type: " + socketType()
                + OsUtils.LINE_SEPARATOR + bootstrap();
    }
}
