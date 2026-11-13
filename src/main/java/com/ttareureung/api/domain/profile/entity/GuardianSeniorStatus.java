package com.ttareureung.api.domain.profile.entity;

// 같은 계정 안의 연결은 바로 ACTIVE. PENDING은 계정 간 초대 기능이 생길 때 사용
public enum GuardianSeniorStatus {
    PENDING,
    ACTIVE
}
