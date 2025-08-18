# 🚀 Netty 기반 API 서버

## ✨ 프로젝트 개요

* 기존 MVC 구조에서 **Servlet Thread Pool**에 의존하던 한계를 극복
* **Netty 이벤트 루프 기반**으로 전환하여 고성능/대규모 동시 연결 처리 가능
* 내부 전용 유틸 제거 후 **표준 Java/Spring 구조화**
* 전략(Strategy) + 팩토리(Factory) 패턴을 적용하여 유연한 확장성 확보

---

## 📂 프로젝트 구조

```plaintext
main
 ├── java
 │   └── com.example.demo
 │       ├── aop
 │       │   └── ApiAop
 │       ├── biz.bean
 │       │   ├── sub
 │       │   │   └── SimpleSubBean
 │       │   └── SimpleBean
 │       ├── controller
 │       │   └── ApiController
 │       ├── dependency
 │       │   └── Dependency
 │       ├── factory
 │       │   ├── AbstractApiInterface
 │       │   ├── ApiInterface
 │       │   ├── ApiJobFactory
 │       │   ├── IfIdFactory
 │       │   └── ProcInterface
 │       ├── handler
 │       │   ├── ExceptionHandler
 │       │   └── HttpRequestHandler
 │       └── server
 │           ├── NettyServer
 │           ├── ContextData
 │           ├── DBConfig
 │           ├── DemoApplication
 │           └── ServiceManager
 └── resources
     └── mapper
```

---

## 🔄 Before vs After

### Before: Spring MVC (동기식)

* Servlet 기반 **Thread-per-request** 모델
* HttpServletRequest/Response 기반 파라미터 처리
* 개발 용이하지만 **대규모 트래픽 시 병목 발생**

### After: Netty (비동기 이벤트 루프 구조)

* 이벤트 루프 기반 **Non-blocking I/O**
* 수만 개의 동시 연결 처리 가능
* HttpServletRequest 없이 JSON Request/Response 직접 처리

```java
// Netty ServerInitializer (발췌)
ch.pipeline().addLast(new HttpServerCodec());
ch.pipeline().addLast(new HttpObjectAggregator(1048576));
ch.pipeline().addLast(new HttpRequestHandler(apiController));
ch.pipeline().addLast("readTimeoutHandler", new ReadTimeoutHandler(30));
ch.pipeline().addLast("writeTimeoutHandler", new WriteTimeoutHandler(30));
ch.pipeline().addLast("exceptionHandler", new ExceptionHandler());
```

---

## 🛠 주요 코드 샘플

### IfIdFactory

```java
@Service
@Slf4j
public class IfIdFactory {

    public ApiInterface getBean(ApplicationContext appCtx, ContextData data) throws Exception {

        String ifId = (String) data.getParams().get("ifId");
        ApiInterface apiInterface = null;
        try{
            apiInterface = (ApiInterface) appCtx.getBean(ifId);
        }catch (NoSuchBeanDefinitionException e) {
            log.warn("API명 체크 실패 {}", ifId);
            throw e;
        }
        return apiInterface;



    }


}

```

---

### SimpleSubBean

```java
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
    public void makeReturnParam(ContextData data) {
        // 응답 포맷 구성 가능
    }

    @Override
    public ArrayList<String> createCheckParams(ContextData data) {
        return null;
    }
}
```

---

### ApiAop

```java
@Slf4j
@Component
@Aspect
public class ApiAop {

    @Pointcut("execution(* com.example.demo.controller..*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object logExecutionTimeAndHeaderCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        // 로깅 + 헤더 검증 + 실행시간 측정
    }

    @AfterReturning(value = "controllerMethods()", returning = "returnObj")
    public void afterReturnLog(JoinPoint joinPoint, Object returnObj) {
        log.info("Return value: {}", returnObj);
    }
}
```

---



### NettyServer

```java
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
        boss = new NioEventLoopGroup(2);   // 새 연결 수락 스레드
        worker = new NioEventLoopGroup(20);// 데이터 송수신 스레드
    }

    public void run() throws Exception {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(new HttpServerCodec());
                     ch.pipeline().addLast(new HttpObjectAggregator(1048576));
                     ch.pipeline().addLast(new HttpRequestHandler(apiController));
                     ch.pipeline().addLast(new ReadTimeoutHandler(30));
                     ch.pipeline().addLast(new WriteTimeoutHandler(30));
                     ch.pipeline().addLast(new ExceptionHandler());
                 }
             });

            log.info("nettyServer :{} Start", nettyPort);
            ChannelFuture f = b.bind(nettyPort).sync();
            f.channel().closeFuture().sync();

        } finally {
            shutdownNetty();
        }
    }
}
```

---

## ✅ 요약

* **Spring AOP + Netty 서버 통합**으로 구조 리팩토링
* **Bean, Factory, Controller, AOP, Netty Handler** 모듈화
* 확장성과 성능을 고려한 **비동기 이벤트 루프 구조**
