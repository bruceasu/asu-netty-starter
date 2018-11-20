package io.netty.transport;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Getter
@ToString
public abstract class Connection {

    private final UnresolvedAddress address;
    @Setter
    private volatile boolean                                 connected         = false;
    private volatile boolean                                 complete          = false;
    private          List<ConnectionConnectCompleteListener> completeListeners = new ArrayList<ConnectionConnectCompleteListener>();

    public Connection(UnresolvedAddress address) {
        this.address = address;
    }

    public abstract void setReconnect(boolean reconnect);

    public void setComplete(boolean flag) {
        this.complete = flag;
        for (ConnectionConnectCompleteListener callback : completeListeners) {
            callback.complete(this);
        }
    }

    public void addCompleteLister(ConnectionConnectCompleteListener callback) {
        completeListeners.add(callback);
        if (complete) {
            callback.complete(this);
        }
    }

    public interface ConnectionConnectCompleteListener {

        void complete(Connection connection);
    }

}
