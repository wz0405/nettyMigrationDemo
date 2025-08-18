package com.example.demo;

import java.util.HashMap;
import java.util.Map;

public class ContextData {
    private final Map<String, Object> params = new HashMap<>();
    private Object result;

    public Map<String, Object> getParams() { return params; }
    public void setParam(String key, Object value) { params.put(key, value); }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
}