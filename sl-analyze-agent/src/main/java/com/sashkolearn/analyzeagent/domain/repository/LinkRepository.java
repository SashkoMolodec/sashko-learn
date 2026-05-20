package com.sashkolearn.analyzeagent.domain.repository;

import com.sashkolearn.analyzeagent.domain.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LinkRepository extends JpaRepository<Link, Link.LinkId> {

    List<Link> findByFromId(UUID fromId);

    List<Link> findByToId(UUID toId);

    @Modifying
    @Query("DELETE FROM Link l WHERE l.fromId = :noteId OR l.toId = :noteId")
    void deleteAllLinksForNote(@Param("noteId") UUID noteId);
}
