package com.wordonline.matching.global.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.decoration.entity.DecoType;

@ReadingConverter
public class DecoTypeReadConverter implements Converter<String, DecoType> {
    @Override
    public DecoType convert(String source) {
        return DecoType.valueOf(source);
    }
}
