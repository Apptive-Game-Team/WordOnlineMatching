package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.server.entity.ServerState;

@ReadingConverter
public class SessionServerStateReadConverter implements Converter<String, ServerState> {

    @Override
    public ServerState convert(String source) {
        if (source == null) {
            return null;
        }
        try {
            return ServerState.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
