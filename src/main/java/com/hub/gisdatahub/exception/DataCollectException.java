package com.hub.gisdatahub.exception;

import org.springframework.stereotype.Component;

@Component
public class DataCollectException {
    public IllegalArgumentException startDateException(){
        return new IllegalArgumentException("start는 1 이상이어야 합니다.");
    }
    public IllegalArgumentException endNotLowerThanStartException(){
        return new IllegalArgumentException("end는 start보다 커야합니다.");
    }
    public IllegalArgumentException endLimitException(){
        return new IllegalArgumentException("end는 1000이하로 제한됩니다.");
    }

    public IllegalArgumentException basicDateTypeException(){
        return new IllegalArgumentException("date는 yyyyMMdd 형식이어야 합니다.");
    }
    public IllegalArgumentException IsoDateTypeException(){
        return new IllegalArgumentException("date는 yyyy-MM-dd 형식이어야 합니다.");
    }
    public IllegalArgumentException hourFormatException(){
        return new IllegalArgumentException("hour는 00~23 형식이어야 합니다.");
    }
    public IllegalArgumentException hourRangeException(){
        return new IllegalArgumentException("hour는 00~23 사이어야 합니다.");
    }
}
