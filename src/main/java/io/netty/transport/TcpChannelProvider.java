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

import io.netty.channel.*;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public final class TcpChannelProvider<T extends Channel> implements ChannelFactory<T> {

    public static final ChannelFactory<ServerChannel> JAVA_NIO_ACCEPTOR     = new TcpChannelProvider<ServerChannel>(
            SocketType.JAVA_NIO, ChannelType.ACCEPTOR);
    public static final ChannelFactory<ServerChannel> NATIVE_EPOLL_ACCEPTOR = new TcpChannelProvider<ServerChannel>(
            SocketType.NATIVE_EPOLL, ChannelType.ACCEPTOR);


    public static final ChannelFactory<Channel> JAVA_NIO_CONNECTOR     = new TcpChannelProvider<Channel>(
            SocketType.JAVA_NIO, ChannelType.CONNECTOR);
    public static final ChannelFactory<Channel> NATIVE_EPOLL_CONNECTOR = new TcpChannelProvider<Channel>(
            SocketType.NATIVE_EPOLL, ChannelType.CONNECTOR);
    private final SocketType  socketType;
    private final ChannelType channelType;

    public TcpChannelProvider(SocketType socketType, ChannelType channelType) {
        this.socketType = socketType;
        this.channelType = channelType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T newChannel() {
        switch (channelType) {
            case ACCEPTOR:
                switch (socketType) {
                    case JAVA_NIO:
                        return (T) new NioServerSocketChannel();
                    case NATIVE_EPOLL:
                        return (T) new EpollServerSocketChannel();
                    default:
                        throw new IllegalStateException("invalid socket type: " + socketType);
                }
            case CONNECTOR:
                switch (socketType) {
                    case JAVA_NIO:
                        return (T) new NioSocketChannel();
                    case NATIVE_EPOLL:
                        return (T) new EpollSocketChannel();
                    default:
                        throw new IllegalStateException("invalid socket type: " + socketType);
                }
            default:
                throw new IllegalStateException("invalid channel type: " + channelType);
        }
    }

    public enum SocketType {
        JAVA_NIO, NATIVE_EPOLL   // for linux
    }

    public enum ChannelType {
        ACCEPTOR, CONNECTOR
    }
}
