package com.example.demo.factory;

import com.example.demo.ContextData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;


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
