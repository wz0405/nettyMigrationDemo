# Spring MVC → Netty Migration Demo

이 프로젝트는 기존 **Spring MVC 기반 동기식 REST API 서버**를  
**Netty 기반 비동기/논블로킹 서버**로 리팩토링한 데모입니다.  

---

## ✨ 프로젝트 개요
- 기존 MVC 구조에서 **Servlet Thread Pool**에 의존하던 한계를 극복
- **Netty 이벤트 루프 기반**으로 전환하여 고성능/대규모 동시 연결 처리 가능

---

## ⚖️ Before vs After

### Before: Spring MVC (동기식 구조)
- 요청 1건당 스레드 1개 점유
- 트래픽이 늘어날수록 Thread Pool 확장 필요 → 리소스 소모 증가
- 블로킹 I/O (`HttpServletRequest` / `HttpServletResponse`)

```java
// 기존 Spring MVC Controller 예시
@RestController
@RequestMapping("/api")
public class PaymentController {

    @PostMapping("/pay")
    public ResponseEntity<String> pay(@RequestBody PaymentRequest request) {
        // 결제 로직 동기 실행
        String result = paymentService.process(request);
        return ResponseEntity.ok(result);
    }
}
