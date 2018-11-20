package io.netty.transport.processor;

import io.netty.transport.channel.WrapChannel;
import me.asu.socket.message.IMessage;

/**
 * Consumer's processor.
 */
public interface ConsumerProcessor {

    void handleResponse(WrapChannel channel, IMessage response) throws Exception;
}
