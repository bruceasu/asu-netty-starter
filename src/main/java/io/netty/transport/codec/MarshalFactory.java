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

package io.netty.transport.codec;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * MarshalFactory.
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-11 15:36
 */
@Slf4j
public class MarshalFactory {

    private static ConcurrentHashMap<Integer, Marshal> cache = new ConcurrentHashMap<Integer, Marshal>();

    static {
        ServiceLoader<Marshal> loader = ServiceLoader.load(Marshal.class);
        Iterator<Marshal> iterator = loader.iterator();
        while(iterator.hasNext()) {
            Marshal next = iterator.next();
            addMarshal(next);
        }
    }

    public static void addMarshal(Marshal marshal) {
        if (marshal == null) {
            return;
        }
        int type = marshal.type();
        if (cache.containsKey(type)) {
            log.warn("Marshal(type={}) 已经存在，将用新的Marshal替换。", type);
        }
        cache.put(type, marshal);
    }

    public static Marshal get(int type) {
        Marshal marshal = cache.get(type);
        if (marshal == null) {
            throw new UnsupportedOperationException("Not implement yet!");
        }
        return marshal;
    }

}
