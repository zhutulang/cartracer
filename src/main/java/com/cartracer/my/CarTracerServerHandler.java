package com.cartracer.my;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaders.setContentLength;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * CarTracerServerHandler
 */
public class CarTracerServerHandler extends SimpleChannelInboundHandler<Object> {

	private static final String webSocketUrl = "ws://localhost:9999/websocket";

	private static final String HEART_BEAT_MSG = "I am alive";

    private static final Logger logger = Logger.getLogger(CarTracerServerHandler.class.getName());

    private WebSocketServerHandshaker handshaker = null;

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		AudienceListener.addAudience(ctx);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		logger.info("handler removed:"+ctx.hashCode());
		AudienceListener.removeAudience(ctx);
	}

	@Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
		// 传统的HTTP接入
		if (msg instanceof FullHttpRequest) {
		    handleHttpRequest(ctx, (FullHttpRequest) msg);
		}
		// WebSocket接入
		else if (msg instanceof WebSocketFrame) {
		    handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
	    ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
		// 如果HTTP解码失败，返回HHTP异常
		if (!req.getDecoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
		    sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
		    return;
		}
		// 构造握手响应返回，本机测试
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(webSocketUrl, null, false);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
		    WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
		} else {
		    handshaker.handshake(ctx.channel(), req);
		}
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
	    // 判断是否是关闭链路的指令
	    if (frame instanceof CloseWebSocketFrame) {
		    handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
		    return;
	    }

	    // 仅支持文本消息，不支持二进制消息
	    if (!(frame instanceof TextWebSocketFrame)) {
		    throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
	    }

	    // 如果是心跳消息，更新时间戳
	    String clientSendMsg = ((TextWebSocketFrame) frame).text();
	    if (HEART_BEAT_MSG.equalsIgnoreCase(clientSendMsg)) {
		    AudienceListener.updateAudience(ctx);
	    }
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		// 返回应答给客户端
		if (res.getStatus().code() != 200) {
		    ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(),
			    CharsetUtil.UTF_8);
		    res.content().writeBytes(buf);
		    buf.release();
		    setContentLength(res, res.content().readableBytes());
		}

		// 如果是非Keep-Alive，关闭连接
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!isKeepAlive(req) || res.getStatus().code() != 200) {
		    f.addListener(ChannelFutureListener.CLOSE);
		}
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
    }
}
