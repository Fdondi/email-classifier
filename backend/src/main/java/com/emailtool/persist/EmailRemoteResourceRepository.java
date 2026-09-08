package com.emailtool.persist;

import com.emailtool.domain.EmailRemoteResource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRemoteResourceRepository extends JpaRepository<EmailRemoteResource, Long> {

    List<EmailRemoteResource> findByEmailIdOrderByResourceIndexAsc(Long emailId);

    Optional<EmailRemoteResource> findByEmailIdAndResourceIndex(Long emailId, int resourceIndex);

    Optional<EmailRemoteResource> findByEmailIdAndOriginalUrl(Long emailId, String originalUrl);

    void deleteByEmailId(Long emailId);
}
