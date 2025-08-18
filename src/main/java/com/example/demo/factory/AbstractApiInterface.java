package com.example.demo.factory;

import com.example.demo.ContextData;

public abstract class AbstractApiInterface implements ApiInterface {

    @Override
    public boolean validateParam(ContextData data) {
        return true; // 기본 검증 (확장 가능)
    }


}
