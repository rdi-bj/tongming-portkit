package com.jinw.common.domain.graph.deserializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.common.domain.graph.FunctionKey;

import java.io.IOException;

public class FunctionKeyKeyDeserializer extends KeyDeserializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt)
            throws IOException {
        return MAPPER.readValue(key, FunctionKey.class);
    }
}