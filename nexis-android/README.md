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
- Local model: file picking + persisted link (LocalModelScreen) + llama.cpp JNI wired in (src/main/cpp, LocalLlamaEngine, LocalModelAdapter as "local" provider in every assistant's fallback chain, works offline, no API key) — UNTESTED, first real build+run needed
- Telegram integration
