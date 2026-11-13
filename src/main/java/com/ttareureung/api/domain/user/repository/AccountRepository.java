package com.ttareureung.api.domain.user.repository;

import com.ttareureung.api.domain.user.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByEmail(String email);

    Optional<Account> findByEmail(String email);

    // 같은 계정의 프로필 생성·삭제 동시 요청 직렬화용 (보호자 수 확인 후 삭제 사이의 경쟁 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
