package com.example.demo.factory;

import com.example.demo.ContextData;

import java.util.ArrayList;

public interface ApiInterface {
    boolean validateParam(ContextData data) throws Exception;

    boolean beforeCheck(ContextData data) throws Exception;

    void makeReturnParam(ContextData data) throws Exception;

    ArrayList<String> createCheckParams(ContextData data);

    void execute(ContextData data) throws Exception;
}
