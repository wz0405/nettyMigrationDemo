package com.example.demo.handler;

import com.example.demo.ContextData;
import com.example.demo.controller.ApiController;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@Slf4j
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Gson gson = new Gson();
    private static final String EXPECTED_URI = "/api";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final Set<String> OPTIONAL_HEADERS = Set.of("ifId");

    private final ApiController apiController;

    public HttpRequestHandler(ApiController apiController) {
        this.apiController = apiController;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!EXPECTED_URI.equals(request.uri())) {
            sendJson(ctx, HttpResponseStatus.NOT_FOUND, Map.of("error", "Invalid URI"), null);
            return;
        }

        if (!HttpMethod.POST.equals(request.method())) {
            sendJson(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, Map.of("error", "Only POST allowed"), null);
            return;
        }

        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.contains(JSON_CONTENT_TYPE)) {
            sendJson(ctx, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, Map.of("error", "Content-Type must be application/json"), null);
            return;
        }

        String json = request.content().toString(StandardCharsets.UTF_8);
         Map<String, Object> rawMap = gson.fromJson(json, Map.class);

        ContextData requestBody = new ContextData();
        if (rawMap != null) {
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                requestBody.setParam(entry.getKey(), entry.getValue());
            }
        }
        // Spring MVC Controller와 연계
        MockHttpServletRequest servletRequest = buildServletRequest(ctx, request);
        if (servletRequest == null) {
            return; // 이미 오류 응답 처리됨
        }
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<Map<String, Object>> responseEntity = apiController.api(requestBody, servletRequest);

        sendJson(
                ctx,
                HttpResponseStatus.valueOf(responseEntity.getStatusCodeValue()),
                responseEntity.getBody(),
                responseEntity.getHeaders()
        );
    }

    private MockHttpServletRequest buildServletRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        String ifId = request.headers().get("ifId");
        if (ifId == null || ifId.isBlank()) {
            sendJson(ctx, HttpResponseStatus.FORBIDDEN, Map.of("error", "ifId missing"), null);
            return null;
        }

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setContentType(JSON_CONTENT_TYPE);
        servletRequest.setRemoteAddr(getClientIp(ctx));
        servletRequest.addHeader("ifId", ifId);

        for (String header : OPTIONAL_HEADERS) {
            String value = request.headers().get(header);
            if (value != null && !value.isBlank()) {
                servletRequest.addHeader(header, value);
            }
        }

        String cookieHeader = request.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader != null) {
            Set<io.netty.handler.codec.http.cookie.Cookie> cookies =
                    ServerCookieDecoder.STRICT.decode(cookieHeader);
            Cookie[] servletCookies = cookies.stream()
                    .map(c -> new Cookie(c.name(), c.value()))
                    .toArray(Cookie[]::new);
            servletRequest.setCookies(servletCookies);
        }

        return servletRequest;
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, Map<String, Object> body, org.springframework.http.HttpHeaders headers) {
        String json = gson.toJson(body);
        ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        if (headers != null && headers.containsKey(org.springframework.http.HttpHeaders.SET_COOKIE)) {
            for (String cookie : headers.get(org.springframework.http.HttpHeaders.SET_COOKIE)) {
                response.headers().add(HttpHeaderNames.SET_COOKIE, cookie);
            }
        }

        ctx.writeAndFlush(response);
    }

    private String getClientIp(ChannelHandlerContext ctx) {
        InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        return socketAddress.getAddress().getHostAddress();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in HTTP handler", cause);
        ctx.close();
    }
}
