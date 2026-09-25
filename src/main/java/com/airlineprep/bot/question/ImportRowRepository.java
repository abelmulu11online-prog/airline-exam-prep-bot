package com.airlineprep.bot.question;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface ImportRowRepository extends JpaRepository<ImportRow,Long> {
 Page<ImportRow> findByBatchIdOrderByRowNumber(Long id, Pageable page);
 List<ImportRow> findByBatchIdOrderByRowNumber(Long id);
}
