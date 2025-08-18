package com.example.demo.biz.bean.sub;

import com.example.demo.ContextData;
import com.example.demo.factory.AbstractApiInterface;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

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
