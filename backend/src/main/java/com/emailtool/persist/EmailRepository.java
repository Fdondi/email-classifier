package com.emailtool.persist;

import com.emailtool.domain.Category;
import com.emailtool.domain.ClassificationSource;
import com.emailtool.domain.Email;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<Email, Long> {

    Optional<Email> findByMessageId(String messageId);

    Optional<Email> findBySourcePath(String sourcePath);

    List<Email> findBySourcePathStartingWith(String sourcePathPrefix);

    List<Email> findByCategoryIsNull();

    long countByCategoryIsNull();

    List<Email> findByInjectionSuspectedTrueAndCategoryNot(Category spam);

    List<Email> findByCategoryIn(Collection<Category> categories);

    List<Email> findByActionNeededTrue();

    List<Email> findByCategory(Category category);

    Page<Email> findByCategoryOrderByBatchRankAscIdAsc(Category category, Pageable pageable);

    long countByCategory(Category category);

    List<Email> findByCategoryAndInjectionSuspectedFalse(Category category);

    List<Email> findByClassificationSource(ClassificationSource source);

    List<Email> findByClassificationSourceAndSummary(ClassificationSource source, String summary);
}
