# Codex Instructions

- Maven must use the repository-local cache configured in `.mvn/maven.config`.
- Never use a Maven local repository under `target/`, including paths such as `target/.m2-verify` or `target\.m2-verify`.
- If a Maven command needs an explicit local repository override, use `.mvn/repository` instead of any `target/` path.
