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


import io.netty.channel.*;
import io.netty.transport.channel.WrapChannel;
import io.netty.transport.exception.Signal;
import io.netty.transport.processor.ConsumerProcessor;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.asu.socket.message.IMessage;


/**
 * <p>2017 Suk All rights reserved.</p>
 *
 * @author Suk
 * @version 1.0.0
 * @since 2017-09-12 14:17
 */
@ChannelHandler.Sharable
@Data
@Slf4j
public class ConnectorHandler extends ChannelInboundHandlerAdapter {

    private ConsumerProcessor processor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (msg instanceof IMessage && processor != null) {
            try {
                processor.handleResponse(WrapChannel.attachChannel(ch), (IMessage) msg);
            } catch (Throwable t) {
                log.error("发生错误: {}, 在 {} #channelRead()。", t.getMessage(), ch);
            }
        } else {
            log.warn("接收到不支持的报文: {}, channel: {}。", msg.getClass(), ch);
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        // 高水位线: ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK
        // 低水位线: ChannelOption.WRITE_BUFFER_LOW_WATER_MARK
        if (!ch.isWritable()) {
            // 当前channel的缓冲区(OutboundBuffer)大小超过了WRITE_BUFFER_HIGH_WATER_MARK
            log.warn("{} is not writable, high water mask: {}, "
                            + "the number of flushed entries that are not written yet: {}.", ch,
                    config.getWriteBufferHighWaterMark(), ch.unsafe().outboundBuffer().size());

            config.setAutoRead(false);
        } else {
            // 曾经高于高水位线的OutboundBuffer现在已经低于WRITE_BUFFER_LOW_WATER_MARK了
            log.warn("{} is writable(rehabilitate), low water mask: {}, "
                            + "the number of flushed entries that are not written yet: {}.", ch,
                    config.getWriteBufferLowWaterMark(), ch.unsafe().outboundBuffer().size());

            config.setAutoRead(true);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel ch = ctx.channel();

        if (cause instanceof Signal) {
            log.error("捕获Signal: {}, 关闭通道: {}.", ((Signal) cause).name(), ch);
            ch.close();
        } else if (cause instanceof IOException) {
            log.error("捕获IOException: {}, 关闭通道: {}.", cause.getMessage(), ch);
            ch.close();
        } else {
            log.error("捕获未知异常: {}, 通道: {}.", cause.getMessage(), ch);
        }
    }
}
