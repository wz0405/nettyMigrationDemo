package com.example.demo.factory;


import com.example.demo.ContextData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;



@Service
@Slf4j
public class ApiJobFactory {

	public ProcInterface getBean(ApplicationContext appCtx, ContextData contextData) throws Exception {

		String ifId = (String)contextData.getParams().get("ifId");
		try {
			return (ProcInterface) appCtx.getBean(ifId.substring(0,6));

		}catch (NoSuchBeanDefinitionException e) {
			log.warn("IFID 체크 실패 {}", ifId);
			throw e;
		}

		
	}


}
