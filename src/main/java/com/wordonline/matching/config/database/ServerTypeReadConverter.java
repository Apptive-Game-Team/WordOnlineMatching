package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.server.domain.ServerType;

@ReadingConverter
public class ServerTypeReadConverter implements Converter<String, ServerType> {

    @Override
    public ServerType convert(String source) {
        return ServerType.valueOf(source.toUpperCase());
    }
}
