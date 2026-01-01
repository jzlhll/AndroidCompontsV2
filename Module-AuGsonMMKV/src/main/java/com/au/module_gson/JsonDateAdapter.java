package com.au.module_gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Date;

public class JsonDateAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {
    
    @Override
    public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
        // 序列化：Date -> Long
        return new JsonPrimitive(src.getTime());
    }

    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        // 反序列化：Long -> Date
        try {
            return new Date(json.getAsLong());
        } catch (Exception e) {
            throw new JsonParseException("Invalid URI format: " + json, e);
        }
    }
}