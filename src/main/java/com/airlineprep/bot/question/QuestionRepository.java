package com.airlineprep.bot.question;
import org.springframework.data.jpa.repository.*;
public interface QuestionRepository extends JpaRepository<Question,Long>, JpaSpecificationExecutor<Question> {
 @Override @EntityGraph(attributePaths="currentVersion")
 org.springframework.data.domain.Page<Question> findAll(org.springframework.data.jpa.domain.Specification<Question> spec, org.springframework.data.domain.Pageable page);
 long countByStatus(QuestionStatus status);
}
