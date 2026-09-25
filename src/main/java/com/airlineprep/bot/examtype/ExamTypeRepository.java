package com.airlineprep.bot.examtype;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamTypeRepository extends JpaRepository<ExamType, Long> {
    java.util.Optional<ExamType> findByCode(String code);
    java.util.List<ExamType> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
    java.util.List<ExamType> findAllByOrderByDisplayOrderAscIdAsc();
    boolean existsByCodeAndIdNot(String code, Long id);
    long countByActiveTrue();
}
