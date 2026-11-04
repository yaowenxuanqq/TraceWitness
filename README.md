# TraceWitness

**AI-Native Video Incident Investigation Agent**

> Trace the scene. Verify the evidence.

TraceWitness is an evidence-grounded AI application for video incident investigation. It combines video event processing, natural-language investigation, spatiotemporal retrieval, trajectory reconstruction, and auditable evidence reporting.

The project is designed as a real Agent application rather than a chat-only demo: tool results are traceable, every conclusion must cite run-scoped evidence, and high-impact actions require human approval.

## Status

Planning and early implementation. The current 12-week delivery plan is available in [docs/project-plan.md](docs/project-plan.md).

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
    |- in-process queue and outbox
    |- llama.cpp + local GGUF model when available
```

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

The layout will be introduced incrementally as implementation starts.

## Data and Privacy

- No real surveillance footage or personal data in the repository
- Synthetic or explicitly licensed demo data only
- Model weights are not committed
- Secrets and local runtime data are excluded from version control

## License

No open-source license has been selected yet. All rights are reserved unless a license is added.