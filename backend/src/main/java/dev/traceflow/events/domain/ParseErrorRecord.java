package dev.traceflow.events.domain;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A line (or block) that could not be fully interpreted. Parsing continues after recording it. */
@Entity
@Table(name = "parse_errors")
@Getter
@Setter
@NoArgsConstructor
public class ParseErrorRecord {

    @Id
    private UUID id;
    private UUID analysisId;
    private UUID fileId;
    private int lineNumber;
    private String severity;
    private String reason;
    private String raw;
}
