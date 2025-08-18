package com.example.demo.biz.bean;

import com.example.demo.ContextData;
import com.example.demo.dependency.Dependency;
import com.example.demo.factory.ApiInterface;
import com.example.demo.factory.ProcInterface;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("SIMPLE")
public class SimpleBean extends Dependency implements ProcInterface {

    private ApiInterface apiInterface;

    @Override
    public boolean beforProcess(ContextData data) throws Exception {
        apiInterface = ifIdFactory.getBean(ctx, data);
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

        if (result instanceof Map) {
            return (Map<String, Object>) result;
        } else {
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("data", result);
            return wrapper;
        }
    }
}
