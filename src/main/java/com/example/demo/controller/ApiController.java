package com.example.demo.controller;

import com.example.demo.ContextData;
import com.example.demo.dependency.Dependency;
import com.example.demo.factory.ProcInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController extends Dependency {

	@PostMapping
	public ResponseEntity<Map<String, Object>> api(@RequestBody ContextData requestBody, HttpServletRequest request) {

		Map<String, Object> responseMap = new LinkedHashMap<>();
		HttpHeaders headers = new HttpHeaders();

		try {
			manageHeader(request, requestBody);

			ProcInterface proc = apiJobFactory.getBean(ctx, requestBody);
			if (proc.beforProcess(requestBody)) {
				proc.doProcess(requestBody);
				Map<String, Object> afterResult = (Map<String, Object>) proc.afterProcess(requestBody);
				if (afterResult != null) {
					responseMap.putAll(afterResult);
				}
				responseMap.put("resultCd", "0000");
				responseMap.put("resultMsg", "SUCCESS");
			}else{
				responseMap.put("resultCd", "9999");
				responseMap.put("resultMsg", "beforeCheckFail");
			}



		} catch (Exception e) {
			log.error("API 처리 중 오류 발생", e);
			responseMap.put("resultCd", "9999");
			responseMap.put("resultMsg", "ERROR");
		}

		return new ResponseEntity<>(responseMap, headers, HttpStatus.OK);
	}

	private void manageHeader(HttpServletRequest request, ContextData requestBody) {
		requestBody.setParam("ifId", request.getHeader("ifId"));
	}
}
