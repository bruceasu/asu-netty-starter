package io.netty.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.OsUtils;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.asu.util.NamedThreadFactory;

/**
 * @author suk
 */
@Slf4j
public class UdpAcceptor {

    private static final int              BYTES_1M = 1024 * 1024;
    protected final      HashedWheelTimer timer    = new HashedWheelTimer(
            new NamedThreadFactory("acceptor.timer"));
    /**
     * use native transport
     */
    private final      boolean                      isNative;
    private final      int                          nWorkers;
    @Getter
    @Setter
    protected          SocketAddress                localAddress;
    protected volatile ByteBufAllocator             allocator;
    @Getter
    @Setter
    private            ChannelInboundHandlerAdapter handler;
    private            Bootstrap                    bootstrap;
    private            EventLoopGroup               worker;


    public UdpAcceptor(int port) {
        this(new InetSocketAddress(port));
    }

    public UdpAcceptor(SocketAddress localAddress) {
        this(localAddress, 0);
    }

    public UdpAcceptor(int port, int nWorkers) {
        this(new InetSocketAddress(port), nWorkers);
    }

    public UdpAcceptor(SocketAddress localAddress, int nWorkers) {
        this(localAddress, nWorkers, false);
    }

    public UdpAcceptor(int port, boolean isNative) {
        this(new InetSocketAddress(port), 0, isNative);
    }

    public UdpAcceptor(SocketAddress localAddress, int nWorkers, boolean isNative) {
        this.localAddress = localAddress;
        if (nWorkers < 1) {
            this.nWorkers = Runtime.getRuntime().availableProcessors() << 1;
        } else {
            this.nWorkers = nWorkers;
        }
        this.isNative = isNative;
        ThreadFactory workerFactory = workerThreadFactory("acceptor.worker");
        worker = initEventLoopGroup(this.nWorkers, workerFactory);

        bootstrap = new Bootstrap().group(worker)
                                   // 支持广播
                                   .option(ChannelOption.SO_BROADCAST, Boolean.TRUE)
                                   .option(ChannelOption.SO_BACKLOG, 128)
                                   // 设置UDP读缓冲区为1M
                                   .option(ChannelOption.SO_RCVBUF, BYTES_1M)
                                   // 设置UDP写缓冲区为1M
                                   .option(ChannelOption.SO_SNDBUF, BYTES_1M);
        initChannelClass();
    }

    public ChannelFuture bind(final SocketAddress localAddress) {
        bootstrap.handler(new ChannelInitializer<NioDatagramChannel>() {
            @Override
            protected void initChannel(NioDatagramChannel ch) throws Exception {
                ch.pipeline().addLast("framer", new MessageToMessageDecoder<DatagramPacket>() {
                    @Override
                    protected void decode(ChannelHandlerContext ctx,
                                          DatagramPacket msg,
                                          List<Object> out) throws Exception {
                        out.add(msg.content().toString(Charset.forName("UTF-8")));
                    }
                }).addLast("handler", handler);
            }
        });

        return bootstrap.bind(localAddress);
    }

    public void start() throws InterruptedException {
        start(true);
    }

    public void start(boolean sync) throws InterruptedException {
        // wait until the server socket is bind succeed.
        ChannelFuture future = null;
        future = bind(localAddress).sync();
        SocketAddress socketAddress = future.channel().localAddress();
        setLocalAddress(socketAddress);
        log.info("UDP 服务启动[{}] {}", getLocalAddress(), (sync ? ", 等待到server socket关闭。" : "。"));
        if (sync) {
            // wait until the server socket is closed.
            future.channel().closeFuture().sync();
        }
    }

    public SocketAddress localAddress() {
        return localAddress;
    }

    public int boundPort() {
        if (!(localAddress instanceof InetSocketAddress)) {
            throw new UnsupportedOperationException("此地址不支持获取端口。");
        }
        return ((InetSocketAddress) localAddress).getPort();
    }

    public void shutdownGracefully() {
        worker.shutdownGracefully();
    }

    @Override
    public String toString() {
        return "Socket address:[" + localAddress + ']' + ", socket is native: " + (isNative
                && NativeSupport.isNativeEPollAvailable()) + OsUtils.LINE_SEPARATOR + bootstrap;
    }

    private ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    private void initChannelClass() {
        if (isNative && NativeSupport.isNativeEPollAvailable()) {
            bootstrap.channel(EpollDatagramChannel.class);
        } else {
            bootstrap.channel(NioDatagramChannel.class);
        }
    }

    private EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory) {
        if (isNative && NativeSupport.isNativeEPollAvailable()) {
            return new EpollEventLoopGroup(nThreads, tFactory);
        } else {
            return new NioEventLoopGroup(nThreads, tFactory);
        }
    }

}
