package org.yamcs.web.websocket;

import io.protostuff.Schema;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import java.io.IOException;

public interface WebSocketEncoder {

    WebSocketFrame encodeReply(WebSocketReplyData reply) throws IOException;

    WebSocketFrame encodeException(WebSocketException e) throws IOException;

    <T> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> schema) throws IOException;
}
