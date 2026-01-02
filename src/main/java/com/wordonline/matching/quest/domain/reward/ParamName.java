package com.wordonline.matching.quest.domain.reward;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// 변수에 받을 request param 이름을 지정
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamName {

    String value();
}
