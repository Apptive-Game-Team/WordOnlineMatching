package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.server.domain.ServerState;

@ReadingConverter
public class ServerStateReadConverter implements Converter<String, ServerState> {

    @Override
    public ServerState convert(String source) {
        if (source == null) {
            return null;
        }
        return ServerState.valueOf(source.toUpperCase());
    }
}
