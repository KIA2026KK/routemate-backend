# RouteMate backend

- Inspect existing code before editing. Keep changes within the requested feature.
- Java 21, Spring Boot, Gradle, PostGIS, Redis and Flyway form one backend application.
- Keep feature packages auth, user, trip, matching, room, voice and common.
- Verify SDK APIs and dependency compatibility using official documentation.
- Never commit passwords, tokens, API secrets or local .env files.
- Explain deletions and structural changes before making them.
- Report build and test results honestly; unexecuted checks are not successes.
- Keep API contracts aligned with the Android repository.
- Do not add chat, friends, AI recommendations or other extra MVP features.
- Apply DDD within each feature: domain, application, infrastructure and presentation.
- Domain must not depend on controllers, SDKs or infrastructure implementations.
- Put use-case orchestration and transaction boundaries in application; adapters in infrastructure.
- Add aggregates and repository ports only when required by actual business invariants.
- Branch every API/feature from up-to-date develop, not main.
- Submit feature PRs to develop. After checks pass, promote develop to main via a separate PR.
- Never push feature changes directly to develop/main or merge failed checks.
- Use one feature branch per change and recommend a commit message when finished.
