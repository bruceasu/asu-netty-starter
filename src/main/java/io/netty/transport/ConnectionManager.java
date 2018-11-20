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

import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接管理器, 用于自动管理(按照地址归组)连接.
 */
@Slf4j
public class ConnectionManager {

    public static final String                                                             TAG         = "【连接管理】";
    private final       ConcurrentMap<UnresolvedAddress, CopyOnWriteArrayList<Connection>> connections = new ConcurrentHashMap<UnresolvedAddress, CopyOnWriteArrayList<Connection>>();

    /**
     * 设置为自动管理连接.
     */
    public void manage(Connection connection) {
        UnresolvedAddress address = connection.getAddress();
        CopyOnWriteArrayList<Connection> list = connections.get(address);
        if (list == null) {
            CopyOnWriteArrayList<Connection> newList = new CopyOnWriteArrayList<Connection>();
            list = connections.putIfAbsent(address, newList);
            if (list == null) {
                list = newList;
            }
        }
        list.add(connection);
    }

    /**
     * 取消对指定地址的自动重连.
     */
    public void cancelReconnect(UnresolvedAddress address) {
        CopyOnWriteArrayList<Connection> list = connections.remove(address);
        if (list != null) {
            for (Connection c : list) {
                c.setReconnect(false);
            }
        }
        log.debug("{}取消重连接到: {}。 \n", TAG, address);
    }

    /**
     * 取消对所有地址的自动重连.
     */
    public void cancelAllReconnect() {
        for (UnresolvedAddress address : connections.keySet()) {
            cancelReconnect(address);
        }
    }
}
