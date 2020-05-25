package com.qux.util;

public class DB {
	
	private static String prefix;
	
	public static String getTable(Class<?> cls){
		if(prefix==null){
			return cls.getSimpleName().toLowerCase();
		} else {
			return DB.prefix + "_"+ cls.getSimpleName().toLowerCase();
		}
	}
	
	public static void setPrefix(String prefix){
		System.out.println("DB.setPrefix(" + prefix + ") > enter");
		DB.prefix = prefix;
	}
	
}
