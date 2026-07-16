package com.example.demo.handler;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class ExceptionHandler extends ChannelInboundHandlerAdapter {

    private static final Gson gson = new Gson();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String clientIp = getClientIp(ctx);
        log.error("Exception caught from {}: {}", clientIp, cause.getMessage(), cause);

        if (ctx.channel().isActive()) {
            sendErrorResponse(ctx);
        }

        ctx.close();
    }

    private void sendErrorResponse(ChannelHandlerContext ctx) {
        try {
            Map<String, Object> errorBody = Map.of(
                    "error", "Internal server error",
                    "resultCd", "9999"
            );
            String json = gson.toJson(errorBody);
            ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    content
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("Failed to send error response", e);
        }
    }

    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }
}