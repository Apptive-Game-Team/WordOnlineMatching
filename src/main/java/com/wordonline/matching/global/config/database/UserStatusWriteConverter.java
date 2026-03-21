package com.wordonline.matching.global.config.database;

import java.awt.Color;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.EnumWriteSupport;

import com.wordonline.matching.auth.domain.UserStatus;

@WritingConverter
public class UserStatusWriteConverter implements Converter<UserStatus, String> {
    @Override
    public String convert(UserStatus source) {
        return source.name();
    }
}

