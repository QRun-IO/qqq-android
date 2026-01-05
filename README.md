# qqq-android

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Android client library for QQQ applications.

**For:** Developers building native Android apps that connect to QQQ backends  
**Status:** Experimental

## Why This Exists

QQQ applications expose REST APIs that any client can consume. For Android apps that need to interact with QQQ backends, this library provides typed clients and UI components that understand QQQ's metadata model.

## Features

- **API Client** - Typed client for QQQ REST endpoints
- **Metadata Loading** - Fetch table and field definitions
- **Record Operations** - Query, insert, update, delete records
- **Process Execution** - Run backend processes from Android

## Quick Start

### Prerequisites

- Android Studio
- Android SDK 26+
- QQQ backend running

### Installation

Add to `build.gradle`:

```groovy
dependencies {
    implementation 'io.qrun:qqq-android:0.1.0'
}
```

### Configuration

```kotlin
val client = QqqClient.Builder()
    .baseUrl("https://your-qqq-app.com")
    .build()
```

### Basic Usage

```kotlin
// Fetch records
val orders = client.query("order")
    .filter("status", "pending")
    .limit(20)
    .execute()

// Create record
val newOrder = client.insert("order", mapOf(
    "customerName" to "Acme Corp",
    "total" to 100.00
))

// Run process
client.runProcess("orderSync")
```

## Usage

### Authentication

```kotlin
val client = QqqClient.Builder()
    .baseUrl("https://your-qqq-app.com")
    .authToken("your-api-token")
    .build()
```

### Querying Records

```kotlin
// With filters
val results = client.query("customer")
    .filter("status", Operator.EQUALS, "active")
    .filter("createdDate", Operator.GREATER_THAN, "2024-01-01")
    .orderBy("name", SortOrder.ASC)
    .execute()

// Get single record
val customer = client.get("customer", 123)
```

### Modifying Records

```kotlin
// Update
client.update("order", 123, mapOf(
    "status" to "shipped"
))

// Delete
client.delete("order", 123)
```

## Project Status

Experimental. Core functionality is being developed.

### Roadmap

- Offline caching
- Background sync
- UI components for common patterns
- Kotlin coroutines support

## Contributing

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

Apache-2.0 - See [LICENSE](LICENSE) for details.
