package dev.traceflow.events.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID>, JpaSpecificationExecutor<LogEvent> {

    @Query("select e from LogEvent e where e.traceRef = :traceRef order by e.timestamp asc nulls last, e.seq asc")
    List<LogEvent> findTraceEvents(UUID traceRef, Pageable pageable);

    long countByTraceRef(UUID traceRef);

    List<LogEvent> findByFileIdAndLineStartBetweenOrderByLineStartAsc(UUID fileId, int from, int to);
}
