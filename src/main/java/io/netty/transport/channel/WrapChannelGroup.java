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

package io.netty.transport.channel;


import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.transport.UnresolvedAddress;
import io.netty.util.SystemClock;
import io.netty.util.internal.SystemPropertyUtil;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import me.asu.util.AtomicUpdater;

/**
 * @author Suk
 */
public class WrapChannelGroup {

    private static final AtomicReferenceFieldUpdater<CopyOnWriteArrayList, Object[]> CHANNELS_UPDATER      = AtomicUpdater
            .newAtomicReferenceFieldUpdater(CopyOnWriteArrayList.class, Object[].class, "array");
    private static final AtomicIntegerFieldUpdater<WrapChannelGroup>                 SIGNAL_NEEDED_UPDATER = AtomicIntegerFieldUpdater
            .newUpdater(WrapChannelGroup.class, "signalNeeded");
    private static final AtomicIntegerFieldUpdater<WrapChannelGroup>                 INDEX_UPDATER         = AtomicIntegerFieldUpdater
            .newUpdater(WrapChannelGroup.class, "index");
    private static       long                                                        LOSS_INTERVAL         = SystemPropertyUtil
            .getLong("jupiter.io.channel.group.loss.interval.millis", TimeUnit.MINUTES.toMillis(5));
    private final        CopyOnWriteArrayList<WrapChannel>                           channels              = new CopyOnWriteArrayList<WrapChannel>();
    private final        ReentrantLock                                               lock                  = new ReentrantLock();
    private final        Condition                                                   notifyCondition       = lock
            .newCondition();

    private final UnresolvedAddress address;
    // attempts to elide conditional wake-ups when the lock is uncontended.
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int  signalNeeded   = 0; // 0: false, 1: true
    @SuppressWarnings("unused")
    private volatile int  index          = 0;
    private volatile int  capacity       = Integer.MAX_VALUE;
    private volatile int  warmUp         = 600000; // warm-up time
    private volatile long timestamp      = SystemClock.millisClock().now();
    private volatile long deadlineMillis = -1;

    // 连接断开时自动被移除
    private final ChannelFutureListener remover = new ChannelFutureListener() {

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            remove(WrapChannel.attachChannel(future.channel()));
        }
    };

    public WrapChannelGroup(UnresolvedAddress address) {
        this.address = address;
    }

    public UnresolvedAddress remoteAddress() {
        return address;
    }

    public WrapChannel next() {
        for (; ; ) {
            // snapshot of channels array
            Object[] elements = CHANNELS_UPDATER.get(channels);
            int length = elements.length;
            if (length == 0) {
                if (waitForAvailable(1000)) {
                    // wait a moment
                    continue;
                }
                throw new IllegalStateException("no channel");
            }
            if (length == 1) {
                return (WrapChannel) elements[0];
            }

            int index = INDEX_UPDATER.getAndIncrement(this) & Integer.MAX_VALUE;

            return (WrapChannel) elements[index % length];
        }
    }

    public List<? extends WrapChannel> channels() {
        return new ArrayList<WrapChannel>(channels);
    }

    public boolean isEmpty() {
        return channels.isEmpty();
    }

    public boolean add(WrapChannel channel) {
        boolean added = channel instanceof WrapChannel && channels.add(channel);
        if (added) {
            // reset timestamp
            timestamp = SystemClock.millisClock().now();

            channel.channel().closeFuture().addListener(remover);
            deadlineMillis = -1;

            if (SIGNAL_NEEDED_UPDATER.getAndSet(this, 0) != 0) {
                // signal needed: true
                final ReentrantLock _look = lock;
                _look.lock();
                try {
                    // must signal all
                    notifyCondition.signalAll();
                } finally {
                    _look.unlock();
                }
            }
        }
        return added;
    }

    public boolean remove(WrapChannel channel) {
        boolean removed = channel instanceof WrapChannel && channels.remove(channel);
        if (removed) {
            // reset timestamp
            timestamp = SystemClock.millisClock().now();

            if (channels.isEmpty()) {
                deadlineMillis = SystemClock.millisClock().now() + LOSS_INTERVAL;
            }
        }
        return removed;
    }

    public int size() {
        return channels.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isAvailable() {
        return !channels.isEmpty();
    }

    public boolean waitForAvailable(long timeoutMillis) {
        boolean available = isAvailable();
        if (available) {
            return true;
        }
        long remains = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        final ReentrantLock _look = lock;
        _look.lock();
        try {
            // avoid "spurious wakeup" occurs
            while (!(available = isAvailable())) {
                // set signal needed to true
                signalNeeded = 1;
                if ((remains = notifyCondition.awaitNanos(remains)) <= 0) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            _look.unlock();
        }

        return available;
    }

    public int getWarmUp() {
        return warmUp > 0 ? warmUp : 0;
    }

    public void setWarmUp(int warmUp) {
        this.warmUp = warmUp;
    }

    public boolean isWarmUpComplete() {
        return SystemClock.millisClock().now() - timestamp - warmUp > 0;
    }

    public long timestamp() {
        return timestamp;
    }

    public long deadlineMillis() {
        return deadlineMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WrapChannelGroup that = (WrapChannelGroup) o;

        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public String toString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

        return "WrapChannelGroup{" + "channels=" + channels + ", warmUp=" + warmUp + ", time="
                + dateFormat.format(new Date(timestamp)) + ", address=" + address + '}';
    }
}
