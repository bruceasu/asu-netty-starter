package io.netty.transport.handler.acceptor;


import io.netty.channel.*;
import io.netty.transport.channel.WrapChannel;
import io.netty.transport.exception.Signal;
import io.netty.transport.processor.ProviderProcessor;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import me.asu.socket.message.IProtoMessage;
import me.asu.socket.message.ProtoMessage;

/**
 * @author Suk
 */
@Slf4j
@ChannelHandler.Sharable
public class AcceptorHandler extends ChannelInboundHandlerAdapter {


    private static final AtomicInteger channelCounter    = new AtomicInteger(0);
    private final AtomicInteger              connectionCounter = new AtomicInteger(0);
    private ProviderProcessor processor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();

        if (msg instanceof ProtoMessage && processor != null) {
            WrapChannel channel = WrapChannel.attachChannel(ch);
            try {
                processor.handleRequest(channel, (IProtoMessage) msg);
            } catch (Throwable t) {
                processor.handleException(channel, (ProtoMessage) msg, 1, t);
            }
        } else {
            log.warn("接收到不支持的报文: {}, channel: {}.", msg.getClass(), ch);
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int count = channelCounter.incrementAndGet();
        log.debug("连接第（{}）个通道（{}）", count, ctx.channel());
        connectionCounter.incrementAndGet();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        int count = channelCounter.getAndDecrement();
        log.debug("断开第（{}）个通道（{}）", count, ctx.channel());
        connectionCounter.decrementAndGet();
        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        ChannelConfig config = ch.config();

        // 高水位线: ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK
        // 低水位线: ChannelOption.WRITE_BUFFER_LOW_WATER_MARK
        if (!ch.isWritable()) {
            // 当前channel的缓冲区(OutboundBuffer)大小超过了WRITE_BUFFER_HIGH_WATER_MARK
            log.warn(
                    "{} is not writable, high water mask: {}, the number of flushed entries that are not written yet: {}.",
                    ch, config.getWriteBufferHighWaterMark(), ch.unsafe().outboundBuffer().size());

            config.setAutoRead(false);
        } else {
            // 曾经高于高水位线的OutboundBuffer现在已经低于WRITE_BUFFER_LOW_WATER_MARK了
            log.warn(
                    "{} is writable(rehabilitate), low water mask: {}, the number of flushed entries that are not written yet: {}.",
                    ch, config.getWriteBufferLowWaterMark(), ch.unsafe().outboundBuffer().size());

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
            ch.close();
        }
    }

    public ProviderProcessor processor() {
        return processor;
    }

    public void processor(ProviderProcessor processor) {
        this.processor = processor;
    }
}
