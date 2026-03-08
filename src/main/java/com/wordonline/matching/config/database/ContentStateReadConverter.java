package com.wordonline.matching.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.wordonline.matching.adventure.domain.ContentState;

@ReadingConverter
public class ContentStateReadConverter implements Converter<String, ContentState> {

    @Override
    public ContentState convert(String source) {
        return ContentState.valueOf(source);
    }
}
