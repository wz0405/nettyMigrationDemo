# Netty 기반 비동기 API 서버

## 개요

Spring MVC의 Servlet Thread Pool 기반 구조에서 Netty 이벤트 루프 기반으로 전환한 API 서버 샘플입니다.

전환 목적은 Thread-per-request 모델의 구조적 한계를 제거하고, 대규모 동시 연결 환경에서의 처리 효율을 높이는 것입니다.

벤치마크 테스트 환경에서는 MVC 대비 TPS 개선을 확인했습니다.  
다만 실 운영 트래픽 규모에서는 유의미한 차이를 체감하지 못했으며, Netty 전환이 항상 유리한 선택은 아님을 확인했습니다.

---

## Before / After

| 항목 | Spring MVC | Netty |
|------|-----------|-------|
| I/O 모델 | Blocking (Thread-per-request) | Non-blocking (이벤트 루프) |
| 동시 연결 | Servlet Thread Pool 크기에 종속 | 소수 스레드로 다수 연결 처리 |
| 요청 파싱 | HttpServletRequest | Netty Pipeline + FullHttpRequest 직접 파싱 |
| 확장 방식 | Thread Pool 크기 증가 | Worker 스레드 수 조정 |
| 운영 복잡도 | 낮음 | 상대적으로 높음 |

---

## 기술 스택

- Java 17 / Spring Boot 3.2.3
- Netty 4.1.111.Final (`netty-codec-http`, `netty-handler`, `netty-transport`)
- MyBatis 3.0.3 / MariaDB
- Gson 2.9.1
- Lombok / Spring AOP

> `spring.main.web-application-type=none` 설정으로 Tomcat/Undertow 등 내장 Servlet 컨테이너를 비활성화하고  
> Netty를 직접 기동합니다. Spring Context는 유지하되 Servlet 스택은 완전히 제거된 구조입니다.

---

## 프로젝트 구조

```plaintext
com.example.demo
├── aop
│   └── ApiAop                  # 실행시간 측정, 민감정보 마스킹, MDC 로그 추적
├── biz.bean
│   ├── SimpleBean               # ProcInterface 구현체 (ifId 앞 6자리 매핑)
│   └── sub
│       └── SimpleSubBean        # ApiInterface 구현체 (전체 ifId 매핑)
├── controller
│   └── ApiController            # Spring @RestController — Netty/MVC 공용
├── dependency
│   └── Dependency               # 공통 의존성 베이스 클래스
├── factory
│   ├── ApiInterface             # API 구현체 인터페이스
│   ├── AbstractApiInterface     # validateParam 기본 구현 제공
│   ├── ApiJobFactory            # ifId 앞 6자리로 ProcInterface Bean 조회
│   ├── IfIdFactory              # 전체 ifId로 ApiInterface Bean 조회
│   └── ProcInterface            # beforProcess / doProcess / afterProcess
├── handler
│   ├── HttpRequestHandler       # Netty 인바운드 핸들러 (검증, 파싱, 응답)
│   └── ExceptionHandler         # Netty Pipeline 예외 처리
└── server
    ├── NettyServer              # Boss/Worker 이벤트 루프, graceful shutdown
    ├── ContextData              # 요청/응답 컨텍스트 (params + result)
    ├── DBConfig                 # DataSource / MyBatis SqlSessionFactory
    ├── ServiceManager           # ApplicationContext 정적 접근
    └── DemoApplication          # Spring Boot 진입점 + NettyServer.run() 호출
```

---

## 주요 코드 샘플

### NettyServer — 이벤트 루프 구성 및 graceful shutdown

`SO_BACKLOG`, `TCP_NODELAY`, `SO_KEEPALIVE` 옵션을 명시하고,  
`AdaptiveRecvByteBufAllocator`로 수신 버퍼를 트래픽에 따라 동적 조정합니다.  
`ApplicationListener<ContextClosedEvent>` 구현으로 SIGTERM(kill -15) 수신 시 이벤트 루프를 정상 종료합니다.

```java
@Slf4j
@Service
public class NettyServer implements ApplicationListener<ContextClosedEvent> {

    @Value("${netty.server.port}")
    private int nettyPort;
    private EventLoopGroup boss;
    private EventLoopGroup worker;

    @PostConstruct
    public void init() {
        boss   = new NioEventLoopGroup(2);   // 새 연결 수락 전용
        worker = new NioEventLoopGroup(20);  // 데이터 송수신 처리
    }

    public void run() throws Exception {
        try {
            if (!worker.isShuttingDown()) {
                ServerBootstrap b = new ServerBootstrap();
                b.group(boss, worker)
                 .channel(NioServerSocketChannel.class)
                 .option(ChannelOption.SO_BACKLOG, 1024)
                 .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                 .childOption(ChannelOption.TCP_NODELAY, true)
                 .childOption(ChannelOption.SO_KEEPALIVE, true)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     @Override
                     protected void initChannel(SocketChannel ch) {
                         // shutdown 진행 중이면 신규 채널 초기화 중단
                         if (boss.isShuttingDown() || worker.isShuttingDown()) return;

                         MDC.put("logId", UUID.randomUUID().toString());
                         ch.config().setRecvByteBufAllocator(new AdaptiveRecvByteBufAllocator());

                         ch.pipeline().addLast(new HttpServerCodec());
                         ch.pipeline().addLast(new HttpObjectAggregator(1048576)); // 최대 1MB
                         ch.pipeline().addLast(new HttpRequestHandler(apiController));
                         ch.pipeline().addLast("readTimeoutHandler",  new ReadTimeoutHandler(30));
                         ch.pipeline().addLast("writeTimeoutHandler", new WriteTimeoutHandler(30));
                         ch.pipeline().addLast("exceptionHandler",    new ExceptionHandler());
                     }
                 });

                log.info("NettyServer :{} Start", nettyPort);
                b.bind(nettyPort).sync().channel().closeFuture().sync();
            }
        } finally {
            shutdownNetty();
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdownNetty(); // SIGTERM 수신 시 호출
    }

    public void shutdownNetty() {
        if (worker != null) worker.shutdownGracefully(5, 30, TimeUnit.SECONDS);
        if (boss   != null) boss.shutdownGracefully(5, 30, TimeUnit.SECONDS);
    }
}
```

---

### HttpRequestHandler — 요청 검증, 파싱, MockHttpServletRequest 브리지

Netty Pipeline에서 HTTP 요청을 수신하여 URI / Method / Content-Type / 필수 헤더를 검증합니다.  
`FullHttpRequest`를 파싱해 `ContextData`를 구성하고, `MockHttpServletRequest`로 래핑하여  
Spring `@RestController`(`ApiController`)를 그대로 재사용합니다.  
쿠키 파싱과 클라이언트 IP 추출도 Pipeline 내에서 처리합니다.

```java
@Slf4j
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String EXPECTED_URI      = "/api";
    private static final String JSON_CONTENT_TYPE = "application/json";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

        // URI / Method / Content-Type 검증
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
            sendJson(ctx, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE,
                     Map.of("error", "Content-Type must be application/json"), null);
            return;
        }

        // Body 파싱 → ContextData
        String json = request.content().toString(StandardCharsets.UTF_8);
        Map<String, Object> rawMap = gson.fromJson(json, Map.class);
        ContextData requestBody = new ContextData();
        if (rawMap != null) rawMap.forEach(requestBody::setParam);

        // MockHttpServletRequest 생성 (Spring Controller 재사용)
        MockHttpServletRequest servletRequest = buildServletRequest(ctx, request);
        if (servletRequest == null) return; // ifId 누락 → 이미 오류 응답 처리됨

        ResponseEntity<Map<String, Object>> responseEntity =
                apiController.api(requestBody, servletRequest);

        sendJson(ctx,
                 HttpResponseStatus.valueOf(responseEntity.getStatusCodeValue()),
                 responseEntity.getBody(),
                 responseEntity.getHeaders());
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

        // 쿠키 파싱 및 전달
        String cookieHeader = request.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader != null) {
            Cookie[] cookies = ServerCookieDecoder.STRICT.decode(cookieHeader).stream()
                    .map(c -> new Cookie(c.name(), c.value()))
                    .toArray(Cookie[]::new);
            servletRequest.setCookies(cookies);
        }
        return servletRequest;
    }
}
```

---

### 2-Tier 라우팅 — ApiJobFactory + IfIdFactory

`ifId`를 두 단계로 분기하는 구조입니다.

1. **ApiJobFactory**: `ifId` 앞 6자리로 `ProcInterface` Bean을 조회합니다. 처리 흐름(`beforProcess → doProcess → afterProcess`)을 담당하는 그룹 단위 라우팅입니다.
2. **IfIdFactory**: 전체 `ifId`로 `ApiInterface` Bean을 조회합니다. 실제 비즈니스 로직을 담당하는 세부 라우팅입니다.

```java
// ApiJobFactory — ifId 앞 6자리로 처리 흐름 Bean 조회
@Service
public class ApiJobFactory {
    public ProcInterface getBean(ApplicationContext appCtx, ContextData contextData) throws Exception {
        String ifId = (String) contextData.getParams().get("ifId");
        try {
            return (ProcInterface) appCtx.getBean(ifId.substring(0, 6));
        } catch (NoSuchBeanDefinitionException e) {
            log.warn("IFID 체크 실패 {}", ifId);
            throw e;
        }
    }
}
```

```java
// SimpleBean — @Service("SIMPLE"), ProcInterface 구현체
// ifId가 "SIMPLE"로 시작하는 모든 요청의 처리 흐름을 담당
@Service("SIMPLE")
public class SimpleBean extends Dependency implements ProcInterface {

    private ApiInterface apiInterface;

    @Override
    public boolean beforProcess(ContextData data) throws Exception {
        apiInterface = ifIdFactory.getBean(ctx, data); // 전체 ifId로 세부 Bean 조회
        return apiInterface.beforeCheck(data);
    }

    @Override
    public void doProcess(ContextData data) throws Exception {
        apiInterface.execute(data);
    }

    @Override
    public Map<String, Object> afterProcess(ContextData data) throws Exception {
        apiInterface.makeReturnParam(data);
        Object result = data.getResult();
        if (result instanceof Map) return (Map<String, Object>) result;
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("data", result);
        return wrapper;
    }
}
```

```java
// SimpleSubBean — @Service("SIMPLEBEAN"), ApiInterface 구현체
// ifId = "SIMPLEBEAN" 요청의 실제 비즈니스 로직 담당
@Service("SIMPLEBEAN")
public class SimpleSubBean extends AbstractApiInterface {

    @Override
    public boolean beforeCheck(ContextData data) {
        return data.getParams().containsKey("mbUid");
    }

    @Override
    public void execute(ContextData data) throws Exception {
        data.setResult("Hello, " + data.getParams().get("mbUid"));
    }

    @Override
    public void makeReturnParam(ContextData data) { }

    @Override
    public ArrayList<String> createCheckParams(ContextData data) { return null; }
}
```

> API 추가 시 `@Service("SIMPLE계열_IFID")` 구현체만 등록하면 됩니다. 라우팅 테이블 수정은 불필요합니다.

---

### ApiAop — MDC 추적, 민감정보 마스킹, 슬로우 요청 감지

```java
@Slf4j
@Component
@Aspect
public class ApiAop {

    private static final Gson gson = new Gson();

    @Pointcut("execution(* com.example.demo.controller..*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object logExecutionTimeAndHeaderCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.clear();
        MDC.put("logId", UUID.randomUUID().toString()); // 요청별 고유 로그 ID

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof ContextData contextData) {
                // 민감정보(pwd, pw, cardNo) 마스킹 후 로깅
                log.info("parameter value = {}", gson.toJson(maskSensitiveInfo(contextData.getParams())));
            }
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object result = joinPoint.proceed();
        stopWatch.stop();

        log.info("Executed method: {}, Time = {} ms",
                 ((MethodSignature) joinPoint.getSignature()).getMethod().getName(),
                 stopWatch.getTotalTimeMillis());

        // 5초 초과 시 슬로우 요청 경고
        if (stopWatch.getTotalTimeMillis() > 5000) {
            log.warn("Slow method detected: {} ms", stopWatch.getTotalTimeMillis());
        }

        return result;
    }

    private Map<String, Object> maskSensitiveInfo(Map<String, Object> params) {
        for (String key : new String[]{"pwd", "pw", "cardNo"}) {
            if (params.containsKey(key)) params.put(key, "****");
        }
        return params;
    }
}
```

---

## 요청 처리 흐름

```
Client
  │
  ▼ HTTP POST /api
Netty Pipeline
  ├── HttpServerCodec         # HTTP 인코딩/디코딩
  ├── HttpObjectAggregator    # 청크 조합 (최대 1MB)
  ├── HttpRequestHandler      # URI/Method/헤더 검증, ContextData 구성
  │     └── MockHttpServletRequest 생성
  │
  ▼
ApiController (Spring @RestController)
  │  manageHeader() — ifId 헤더 → ContextData 주입
  │
  ▼
ApiJobFactory.getBean(ifId.substring(0,6))   # 처리 흐름 Bean 조회
  │
  ▼
ProcInterface (예: SimpleBean)
  ├── beforProcess()  → IfIdFactory.getBean(ifId)  # 세부 Bean 조회
  │                   → ApiInterface.beforeCheck()  # 파라미터 검증
  ├── doProcess()     → ApiInterface.execute()      # 비즈니스 로직
  └── afterProcess()  → ApiInterface.makeReturnParam() + 응답 포맷
  │
  ▼
ApiController → ResponseEntity
  │
  ▼
HttpRequestHandler.sendJson() → Netty Channel → Client
```

---

## 실행 방법

```bash
# application.properties 주요 설정
spring.main.web-application-type=none   # Servlet 컨테이너 비활성화
netty.server.port=38099

spring.datasource.hikari.maximum-pool-size=20

# 실행
./gradlew bootRun
```

---

## 정리

- `spring.main.web-application-type=none`으로 Servlet 컨테이너를 완전히 제거하고 Netty만으로 HTTP 처리
- `SO_BACKLOG`, `TCP_NODELAY`, `SO_KEEPALIVE`, `AdaptiveRecvByteBufAllocator` 등 소켓 옵션 직접 제어
- `MockHttpServletRequest` 브리지로 Spring `@RestController`를 Netty 환경에서 그대로 재사용
- 2-Tier 라우팅(그룹 단위 6자리 + 세부 전체 ifId)으로 API 확장 시 코드 변경 없이 Bean 등록만으로 처리
- AOP에서 MDC 기반 요청 추적, 민감정보 마스킹, 5초 슬로우 요청 감지를 공통 처리
- 벤치마크 TPS 개선 확인 / 실 운영 규모에서는 유의미한 차이 미확인
- 운영 복잡도 증가를 고려하면 트래픽 규모에 따라 MVC가 적합한 경우도 있음
