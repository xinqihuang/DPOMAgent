## MODIFIED Requirements

### Requirement: Persistent Investigation
DPOMAgent SHALL remain the authoritative system of record for Incident, Investigation, Run, Step, Observation, Hypothesis
and Conclusion throughout and after the Phase 1B transport migration. When an Investigation reaches a terminal state that
requires evaluation handoff, DPOMAgent SHALL persist terminal state, Conclusion, Run completion and the corresponding
canonical Diagnosis Event outbox row in one database transaction. It SHALL recover from persisted facts without depending
on the original LLM conversation. Replacing the HTTP delivery adapter with Kafka MUST NOT transfer or retire this authority.

#### Scenario: Resume
- **GIVEN** an Investigation has completed several persisted steps
- **WHEN** DPOMAgent restarts
- **THEN** DPOMAgent SHALL recover the authoritative state from its database
- **AND** recovery SHALL NOT depend on the original LLM conversation

#### Scenario: Atomic terminalization
- **GIVEN** an Investigation produces a persistable terminal Conclusion
- **WHEN** DPOMAgent commits terminalization
- **THEN** Investigation state, Conclusion, Run completion and exactly one outbox event SHALL become atomically visible
- **AND** network delivery MUST NOT occur inside that database transaction

#### Scenario: Terminal transaction rolls back
- **GIVEN** writing the Conclusion, Run completion or outbox event fails
- **WHEN** the terminal transaction rolls back
- **THEN** DPOMAgent MUST NOT expose a terminal Investigation without its canonical event
- **AND** recovery SHALL be able to retry terminalization safely

#### Scenario: Phase 1B transport cutover completes
- **GIVEN** Kafka has replaced HTTP as the primary Diagnosis Event transport
- **WHEN** the HTTP compatibility adapter is retired
- **THEN** DPOMAgent SHALL retain all authoritative Investigation responsibilities and records
- **AND** DPOMBaseMCPServer MUST NOT assume those responsibilities

