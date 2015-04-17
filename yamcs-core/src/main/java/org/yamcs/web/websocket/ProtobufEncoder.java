package org.yamcs.web.websocket;

import io.protostuff.Schema;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.MessageType;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;

import java.io.IOException;

public class ProtobufEncoder implements WebSocketEncoder {

    @Override
    public WebSocketFrame encodeException(WebSocketException e) throws IOException {
        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.EXCEPTION)
                .setException(e.toWebSocketExceptionData())
                .build();
        return toFrame(serverMessage);
    }

    @Override
    public WebSocketFrame encodeReply(WebSocketReplyData reply) throws IOException {
        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.REPLY)
                .setReply(reply)
                .build();
        return toFrame(serverMessage);
    }

    @Override
    public <T> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> schema) throws IOException {
        WebSocketSubscriptionData.Builder responseb = WebSocketSubscriptionData.newBuilder();
        responseb.setSequenceNumber(sequenceNumber);
        responseb.setType(dataType);
        if (dataType == ProtoDataType.CMD_HISTORY) {
            responseb.setCommand((CommandHistoryEntry) message);
        } else if (dataType == ProtoDataType.PARAMETER) {
            responseb.setParameterData((ParameterData) message);
        } else {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }

        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.DATA)
                .setData(responseb)
                .build();
        return toFrame(serverMessage);
    }

    private static BinaryWebSocketFrame toFrame(WebSocketServerMessage message) throws IOException {
        // TODO This assumes that the frame is quite small (which it should be, but)
	ByteBuf buf = Unpooled.buffer();
        try (ByteBufOutputStream cout = new ByteBufOutputStream(buf)) {
            message.writeTo(cout);
        }
        return new BinaryWebSocketFrame(buf);
    }
}
