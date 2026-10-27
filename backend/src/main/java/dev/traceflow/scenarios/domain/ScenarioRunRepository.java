package dev.traceflow.scenarios.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRunRepository extends JpaRepository<ScenarioRun, UUID> {

    Page<ScenarioRun> findByScenarioKey(String scenarioKey, Pageable pageable);

    Optional<ScenarioRun> findFirstByScenarioKeyOrderByStartedAtDesc(String scenarioKey);

    long countByScenarioKey(String scenarioKey);
}
