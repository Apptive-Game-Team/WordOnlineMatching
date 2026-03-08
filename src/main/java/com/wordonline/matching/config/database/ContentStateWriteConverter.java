package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.adventure.domain.ContentState;

@WritingConverter
public class ContentStateWriteConverter implements Converter<ContentState, String> {

    @Override
    public String convert(ContentState source) {
        return source.name();
    }
}
