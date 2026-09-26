# Nexis

Kotlin + Jetpack Compose. Build via GitHub Actions (`.github/workflows/build-apk.yml` at repo root → `nexis-debug-apk` artifact).

## Done
- Login/Home/Projects/Conversations/Chat/Settings/Drawer screens
- Adapters: Claude, ChatGPT, Gemini, KiMi (OpenAI-compatible base class ready for custom models)
- Profile edit (name), new chat-screen header glyphs
- FallbackOrchestrator (multi-key failover → next model, max 5) + head health layer
- Encrypted key store, local persistence, GitHub backup

## Pending
- NotebookLM (no public API)
- Custom model "+" dialog done (CustomModelStore, OpenAI-compatible) + Groq/OpenRouter templates + OpenRouter code-paste login (OpenRouterAuth)
- Google sign-in (Credential Manager) coded; bypass stays until GoogleAuth.WEB_CLIENT_ID is filled (login postponed to last)
- GitHub login still a bypass
- Plugins screen
- Local model: file picking + persisted link (LocalModelScreen) + llama.cpp JNI wired in (src/main/cpp, LocalLlamaEngine, LocalModelAdapter). Now its OWN assistant (AssistantRole.LOCAL, third card on Home) with its own projects, not mixed into Coding/Chat's API-key model list. Offline, no API key. — UNTESTED, first real build+run needed
- Telegram integration


## Native ABIs
Built for arm64-v8a and armeabi-v7a (32-bit) — some Android 10/budget phones are 32-bit-only and got "App not installed" when only arm64-v8a was built.

## Fixed debug signing
app/debug.keystore is committed on purpose: without it every CI run signs debug builds with a random key, so installing a new build over an old one fails ("App not installed") on any device that already has a previous build. Do not delete/regenerate it.
