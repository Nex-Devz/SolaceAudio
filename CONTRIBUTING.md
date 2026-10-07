# Contributing to SolaceAudio

Thank you for your interest in contributing to **SolaceAudio**!

## Development Setup

### Prerequisites
- **JDK 17** or newer installed.
- Git.

### Building
Clone the repository and compile using the Gradle wrapper:

```bash
git clone https://github.com/Nex-Devz/SolaceAudio.git
cd SolaceAudio

./gradlew clean build -x test
```

The resulting plugin artifact is generated in `plugin/build/libs/`.

---

## Contribution Workflow

1. **Fork** the repository and create a feature branch:
   ```bash
   git checkout -b feature/my-cool-improvement
   ```
2. **Make your changes**:
   - Follow the existing modular architecture (`com.solaceaudio.model`, `com.solaceaudio.resolver`, `com.solaceaudio.sources.*`).
   - Keep dependencies minimal; rely on native Java `HttpClient`.
3. **Verify Build**:
   - Ensure `./gradlew clean build -x test` passes with zero errors before submitting.
4. **Submit a Pull Request**:
   - Open a PR against the `main` branch.
   - Describe the changes clearly and link any associated issues.

---

## Community & Discussions

Need guidance or want to bounce an idea off the team? Join us on Discord: **[discord.gg/devz](https://discord.gg/devz)**.
