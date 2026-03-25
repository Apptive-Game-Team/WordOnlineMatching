package com.wordonline.matching.global.service;

import lombok.RequiredArgsConstructor;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LocalizationService {

    private final MessageSource messageSource;

    public String getMessage(LocaleContext localeContext, String code) {
        return getMessage(localeContext, code, null);
    }

    public String getMessage(LocaleContext localeContext, String code, Object[] args) {
        Locale locale = getCurrentLocale(localeContext);
        return messageSource.getMessage(code, args, locale);
    }

    private Locale getCurrentLocale(LocaleContext localeContext) {
        return localeContext.getLocale();
    }
}
