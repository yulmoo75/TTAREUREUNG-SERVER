package com.ttareureung.api.domain.medication.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MealTimeSlot {

    // 이후 추가 예정
    MORNING("아침 식후"),
    LUNCH("점심 식후"),
    DINNER("저녁 식후"),
    BEFORE_SLEEP("자기 전");

    private final String label;
}
