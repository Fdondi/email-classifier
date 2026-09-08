package com.emailtool.persist;

import com.emailtool.domain.IngestedFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestedFileRepository extends JpaRepository<IngestedFile, Long> {

    Optional<IngestedFile> findByPath(String path);
}
