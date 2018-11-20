/*
 * Copyright (c) 2017 Suk Honzeon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.netty.transport;


import me.asu.util.NamedThreadFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.*;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.transport.channel.WrapChannelGroup;
import io.netty.transport.estimator.MessageSizeEstimator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.PlatformDependent;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-12 11:24
 */
@lombok.Data
public abstract class Connector {

    protected final HashedWheelTimer                                   timer             = new HashedWheelTimer(
            new NamedThreadFactory("connector.timer"));
    protected final ConnectionManager                                  connectionManager = new ConnectionManager();
    private final   ConcurrentMap<UnresolvedAddress, WrapChannelGroup> addressGroups     = new ConcurrentHashMap<UnresolvedAddress, WrapChannelGroup>();
    protected          EventLoopGroup   workerGroup;
    protected          Bootstrap        bootstrap;
    protected          EventLoopGroup   worker;
    protected volatile ByteBufAllocator allocator;
    protected int nWorkers = Runtime.getRuntime().availableProcessors() * 2 - 1;

    public Connector() {
    }

    public Connector(int nWorkers) {
        this.nWorkers = nWorkers;
    }

    public void init() {
        ThreadFactory workerFactory = workerThreadFactory("netty.connector");
        worker = initEventLoopGroup(nWorkers, workerFactory);

        bootstrap = new Bootstrap().group(worker);

        Config child = config();
        child.setOption(Option.IO_RATIO, 100);
        child.setOption(Option.PREFER_DIRECT, true);
        child.setOption(Option.USE_POOLED_ALLOCATOR, true);

        doInit();
    }

    public ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    public void shutdownGracefully() {
        connectionManager.cancelAllReconnect();
        worker.shutdownGracefully();
        timer.stop();
    }


    public void setOptions() {
        Config child = config();

        setIoRatio(child.getOption(Option.IO_RATIO));

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
        bootstrap.option(ChannelOption.ALLOCATOR, allocator)
                 .option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, MessageSizeEstimator.DEFAULT);
    }

    /**
     * The {@link EventLoopGroup} for the child. These {@link EventLoopGroup}'s are used to handle
     * all the events and IO for {@link io.netty.channel.Channel}'s.
     */
    public EventLoopGroup worker() {
        return worker;
    }

    public WrapChannelGroup group(UnresolvedAddress address) {
        Objects.requireNonNull(address);

        WrapChannelGroup group = addressGroups.get(address);
        if (group == null) {
            WrapChannelGroup newGroup = channelGroup(address);
            group = addressGroups.putIfAbsent(address, newGroup);
            if (group == null) {
                group = newGroup;
            }
        }
        return group;
    }

    /**
     * Creates the same address of the channel group.
     */
    protected WrapChannelGroup channelGroup(UnresolvedAddress address) {
        return new WrapChannelGroup(address);
    }

    public Collection<WrapChannelGroup> groups() {
        return addressGroups.values();
    }

    public boolean isConnectionAvailable(UnresolvedAddress address) {
        return group(address).isAvailable();
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory}.
     */
    public abstract EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory);

    /**
     * Connector options [parent, child].
     */
    public abstract Config config();

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.
     * The default value is {@code 50}, which means the event loop will try to spend the same
     * amount of time for I/O as for non-I/O tasks.
     */
    public abstract void setIoRatio(int workerIoRatio);

    protected abstract void doInit();


}
