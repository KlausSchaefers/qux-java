package com.qux;

import com.qux.util.MongoQuery;
import org.junit.Test;

public class MongyQueryTest {
	
	@Test
	public void test(){
		
		MongoQuery query = MongoQuery.create("{ \"_id\" : \"?id\" }");
		
		System.out.println(query.set("id", "Papa").build());
		System.out.println(query.set("id", "Papa2").build());
		System.out.println(query.set("id", 21).build());
		
		MongoQuery query2 = MongoQuery.create("{ \"_id\" : \"?id\" , \"param1\" : \"?p\"}");
		System.out.println(query2.set("id", "1").set("p", true).build());
		
		MongoQuery query3 = MongoQuery.create("{ \"_id\" : \"?id\" , \"child\" : { \"param1:\" : \"?p\"}}");
		System.out.println(query3.set("id", "1").set("p", true).build());
	}

}
