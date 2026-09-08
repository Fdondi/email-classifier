package com.emailtool.persist;

import com.emailtool.domain.TriageRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageRunRepository extends JpaRepository<TriageRun, Long> {

    List<TriageRun> findAllByOrderByStartedAtDesc();
}
