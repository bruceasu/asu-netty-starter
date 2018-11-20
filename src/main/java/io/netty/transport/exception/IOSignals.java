package io.netty.transport.exception;

/**
 * {@link Signal} has an empty stack trace, you can throw them just like using goto.
 *
 * 当全局goto用的, {@link Signal}有一个空堆栈，你可以像使用goto一样抛出它们.
 */
@SuppressWarnings("all")
public class IOSignals {

    /**
     * 错误的消息标志位
     */
    public static final Signal ILLEGAL_SIGN   = Signal.valueOf(IOSignals.class, "ILLEGAL_SIGN");
    /**
     * Read idle 链路检测
     */
    public static final Signal READER_IDLE    = Signal.valueOf(IOSignals.class, "READER_IDLE");
    /**
     * Protocol body 太大
     */
    public static final Signal BODY_TOO_LARGE = Signal.valueOf(IOSignals.class, "BODY_TOO_LARGE");
}
