package com.example.demo.aop;

import com.example.demo.ContextData;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Component
@Aspect
public class ApiAop {

	private static final Gson gson = new Gson();
	private static final String LOG_ID = "logId";

	@Pointcut("execution(* com.example.demo.controller..*(..))")
	public void controllerMethods() {
	}

	@Around("controllerMethods()")
	public Object logExecutionTimeAndHeaderCheck(ProceedingJoinPoint joinPoint) throws Throwable {
		MDC.clear();
		MDC.put(LOG_ID, UUID.randomUUID().toString());

		HttpServletRequest request = null;

		// 요청 파라미터 로깅
		for (Object arg : joinPoint.getArgs()) {
			if (arg instanceof ContextData contextData) {

				Map<String, Object> paramMap = contextData.getParams();
				if (paramMap != null) {
					log.info("parameter value = {}", gson.toJson(maskSensitiveInfo(paramMap)));
				} else {
					log.info("parameter value = {} (no params)", "{}");
				}
			}
		}

		if (request != null && !validateHeaders(request)) {
			return Map.of("resultCd", "9999", "resultMsg", "Header validation failed");
		}

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		Object result = joinPoint.proceed();
		stopWatch.stop();

		String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
		log.info("Executed method: {}, Time = {} ms", methodName, stopWatch.getTotalTimeMillis());

		if (stopWatch.getTotalTimeMillis() > 5000) {
			log.warn("Slow method detected: {} ms", stopWatch.getTotalTimeMillis());
		}

		return result;
	}

	@AfterReturning(value = "controllerMethods()", returning = "returnObj")
	public void afterReturnLog(JoinPoint joinPoint, Object returnObj) {
		log.info("Return value: {}", returnObj);
	}

	private boolean validateHeaders(HttpServletRequest request) {
		String ifId = request.getHeader("ifId");
		if (!StringUtils.hasText(ifId)) {
			log.warn("Missing required header: ifId");
			return false;
		}
		MDC.put("ifId", ifId);
		return true;
	}

	private Map<String, Object> maskSensitiveInfo(Map<String, Object> params) {
		String[] sensitiveKeys = {"pwd", "pw", "cardNo"};
		for (String key : sensitiveKeys) {
			if (params.containsKey(key)) {
				params.put(key, "****");
			}
		}
		return params;
	}

	/**
	 * 트랜잭션 처리 예시
	 */
	@Around("execution(* com.example.demo.service..*(..))")
	@Transactional
	public Object transactionalService(ProceedingJoinPoint joinPoint) throws Throwable {
		return joinPoint.proceed();
	}
}
