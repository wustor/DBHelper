package com.wustor.helper.util;

import java.util.ArrayList;

public class TextUtil {
	public static boolean isValidate(String content){
		if(content != null && !"".equals(content.trim())){
			return true;
		}
		return false;
	}
	
	public static boolean isValidate(ArrayList list){
		if (list != null && list.size() >0) {
			return true;
		}
		return false;
	}
}
