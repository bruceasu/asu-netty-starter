package io.netty.transport.processor;


import io.netty.transport.channel.WrapChannel;
import me.asu.socket.message.IMessage;

/**
 * Provider's processor.
 */
public interface ProviderProcessor {

    /**
     * 处理正常请求.
     */
    void handleRequest(WrapChannel channel, IMessage request) throws Exception;

    /**
     * 处理异常.
     */
    void handleException(WrapChannel channel, IMessage request, int status, Throwable cause);
}
