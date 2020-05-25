package com.qux.util;

import io.vertx.core.json.JsonObject;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONMapper {

    final ObjectMapper mapper;

    @SuppressWarnings("deprecation")
	public JSONMapper(){
    	mapper = new ObjectMapper();
  
    	/**
    	 * fixme!
    	 */
    	mapper.getJsonFactory().setCharacterEscapes(new HTMLCharacterEscapes());
    	    
    }

    public <T> T fromVertx(JsonObject json, Class<T> tClass){
        try {
            return mapper.readValue(json.encode(), tClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    };


    public JsonObject toVertx(Object obj){
        try {
            String json = mapper.writeValueAsString(obj);
            JsonObject result = new JsonObject(json);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };

   
    public String toJson(Object obj){
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T fromJson(String json, Class<T> tClass){
        try {
            return mapper.readValue(json, tClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

//First, definition of what to escape
class HTMLCharacterEscapes extends CharacterEscapes
{
	 /**
	 * 
	 */
	private static final long serialVersionUID = 3548595529745603238L;
	private final int[] asciiEscapes;
	 
	 public HTMLCharacterEscapes()
	 {
	     // start with set of characters known to require escaping (double-quote, backslash etc)
	     int[] esc = CharacterEscapes.standardAsciiEscapesForJSON();
	     // and force escaping of a few others:
	     esc['<'] = CharacterEscapes.ESCAPE_STANDARD;
	     esc['>'] = CharacterEscapes.ESCAPE_STANDARD;
	     esc['&'] = CharacterEscapes.ESCAPE_STANDARD;
	     esc['\''] = CharacterEscapes.ESCAPE_STANDARD;
	     asciiEscapes = esc;
	 }
	 // this method gets called for character codes 0 - 127
	 @Override public int[] getEscapeCodesForAscii() {
	     return asciiEscapes;
	 }
	 // and this for others; we don't need anything special here
	 @Override public SerializableString getEscapeSequence(int ch) {
	     // no further escaping (beyond ASCII chars) needed:
	     return null;
	 }
}
