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

package io.netty.transport.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import me.asu.socket.message.IMessage;
import me.asu.socket.message.ProtoMessage;

/**
 * ProtoMessageDecoder.
 * <code><pre>
 * format:
 *      |---------+---------+---------+---------+----------+--------+--------+-----------|
 *      | 4 bytes | 4 bytes | 4 bytes | 1 byte  | 1 byte   | 1 byte | 1 byte | N byte(s) |
 *      |---------+---------+---------+---------+----------+--------+--------+-----------|
 *      | cmdId   | seqId   | bodyLen | cmdType | bodyType | code   | ttl    | body data |
 *      |---------+---------+---------+---------+----------+--------+--------+-----------|
 * header:
 *      lengthFieldOffset   = 8 pre header
 *      lengthFieldLength   = 4 only data length
 *      lengthAdjustment    = 4 after header
 *      initialBytesToStrip = 0
 * body:
 *      byte array.
 * </pre></code>
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-11 18:05
 */
public class ProtoMessageDecoder extends LengthFieldBasedFrameDecoder {

    public ProtoMessageDecoder() {
        // maxFrameLength 64K
        super(65535, 8, 4, 4, 0);
    }

    public ProtoMessageDecoder(int maxFrameLength) {
        super(maxFrameLength, 8, 4, 4, 0);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf decode = (ByteBuf) super.decode(ctx, in);
        if (decode == null) {
            return decode;
        }

        byte[] bytes;
        int offset;
        int length = decode.readableBytes();

        if (decode.hasArray()) {
            bytes = decode.array();
            offset = decode.arrayOffset();
        } else {
            bytes = new byte[length];
            decode.getBytes(decode.readerIndex(), bytes);
            offset = 0;
        }
        IMessage message = new ProtoMessage();
        message.unpack(bytes, offset, length);
        ReferenceCountUtil.release(decode);
        return message;
    }
}
