package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.server.entity.ServerType;

@ReadingConverter
public class SessionServerTypeReadConverter implements Converter<String, ServerType> {

    @Override
    public ServerType convert(String source) {
        if (source == null) {
            return null;
        }
        try {
            return ServerType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
