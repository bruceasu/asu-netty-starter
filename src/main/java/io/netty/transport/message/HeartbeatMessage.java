package io.netty.transport.message;


import me.asu.socket.message.ProtoMessage;

/**
 * Shared heartbeat content.
 *
 * @author Suk
 */
public class HeartbeatMessage extends ProtoMessage {

    private HeartbeatMessage() {
        getHeader().setCmdId(0);
        getHeader().setSeqId(0);
        getHeader().setBodyLen(0);
        getHeader().setBodyType((byte) 0);
        getHeader().setCode((byte) 0);
        getHeader().setTtl((byte) 0);
    }

    public static HeartbeatMessage getInstance() {
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {

        static HeartbeatMessage instance = new HeartbeatMessage();
    }
}
