package com.example.demo.dependency;

import com.example.demo.factory.ApiJobFactory;
import com.example.demo.factory.IfIdFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;


@Service
public class Dependency {



	@Autowired
	protected ApiJobFactory apiJobFactory;
	@Autowired
	protected IfIdFactory ifIdFactory;

	@Autowired
	protected ApplicationContext ctx;





}
