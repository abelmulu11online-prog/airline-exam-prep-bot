package com.airlineprep.bot.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    java.util.List<Category> findAllByOrderByDisplayOrderAscIdAsc();
    boolean existsByExamTypeIdAndCodeAndIdNot(Long examTypeId, String code, Long id);
}
