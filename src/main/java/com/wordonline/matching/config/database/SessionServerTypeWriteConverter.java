package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.server.entity.ServerType;

@WritingConverter
public class SessionServerTypeWriteConverter implements Converter<ServerType, String> {

    @Override
    public String convert(ServerType source) {
        if (source == null) {
            return null;
        }
        return source.name();
    }
}
