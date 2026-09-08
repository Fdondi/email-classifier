package com.emailtool.persist;

import com.emailtool.domain.Correction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    @Query("select c from Correction c join fetch c.email order by c.createdAt desc")
    List<Correction> findAllByOrderByCreatedAtDesc();
}
