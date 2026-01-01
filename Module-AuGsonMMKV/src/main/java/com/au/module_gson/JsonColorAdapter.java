package com.au.module_gson;

import android.graphics.Color;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class JsonColorAdapter implements JsonSerializer<Color>, JsonDeserializer<Color> {
    
    @Override
    public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
        // 序列化：Color -> Int
        var colorInt = src.toArgb();
        return new JsonPrimitive(colorInt);
    }

    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        // 反序列化：Int -> Color
        try {
            return Color.valueOf(json.getAsInt());
        } catch (Exception e) {
            throw new JsonParseException("Invalid URI format: " + json, e);
        }
    }
}