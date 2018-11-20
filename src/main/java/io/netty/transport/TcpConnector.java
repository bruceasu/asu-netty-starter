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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.transport.Config.ConfigGroup;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.Data;

/**
 * TcpConnector
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-13 13:57
 */
@Data
public abstract class TcpConnector extends Connector {

    protected final ConfigGroup.ChildConfig childConfig = new ConfigGroup.ChildConfig();
    protected       boolean                 isNative    = true;

    public TcpConnector() {
        super();
        init();
    }

    public TcpConnector(boolean isNative) {
        super();
        this.isNative = isNative;
        init();
    }

    public TcpConnector(boolean isNative, int nWorker) {
        super(nWorker);
        this.isNative = isNative;
        init();
    }

    @Override
    public void setOptions() {
        super.setOptions();

        Bootstrap boot = getBootstrap();

        ConfigGroup.ChildConfig child = childConfig;

        // child options
        boot.option(ChannelOption.SO_REUSEADDR, child.isReuseAddress())
            .option(ChannelOption.SO_KEEPALIVE, child.isKeepAlive())
            .option(ChannelOption.TCP_NODELAY, child.isTcpNoDelay())
            .option(ChannelOption.ALLOW_HALF_CLOSURE, child.isAllowHalfClosure());
        if (child.getRcvBuf() > 0) {
            boot.option(ChannelOption.SO_RCVBUF, child.getRcvBuf());
        }
        if (child.getSndBuf() > 0) {
            boot.option(ChannelOption.SO_SNDBUF, child.getSndBuf());
        }
        if (child.getLinger() > 0) {
            boot.option(ChannelOption.SO_LINGER, child.getLinger());
        }
        if (child.getIpTos() > 0) {
            boot.option(ChannelOption.IP_TOS, child.getIpTos());
        }
        if (child.getConnectTimeoutMillis() > 0) {
            boot.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, child.getConnectTimeoutMillis());
        }
        int bufLowWaterMark = child.getWriteBufferLowWaterMark();
        int bufHighWaterMark = child.getWriteBufferHighWaterMark();
        if (bufLowWaterMark >= 0 && bufHighWaterMark > 0) {
            WriteBufferWaterMark waterMark = new WriteBufferWaterMark(bufLowWaterMark,
                    bufHighWaterMark);
            boot.option(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark);
        }
    }

    @Override
    public Config config() {
        return childConfig;
    }

    @Override
    public void setIoRatio(int workerIoRatio) {
        EventLoopGroup worker = worker();
        if (worker instanceof EpollEventLoopGroup) {
            ((EpollEventLoopGroup) worker).setIoRatio(workerIoRatio);
        } else if (worker instanceof NioEventLoopGroup) {
            ((NioEventLoopGroup) worker).setIoRatio(workerIoRatio);
        }
    }

    @Override
    public EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory) {
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

    public void initChannelFactory() {
        TcpChannelProvider.SocketType socketType = socketType();
        switch (socketType) {
            case NATIVE_EPOLL:
                getBootstrap().channelFactory(TcpChannelProvider.NATIVE_EPOLL_CONNECTOR);
                break;
            case JAVA_NIO:
                getBootstrap().channelFactory(TcpChannelProvider.JAVA_NIO_CONNECTOR);
                break;
            default:
                throw new IllegalStateException("invalid socket type: " + socketType);
        }
    }

    public TcpChannelProvider.SocketType socketType() {
        if (isNative && NativeSupport.isNativeEPollAvailable()) {
            // netty provides the native socket transport for Linux using JNI.
            return TcpChannelProvider.SocketType.NATIVE_EPOLL;
        }
        return TcpChannelProvider.SocketType.JAVA_NIO;
    }

    @Override
    protected void doInit() {
        // child options
        config().setOption(Option.SO_REUSEADDR, true);
        config().setOption(Option.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(3));
        // channel factory
        initChannelFactory();
    }

    public Connection connect(UnresolvedAddress address) {
        return connect(address, false);
    }

    public abstract Connection connect(UnresolvedAddress address, boolean async);
}
