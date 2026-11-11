# TraceWitness

**AI-Native Video Incident Investigation Agent**

> Trace the scene. Verify the evidence.

TraceWitness is an evidence-grounded AI application for video incident investigation. It combines video event processing, natural-language investigation, spatiotemporal retrieval, trajectory reconstruction, and auditable evidence reporting.

The project is designed as a real Agent application rather than a chat-only demo: tool results are traceable, every conclusion must cite run-scoped evidence, and high-impact actions require human approval.

## Status

Week 10 runs on the machine's installed Java 8. `run_mock.bat` still shows `MOCK_MODE`. The platform covers routing, tool-calling investigation, profiles, retrieval, trajectories, evidence verification, and hard cases. See [docs/architecture.md](docs/architecture.md) and [docs/api.md](docs/api.md).

## Planned Architecture

```text
Browser UI
    |
Java Spring Boot platform
    |- incident state machine
    |- policy and hard-constraint evaluation
    |- idempotency, outbox, audit, and evidence chain
    |- human approval workflow
    |
Python FastAPI AI service
    |- video analysis adapters
    |- retrieval and trajectory tools
    |- local or cloud LLM routing
    |- schema-constrained Agent tool calling
    |
Local runtime
    |- H2 file database
    |- local media/evidence storage
    |- deterministic device/event simulator
    |- in-process queue and outbox
    |- owner-configured OpenAI-compatible model API
```

## Software-only Boundary

- No physical cameras, access-control devices, elevators, edge boxes, vendor SDKs, or private hardware protocols.
- Device status, telemetry, access events, and simulated actions come from a deterministic software simulator.
- Optional RTSP is a local software-fed test stream, not a hardware integration.
- Performance results describe software inference on the development machine, not edge-chip performance.

## Core Engineering Goals

- Deterministic replay of business decisions
- Run-scoped evidence IDs enforced by JSON Schema
- Explicit evidence-insufficient fallback instead of unsupported conclusions
- Human approval gates for high-impact actions
- Windows-friendly local startup without Docker or external infrastructure
- Positive, negative, and adversarial evaluation suites

## Repository Layout

```text
platform/       Java business platform
ai-service/     Python AI and Agent service
web/            Browser management UI
contracts/      API and Agent tool schemas
docs/           Public architecture and planning documents
scripts/        Local setup and development scripts
```

`platform/` is the Java service, `ai-service/` is the Python agent, `web/` is the browser page, and `contracts/` holds the tool and report shapes. `eval/positive/` and `eval/reverse/` hold the scenario files. `run_mock.bat` starts the mock loop.

## Data and Privacy

- No real surveillance footage or personal data in the repository
- Synthetic or explicitly licensed demo data only
- Model weights are not committed
- Secrets and local runtime data are excluded from version control

## License

No open-source license has been selected yet. All rights are reserved unless a license is added.
