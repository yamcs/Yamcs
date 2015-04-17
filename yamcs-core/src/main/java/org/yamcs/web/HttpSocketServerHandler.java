package org.yamcs.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.web.rest.ApiRequestHandler;
import org.yamcs.web.websocket.WebSocketServerHandler;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles handshakes and messages
 */
public class HttpSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    //the request to get the list of displays goes here
    public static final String DISPLAYS_PATH = "displays";
    public static final String STATIC_PATH = "_static";
    public static final String API_PATH = "api";

    final static Logger log=LoggerFactory.getLogger(HttpSocketServerHandler.class.getName());

    static StaticFileRequestHandler fileRequestHandler=new StaticFileRequestHandler();
    static ApiRequestHandler apiRequestHandler=new ApiRequestHandler();
    static DisplayRequestHandler displayRequestHandler=new DisplayRequestHandler(fileRequestHandler);
    WebSocketServerHandler webSocketHandler= new WebSocketServerHandler();
    
    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            webSocketHandler.handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        log.debug("{} {}", req.getMethod(), req.getUri());

        if (req.getUri().equals("favicon.ico")) { //TODO send the sugarcube
            sendNegativeHttpResponse(ctx, req, NOT_FOUND);
            return;
        }
        String uri;
        try {
            uri = URLDecoder.decode(req.getUri(), "UTF-8");
        } catch (UnsupportedEncodingException e1) {
            log.warn("Cannot decode uri", e1);
            sendNegativeHttpResponse(ctx, req, FORBIDDEN);
            return;
        }
        String[] path=uri.split("/",3); //uri starts with / so path[0] is always empty
        if(path.length==1) {
            sendNegativeHttpResponse(ctx, req, NOT_FOUND);
            return;
        }
        if(STATIC_PATH.equals(path[1])) {
            if(path.length==2) { //do not accept "/_static/" (i.e. directory listing) requests 
                sendNegativeHttpResponse(ctx, req, FORBIDDEN);
                return;
            }
            fileRequestHandler.handleStaticFileRequest(ctx, req, path[2]);
            return;
        }

        String yamcsInstance=path[1];

        if(!HttpSocketServer.getInstance().isInstanceRegistered(yamcsInstance)) {
        	log.warn("Received request for unregistered (or unexisting) instance '{}'", yamcsInstance);
            sendNegativeHttpResponse(ctx, req, NOT_FOUND);
            return;
        }
        if((path.length==2) || path[2].isEmpty() || path[2].equals("index.html")) {
            fileRequestHandler.handleStaticFileRequest(ctx, req, "index.html");
            return;
        }
        
        String[] rpath = path[2].split("/",2);
        String handler=rpath[0];
        if(WebSocketServerHandler.WEBSOCKET_PATH.equals(handler)) {
            webSocketHandler.handleHttpRequest(ctx, req, yamcsInstance);
        } else if(DISPLAYS_PATH.equals(handler)) {
            displayRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? rpath[1] : null);
        } else if(API_PATH.equals(handler)) {
            apiRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? rpath[1] : null);
        } else {
        	log.warn("Unknown handler {}", handler);
        	sendNegativeHttpResponse(ctx, req, NOT_FOUND);
        }
    }
    
    private void sendNegativeHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponseStatus status) {
        ByteBuf buf = Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8);
        
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, status, buf);
        if (!isKeepAlive(req)) {
            setContentLength(res, buf.readableBytes());
        }

        ChannelFuture f = ctx.writeAndFlush(res);
        if (!isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
