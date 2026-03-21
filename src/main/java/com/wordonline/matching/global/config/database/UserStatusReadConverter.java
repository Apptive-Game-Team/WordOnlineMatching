package com.wordonline.matching.global.config.database;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import com.wordonline.matching.auth.domain.UserStatus;

@ReadingConverter
public class UserStatusReadConverter implements Converter<String, UserStatus> {
    @Override
    public UserStatus convert(String source) {
        return UserStatus.valueOf(source);
    }
}

