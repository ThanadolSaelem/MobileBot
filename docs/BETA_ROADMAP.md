# GooseDroid: Beta Release Roadmap

This roadmap outlines the steps to reach the Beta milestone, transforming the application from a simple AI animated pet into a highly functional, secure, and customizable "Autonomous Desktop Unit" (System Buddy).

## Phase 1: Core Intelligence & Customization (Completed)
- [x] **LLM Integration Engine:** Pluggable AI engine (Cloud via Gemini API / Local via LLaMA).
- [x] **Physics & Overlay Rendering:** Smooth desktop overlay, physics engine (gravity, dragging).
- [x] **Dynamic Prompt Injection:** Separating strict JSON system directives from user-defined personality traits.
- [x] **Custom Persona Support:** Allowing users to define specific roles and personalities for each unit (e.g., "Sassy robotic cat", "Formal Jarvis-like assistant") via the Editor UI.

## Phase 2: Secure Utility & Connectivity (Completed)
- [x] **Plugin Architecture (MCP-Lite):** Refactor the `AiManager` to query a `ToolRegistry` for available system actions, moving away from hardcoded commands. This enables the "Everything is a plugin" philosophy.
- [x] **Secure Web Drop Zone (Cross-Device File Transfer):**
    - Implement a lightweight Local HTTP Server (`Ktor` or similar).
    - **Security:** On-demand activation (only runs when requested), strict whitelisting of file types (images, text, PDF), and URL tokenization.
    - **UI/UX:** QR Code generation for mobile scanning, simple web interface for PC dragging-and-dropping.
    - **User Consent Pipeline:** Explicit user confirmation UI before rendering/processing received files.
- [x] **Clipboard Integration:** Allow the unit to read and manipulate the system clipboard upon user request (e.g., "Translate what I just copied").
- [x] **Android Intent/Share Target:** Register the application as a share target so users can send links/images directly from other apps (like Twitter, Gallery) to their Unit.

## Phase 3: System Awareness (Completed)
- [x] **Accessibility Service Integration:** Grant the unit screen-reading capabilities to summarize or answer questions about the currently displayed app/content.

## Phase 4: Polish & Beta Release
- [x] **UI Refinement:** Enhance the chat interface, Editor UI, and setting screens following Material 3 guidelines.
- [x] **Performance Profiling:** Ensure battery consumption is minimized while the overlay and local server are active.
- [ ] **Beta Testing & Feedback Loop:** Internal testing and release preparation.