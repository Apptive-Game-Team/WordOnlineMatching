package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.server.entity.ServerState;

@WritingConverter
public class SessionServerStateWriteConverter implements Converter<ServerState, String> {

    @Override
    public String convert(ServerState source) {
        if (source == null) {
            return null;
        }
        return source.name();
    }
}
