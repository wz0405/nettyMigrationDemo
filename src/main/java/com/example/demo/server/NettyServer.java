package com.example.demo.server;

import com.example.demo.controller.ApiController;
import com.example.demo.handler.ExceptionHandler;
import com.example.demo.handler.HttpRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NettyServer implements ApplicationListener<ContextClosedEvent>{

    @Value("${netty.server.port}")
    private int nettyPort;
	private EventLoopGroup boss;
	private EventLoopGroup worker;

	@Autowired
	ApiController apiController;

	@PostConstruct
	public void init() {

		boss = new NioEventLoopGroup(2); //새 연결 수락 쓰레드
		worker = new NioEventLoopGroup(20);// 데이타 송수신 쓰레드
	}

	public void run() throws Exception {


		try {

			if(!worker.isShuttingDown()) {
				ServerBootstrap b = new ServerBootstrap();
				// 로그가 필요한 경우 pipeline().addLast() 에 new LoggingHandler(LogLevel.INFO) 추가할 것
				b.group(boss, worker).channel(NioServerSocketChannel.class)
//						.handler(new LoggingHandler(LogLevel.INFO))
						.option(ChannelOption.SO_BACKLOG, 1024)
						.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000).childHandler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch) throws Exception {
//								MDC.put("uniqKey", UUID.randomUUID().toString());
								MDC.put("logId", UUID.randomUUID().toString());
//								ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
								ch.config().setRecvByteBufAllocator(new AdaptiveRecvByteBufAllocator());

								if(boss.isShuttingDown()||boss.isShutdown()||boss.isTerminated()) {
									return;
								}
								if(worker.isShuttingDown()||worker.isShutdown()||worker.isTerminated()) {
									return;
								}

								ch.pipeline().addLast(new HttpServerCodec());
								ch.pipeline().addLast(new HttpObjectAggregator(1048576)); // 1MB까지 지원
								ch.pipeline().addLast(new HttpRequestHandler(apiController));
								ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(30));
								ch.pipeline().addLast("writeTimeoutHandler", new WriteTimeoutHandler(30));
								ch.pipeline().addLast("exceptionHandler", new ExceptionHandler());


							}
						}).childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
                log.info("nettyServer :{} Start",nettyPort);
				ChannelFuture f = b.bind(nettyPort).sync();
				f.channel().closeFuture().sync();


			}

		} catch (Exception e) {
			log.error("",e);
			throw e;
		} finally {
			shutdownNetty();
		}
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {//킬15 받는 곳
		shutdownNetty();
	}

	public void shutdownNetty() {
		log.info("shutting down worker ... ");
		if(worker!=null) {
			worker.shutdownGracefully(5,30,TimeUnit.SECONDS);
		}
		log.info("shut down finished worker");

		log.info("shutting down boss ... ");
		if(boss!=null) {
			boss.shutdownGracefully(5,30,TimeUnit.SECONDS);
		}
		log.info("shut down finished boss");

	}
}