package com.emailtool.persist;

import com.emailtool.domain.SenderPrior;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenderPriorRepository extends JpaRepository<SenderPrior, String> {}
