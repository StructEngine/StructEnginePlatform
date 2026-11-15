package com.structengine.platform.game.support;

import com.structengine.platform.game.protocol.GameMessage;
import com.structengine.platform.game.ws.GameServerChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 单元测试用游戏服连接：可记录下发消息、模拟断开、自动响应 QUERY_Y。
 */
public class FakeGameServerChannel implements GameServerChannel {

    private final String serverId;
    private final List<GameMessage> sent = new ArrayList<>();
    private boolean open = true;
    private boolean sendResult = true;
    private Consumer<GameMessage> onSend;

    public FakeGameServerChannel(String serverId) {
        this.serverId = serverId;
    }

    public FakeGameServerChannel onSend(Consumer<GameMessage> onSend) {
        this.onSend = onSend;
        return this;
    }

    public FakeGameServerChannel sendResult(boolean sendResult) {
        this.sendResult = sendResult;
        return this;
    }

    public void disconnect() {
        this.open = false;
    }

    public List<GameMessage> sent() {
        return sent;
    }

    public GameMessage lastSent() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public boolean send(GameMessage message) {
        if (!open || !sendResult) {
            return false;
        }
        sent.add(message);
        if (onSend != null) {
            onSend.accept(message);
        }
        return true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close(String reason) {
        open = false;
    }
}
