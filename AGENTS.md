# CarromVision Development Guidelines & Agent Instructions

Welcome to the CarromVision project. This document outlines the architectural principles, coding standards, and project rules that all developers and AI agents must follow.

## 1. Architectural Principles (MVVM & Clean Architecture)

The project follows **Model-View-ViewModel (MVVM)** combined with Clean Architecture principles, ensuring clear separation of concerns, testability, and maintainability.

### Layers & Packages (`com.example.carromvision`)
- **`ui/`**: Presentation layer containing Jetpack Compose screens, ViewModels, and UI themes (`ui/theme/`). ViewModels manage UI state using `StateFlow` and handle UI events.
- **`domain/`**: **Pure Kotlin domain layer**. Contains business models, domain use cases, and repository interfaces. 
  - **CRITICAL**: The `domain` layer must **NEVER** depend on the Android framework, Android SDK, or external data/UI libraries (no `android.*`, no Room annotations directly in domain models, no Compose, etc.).
- **`data/`**: Data layer containing repository implementations, local data sources (Room, DataStore), remote data sources, and database/network entities. Mappers translate data models to domain models.
- **`cv/`**: Computer Vision layer. Houses image processing pipelines, board detection, piece tracking, OpenCV/ML integrations, and frame analysis algorithms.
- **`overlay/`**: AR / Camera Overlay layer. Responsible for rendering real-time graphical overlays, trajectory lines, aiming guides, and visual aids on top of the camera preview or board view.
- **`service/`**: Background services, foreground workers, sensor listeners, and hardware integration handlers.

## 2. Strict File Size & Modularity Rules

- **File Length Limit**: Keep files concise. Try to keep source files under **300-400 lines of code**. If a file grows larger, refactor and break it down into smaller, focused components, helper classes, or sub-packages.
- **Single Responsibility Principle**: Each class, composable, and function should have one well-defined responsibility.
- **Composable Decomposition**: Break large Jetpack Compose UI hierarchies into small, reusable, previewable composables.

## 3. Coding Standards & Best Practices

- **Kotlin Idioms**: Use idiomatic Kotlin (immutability with `val`, extension functions, coroutines for asynchronous work, sealed interfaces/classes for UI states).
- **Coroutines & Flow**: Use `suspend` functions for asynchronous operations and `StateFlow` / `SharedFlow` for reactive UI state streams. Avoid blocking the main thread.
- **Dependency Injection**: Design components to support dependency injection (manual or via Hilt) for clean testability.
- **Error Handling**: Use Result wrappers or structured exception handling rather than unhandled crashes.

## 4. Testing & Verification

- Unit test domain use cases and ViewModels.
- Ensure all builds pass successfully after changes (`./gradlew assembleDebug`).
