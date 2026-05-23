package com.codezeng.lms.repository;

import com.codezeng.lms.domain.LocalizedText;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalizedTextRepository extends JpaRepository<LocalizedText, Long> {

    Optional<LocalizedText> findFirstByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(
            String entityType, Long entityId, String fieldKey, String localeTag);

    boolean existsByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(
            String entityType, Long entityId, String fieldKey, String localeTag);
}
