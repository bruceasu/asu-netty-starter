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

package io.netty.transport.handler.connector;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.transport.channel.WrapChannel;
import io.netty.transport.channel.WrapChannelGroup;
import io.netty.transport.handler.ChannelHandlerHolder;
import io.netty.util.*;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import me.asu.util.NamedThreadFactory;


/**
 * Connections watchdog.
 *
 * @author Suk
 */
@Slf4j
@ChannelHandler.Sharable
public abstract class ConnectionWatchdog extends ChannelInboundHandlerAdapter
        implements TimerTask, ChannelHandlerHolder {

    public static final String TAG = "【连接狗】";

    private static final int ST_STARTED = 1;
    private static final int ST_STOPPED = 2;

    private final Bootstrap        bootstrap;
    private final Timer            timer;
    private final SocketAddress    remoteAddress;
    private final WrapChannelGroup group;

    private volatile int state = ST_STARTED;
    private int attempts;
    private List<ReconnectedListener> listeners = new ArrayList<ReconnectedListener>();
    private ExecutorService es = Executors.newSingleThreadExecutor(new NamedThreadFactory("ConnectionWatchdog-Notify-Thread"));
    public ConnectionWatchdog(Bootstrap bootstrap,
                              Timer timer,
                              SocketAddress remoteAddress,
                              WrapChannelGroup group) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.remoteAddress = remoteAddress;
        this.group = group;
    }

    public boolean isStarted() {
        return state == ST_STARTED;
    }

    public void start() {
        state = ST_STARTED;
    }

    public void stop() {
        state = ST_STOPPED;
        if (es != null) {
            es.shutdown();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        if (group != null) {
            group.add(WrapChannel.attachChannel(ch));
        }
        attempts = 0;
        log.debug("{} 连接 {}.", TAG, ch);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean doReconnect = isReconnectNeeded();
        if (doReconnect) {
            attempts++;
            long timeout = 200 * (attempts > 12 ? 12 : attempts);
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
        }
        log.debug("{} 断开连接（{}）, 地址: {}, 重连标识: {}.", TAG, ctx.channel(), remoteAddress, doReconnect);
        ctx.fireChannelInactive();
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        if (!isReconnectNeeded()) {
            log.warn("{} 此地址（{}）已经取消重连。", TAG, remoteAddress);
            return;
        }
        final ChannelFuture future;
        synchronized (bootstrap) {
            bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelHandler[] handlers = handlers();
                    ch.pipeline().addLast(handlers);
                }
            });
            future = bootstrap.connect(remoteAddress);
        }

        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                boolean succeed = f.isSuccess();
                log.warn("{} {}重新连接{}：{}。", TAG, f.channel(), remoteAddress, succeed ? "成功" : "失败");
                if (!succeed) {
                    f.channel().pipeline().fireChannelInactive();
                    for (ReconnectedListener listener : listeners) {
                        listener.operationComplete(false, attempts, ConnectionWatchdog.this);
                    }
                    future.channel().close();
                } else {
                    notifyReconnected();
                }
            }
        });
    }

    public void addReconnectListener(ReconnectedListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeReconnectListener(ReconnectedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private boolean isReconnectNeeded() {
        return isStarted() && (group == null || (group.size() < group.getCapacity()));
    }

    private void notifyReconnected() {
        es.submit(new Runnable() {
            @Override
            public void run() {
                for (ReconnectedListener listener : listeners) {
                    try {
                        listener.operationComplete(true, attempts, ConnectionWatchdog.this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public interface ReconnectedListener {

        void operationComplete(boolean flag, int attempts, ConnectionWatchdog dog) throws Exception;
    }
}
