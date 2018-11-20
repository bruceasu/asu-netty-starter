/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.netty.transport;

import me.asu.util.NamedThreadFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.transport.Config.ConfigGroup;
import io.netty.transport.estimator.MessageSizeEstimator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.PlatformDependent;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;
import lombok.Getter;
import lombok.Setter;

/**
 * 接入连接处理.
 */
public abstract class Acceptor {

    protected final HashedWheelTimer timer = new HashedWheelTimer(
            new NamedThreadFactory("acceptor.timer"));
    private final int nBosses;
    private final int nWorkers;
    @Getter
    @Setter
    protected SocketAddress localAddress;
    protected volatile ByteBufAllocator allocator;
    private ServerBootstrap bootstrap;
    private EventLoopGroup  boss;
    private EventLoopGroup  worker;

    public Acceptor(SocketAddress localAddress) {
        this(localAddress, Runtime.getRuntime().availableProcessors() << 1 + 1);
    }

    public Acceptor(SocketAddress localAddress, int nWorkers) {
        this(localAddress, Runtime.getRuntime().availableProcessors() + 1, nWorkers);
    }

    public Acceptor(SocketAddress localAddress, int nBosses, int nWorkers) {
        this.localAddress = localAddress;
        this.nBosses = nBosses;
        this.nWorkers = nWorkers;
    }

    protected void init() {
        ThreadFactory bossFactory = bossThreadFactory("acceptor.boss");
        ThreadFactory workerFactory = workerThreadFactory("acceptor.worker");
        boss = initEventLoopGroup(nBosses, bossFactory);
        worker = initEventLoopGroup(nWorkers, workerFactory);

        bootstrap = new ServerBootstrap().group(boss, worker);

        // parent options
        Config parent = configGroup().parent();
        parent.setOption(Option.IO_RATIO, 100);

        // child options
        Config child = configGroup().child();
        child.setOption(Option.IO_RATIO, 100);
        child.setOption(Option.PREFER_DIRECT, true);
        child.setOption(Option.USE_POOLED_ALLOCATOR, true);
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
        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }

    protected ThreadFactory bossThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    protected ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    protected void setOptions() {
        Config parent = configGroup().parent(); // parent options
        Config child = configGroup().child(); // child options

        setIoRatio(parent.getOption(Option.IO_RATIO), child.getOption(Option.IO_RATIO));

        boolean direct = child.getOption(Option.PREFER_DIRECT);
        if (child.getOption(Option.USE_POOLED_ALLOCATOR)) {
            if (direct) {
                allocator = new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());
            } else {
                allocator = new PooledByteBufAllocator(false);
            }
        } else {
            if (direct) {
                allocator = new UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred());
            } else {
                allocator = new UnpooledByteBufAllocator(false);
            }
        }
        bootstrap.childOption(ChannelOption.ALLOCATOR, allocator)
                 .childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, MessageSizeEstimator.DEFAULT);
    }

    /**
     * Which allows easy bootstrap of {@link io.netty.channel.ServerChannel}.
     */
    protected ServerBootstrap bootstrap() {
        return bootstrap;
    }

    /**
     * The {@link EventLoopGroup} which is used to handle all the events for the to-be-creates
     * {@link io.netty.channel.Channel}.
     */
    protected EventLoopGroup boss() {
        return boss;
    }

    /**
     * The {@link EventLoopGroup} for the child. These {@link EventLoopGroup}'s are used to
     * handle all the events and IO for {@link io.netty.channel.Channel}'s.
     */
    protected EventLoopGroup worker() {
        return worker;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.
     * The default value is {@code 50}, which means the event loop will try to spend the same
     * amount of time for I/O as for non-I/O tasks.
     */
    public abstract void setIoRatio(int bossIoRatio, int workerIoRatio);

    /**
     * Create a new {@link io.netty.channel.Channel} and bind it.
     */
    protected abstract ChannelFuture bind(SocketAddress localAddress);

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory}.
     */
    protected abstract EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory);

    /**
     * Acceptor options [parent, child].
     */
    public abstract ConfigGroup configGroup();

    /**
     * 启动服务并等待server socket关闭.
     */
    public abstract void start() throws InterruptedException;

    /**
     * 启动服务.
     */
    public abstract void start(boolean sync) throws InterruptedException;
}
