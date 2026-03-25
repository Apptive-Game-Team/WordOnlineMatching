package com.wordonline.matching.global.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.decoration.entity.DecoType;

@WritingConverter
public class DecoTypeWriteConverter implements Converter<DecoType, String> {
    @Override
    public String convert(DecoType source) {
        return source.name();
    }
}
