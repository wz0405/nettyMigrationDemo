package com.example.demo.factory;


import com.example.demo.ContextData;

import java.util.Map;

public interface ProcInterface {
	boolean beforProcess(ContextData data) throws Exception;
	void doProcess(ContextData data) throws Exception;
	Map<? extends String, ?> afterProcess(ContextData data) throws Exception;
}
