package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.server.domain.ServerState;

@WritingConverter
public class ServerStateWriteConverter implements Converter<ServerState, String> {

    @Override
    public String convert(ServerState source) {
        return source.name();
    }
}
