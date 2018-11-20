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

import io.netty.channel.*;
import io.netty.transport.handler.connector.ConnectionWatchdog;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.SocketAddress;
import lombok.extern.slf4j.Slf4j;

/**
 * 对Netty {@link Channel} 的包装, 通过静态方法 {@link #attachChannel(Channel)} 获取一个实例,
 * {@link WrapChannel} 实例构造后会attach到对应 {@link Channel} 上, 不需要每次创建.
 * @author Suk
 */
@Slf4j
public class WrapChannel {

    private static final AttributeKey<WrapChannel>   NETTY_CHANNEL_KEY = AttributeKey.valueOf("netty.channel");
    public static        FutureListener<WrapChannel> CLOSE             = new FutureListener<WrapChannel>() {

        @Override
        public void operationSuccess(WrapChannel channel) throws Exception {
            channel.close();
        }

        @Override
        public void operationFailure(WrapChannel channel, Throwable cause) throws Exception {
            channel.close();
        }
    };

    private final Channel channel;

    private WrapChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * Returns the {@link WrapChannel} for given {@link Channel}, this method never return null.
     */
    public static WrapChannel attachChannel(Channel channel) {
        Attribute<WrapChannel> attr = channel.attr(NETTY_CHANNEL_KEY);
        WrapChannel wrapChannel = attr.get();
        if (wrapChannel == null) {
            WrapChannel newNChannel = new WrapChannel(channel);
            wrapChannel = attr.setIfAbsent(newNChannel);
            if (wrapChannel == null) {
                wrapChannel = newNChannel;
            }
        }
        return wrapChannel;
    }

    public Channel channel() {
        return channel;
    }

    public String id() {
        // 注意这里的id并不是全局唯一, 单节点中是唯一的
        return channel.id().asShortText();
    }

    /**
     * close {@link Channel}
     * @return self {@link WrapChannel}
     */
    public WrapChannel close() {
        channel.close();
        return this;
    }

    /**
     * close {@link Channel}
     * @param listener {@link FutureListener}
     * @return self {@link WrapChannel}
     */
    public WrapChannel close(final FutureListener<WrapChannel> listener) {
        final WrapChannel wrapChannel = this;
        channel.close().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    listener.operationSuccess(wrapChannel);
                } else {
                    listener.operationFailure(wrapChannel, future.cause());
                }
            }
        });
        return wrapChannel;
    }

    /**
     * send message
     * @param msg message
     * @return self {@link WrapChannel}
     */
    public WrapChannel write(Object msg) {
        channel.writeAndFlush(msg, channel.voidPromise());
        return this;
    }

    /**
     * send message
     * @param msg message
     * @param listener {@link FutureListener}
     *
     * @return self {@link WrapChannel}
     */
    public WrapChannel write(final Object msg, final FutureListener<WrapChannel> listener) {
        final WrapChannel wrapChannel = this;
        channel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    listener.operationSuccess(wrapChannel);
                } else {
                    listener.operationFailure(wrapChannel, future.cause());
                }
            }
        });
        return wrapChannel;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof WrapChannel && channel
                .equals(((WrapChannel) obj).channel));
    }

    public boolean isActive() {
        return channel.isActive();
    }

    public boolean inIoThread() {
        return channel.eventLoop().inEventLoop();
    }

    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    public boolean isWritable() {
        return channel.isWritable();
    }

    public boolean isMarkedReconnect() {
        ConnectionWatchdog watchdog = channel.pipeline().get(ConnectionWatchdog.class);
        return watchdog != null && watchdog.isStarted();
    }

    public boolean isAutoRead() {
        return channel.config().isAutoRead();
    }

    public void setAutoRead(boolean autoRead) {
        channel.config().setAutoRead(autoRead);
    }

    @Override
    public int hashCode() {
        return channel.hashCode();
    }

    @Override
    public String toString() {
        return channel.toString();
    }
}
