package com.wustor.helper.util;

import com.google.gson.Gson;

import java.lang.reflect.Type;


public class JsonUtil {

	public static String toJson(Object object) {
		Gson gson = new Gson();
		return gson.toJson(object);
	}
	
	public static Object fromJson(String content,Type type){
		Gson gson = new Gson();
		return gson.fromJson(content, type);
	}

}
