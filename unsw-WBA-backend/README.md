# UNSW WBA

UNSW COMP9900 W13C ALMOND WBA(Workplace-Based Assessments) backend.

---

## Introduction

This system serves as the backend service for the project, employing a high-performance and highly scalable architecture. It primarily provides core business support such as data processing, business processing, notification push, email, and calendar integration services.

---

## Overview
- Java version: 25
- Key Components version：
    - spring-boot-dependencies 4.1.0
    - MybatisPlus 3.5.17
    - springdoc-openApi-starter 3.0.3
    - redisson 4.6.1
    - commons-io 2.2.0
    - org.dhatim.fastexcel 0.20.2
    - org.mnode.ical4j 4.3.0
    - google-api-services-calendar v3-rev411-1.25.0
    - google-oauth-client 1.39.0
    - google-http-client-gson 2.1.1
- Real-time API doc：[OpenApi (Production environments need to be shut down)](http://127.0.0.1:8080/swagger-ui/index.html)
- Online API doc：[ApiFox (Need to be invited)](https://app.apifox.com/project/8489839)

---

## Development Declare
This project adopts a multi-module structure. The `api` module is used to write controllers and aspects, the `service` module is used to write business logic, the `domain` module is used to write entity classes, the `dao` module is used to write database operations, and the `core` module is used to write public classes, such as utility classes, configuration classes, constant classes, etc.

- The `main` branch itself prohibits anyone from directly pushing to it. Therefore, before starting to write code, please pull the latest code from the `main` branch, then create your own development branch from `main`, push the code to your branch, and then submit a `Pull Request` on GitHub to merge it into the `main` branch. After merging, if you want to continue developing the next feature, you must merge the latest code from `main` into your own branch before developing to avoid conflicts.

- When injecting beans, avoid static injection, setter injection, or the `@Autowired` annotation. Prioritize constructor injection.

- Use `@OpenAPI` annotations for controllers and entity classes to facilitate documentation generation. (Avoid using older annotations like `@ApiModel`; use standard `@OpenAPI` annotations to prevent incorrect documentation.)

- All project configuration classes should be located in the `config` package under the `core` module. Avoid moving these classes. For example, moving the MyBatis Plus metadata handler configuration outside the `core` module will prevent entity scanning. Keeping configuration classes in the `core` module is the safest approach.

- Controller return values should consistently be `Result<T>` values from the `domain.pojo` package under the `core` module. Throw `BusinessException` for all business exceptions, preferably using the `ResponseEnum` enumeration to define exception information.

- MyBatis Plus's metadata handler is pre-configured with commonly used fields such as `createTime`, `editTime`, and `isDelete`. Enable or add fields as needed. 6. When using multithreading, use the `@Async` annotation or inject `ThreadPoolTaskExecutor` to configure the thread pool and pass ThreadLocal. Do not manually start a new thread pool, as this will likely cause ThreadLocal variable pollution.

- Currently, request path prefixes are mainly used to distinguish interfaces with different roles. For example, for student-related requests, the controller is written under `edu.unsw.api.controller.student`, and so on.

---

## System Architecture

### Core module
1. **Core Automation System**：Fully automated request interception, log tracing, login interception, permission verification, cross-domain configuration, and rate-limiting configuration management.
2. **Network and Interaction Layer (Web/API Layer)**：Provides a RESTful interface, integrates Spring Validation for defensive input parameter validation, implements log tracing, and enables automatic permission validation based on user roles.
3. **Real-time communication Subsystem**：SSE-based long-connection real-time message push engine supports multi-terminal/multi-tab pages being online at the same time.
4. **Message Consumption Subsystem**：It asynchronously decouples high-concurrency tasks (such as batch emails and message status transitions), eliminates message duplication issues, and has the ability to preempt and self-heal dead/pending messages.
5. **Mail Subsystem**：Supports HTML responsive card emails, CID embedded images, dynamic input stream attachments, and custom sender aliases (Personal).
6. **Recurrence Rule Subsystem**：Following the RFC 5545 specification, accurately calculate the generation trajectory of complex periodic events (such as every other week or specific days of the week).
7. **Calendar Subsystem**：Supports adding schedules to Gmail and Outlook Calendar.

---

## Design Justifications & Highlights

### Highlight 1: Database Design
Our database design features no redundant fields, no large fields, and no complex table constraints or joins. While adhering to enterprise-level design standards, we have fully implemented single-table queries and created perfect composite indexes for the data tables. A single index can cover multiple query conditions. This single-table design combined with well-designed indexes ensures sustainable database performance.

### Highlight 2: Dynamic Role-Based Permission Tree
* **Design decisions**: To ensure the future scalability of role and permission management, we designed a dynamic role-based permission tree.

### Highlight 3: Enhanced Password Storage
Utilizing Spring Security's standard BCrypt implementation, user passwords are virtually impossible to crack.

### Highlight 4: Annotation-Based Rate Limiting System Based on Redisson + Lua Scripts
We implemented a more powerful rate-limiting solution than Redisson's methods using custom Lua code. Annotations can be used to implement any form of rate limiting for a class or even a specific method (based on IP address, user ID, session ID, browser identifier, 10 requests per 3 seconds, etc.).

### Highlight 5: A Highly Fault-Tolerant Distributed Consumption Engine Based on Redis Streams
* **Design decisions**: For our message notifications, including upcoming and expired event notifications, we did not use traditional polling databases or heavyweight RabbitMQ/Kafka deployments. Instead, we chose Redis Streams to implement a lightweight message queue. Because these messages are not core business logic, performance doesn't need to be sacrificed for high reliability. Furthermore, our stream design doesn't compromise high availability.
* **Technological advantages**: 
  * **Fault Recovery**: We designed `consumeOwnPendingMessages` and `claimIdlePendingMessages` mechanisms. When a consumer node fails, other nodes automatically preempt and take over unacknowledged pending messages (PELs) after 30 seconds, completely avoiding single points of failure. The message retry mechanism ensures that consumption can automatically resume after all nodes recover from a fault, without message loss.
  * **Stream Trimming**: A dual defense of "consumption count + time window" is introduced, and `trimNonStrict` pruning is performed regularly to prevent Redis memory from bloating while preserving the amount of data required for auditing.
  * **Message atomization**: Due to issues with distributed SSE sessions, using streams ensures that every machine in the cluster receives SSE messages while preventing message duplication.

### Highlight 6: Event Upcoming and Expiration Reminders
Event reminders cannot be handled simply like order expiration using a delayed queue. Adding all events to a delayed queue would consume significant memory due to long delays and pose a high risk of message loss. Therefore, we adopted a coarse-grained approach using scheduled tasks to fetch data from the database, and a precise sending approach using Redis streams and Redis delayed queues. A carefully designed time window ensures high performance when fetching data from the database while guaranteeing no messages are missed (including events added or deleted by users within the time window). Even with redundant messages, our custom-implemented deduplication delayed queue and stream consumption ensure no duplicate pushes.

### Highlight 7: SSE real-time push system supports concurrent access across multiple devices and tabs
* **Design decisions**：Establish a `SseEmitterRegistry` registry center, optimize the storage structure to `Map<String, CopyOnWriteArrayList<SseEmitter>>`, and support distributed deployment.
* **Technological advantages**：
  * This perfectly solves the problem of missed messages when the same user is online in multiple browser tabs or on multiple devices simultaneously.
  * Combined with Redisson RTopic, this enables **distributed cluster broadcasting**: message publishing only requires targeting the `userId`, regardless of which machine the user's connection is mounted on in the cluster, ensuring accurate push to all active clients. Furthermore, asynchronous push and decoupling are achieved.

### Highlight 8: Production-grade smooth and graceful shutdown
* **Design decisions**：In the thread pool and long polling shutdown chain, we performed in-depth optimization of the Spring destruction lifecycle.
* **Technological advantages**：
  * By implementing the `DisposableBean` and `@PreDestroy` hooks, the thread pool shutdown mode is refactored from forced interruption (`shutdownNow`) to "smooth buffered waiting (`shutdown` + `awaitTermination`), avoiding message and request loss caused by violent shutdown.
  * Gracefully identify `InterruptedException` exception chains to avoid generating glaring ERROR stacks during server shutdown or restart, and ensure that connections are only released after ongoing transactions have been fully ACKed.

### Highlight 9: Zero-memory brute-force high-performance Excel processing engine based on FastExcel
* **Design decisions**：Instead of the traditional Apache POI mode (which builds a complete DOM tree in memory, making it prone to OOM crashes when uploading large files of hundreds of thousands), we adopted the lightweight `FastExcel` and `FastExcel-Reader` solutions.
* **Technological advantages**：
  * The underlying layer uses XML event streams (Stream API) for line-by-line extraction, saving nearly **10 times** JVM heap memory compared to Apache POI, resulting in a significant improvement in parsing speed.
  * The general utility class automatically and uniformly handles data types such as numbers, strings, and dates (`cell.getText()`), avoiding complex type judgment logic and enabling fast structured data conversion (`List<List<String>>` / `List<Map<String, String>>`).

### Highlight 10: A periodic scheduling engine that strictly adheres to RFC 5545 specifications.
* **Design decisions**：It integrates `ical4j` and uses the standard RRULE string representation algorithm.
* **Technological advantages**：
  * The `DTSTART` function is cleverly designed as a "baseline anchor point," allowing the front end to directly input any start time (e.g., setting Tuesday to repeat every Wednesday). The algorithm will automatically and accurately calculate the first compliant Occurrence execution point in the future, simplifying front-end interaction and back-end computational complexity.

### Highlight 11: Carefully crafted Dockerfile deployment file
Integrates JDK configuration, log splitting, log level management, database deployment and initialization, Redis deployment and initialization, Nginx request forwarding, Nginx high-performance file proxy, network attack protection, request buffering, long connections, persistent volume mounting, etc. Furthermore, the backend code configuration files automatically differentiate between production and development environments, allowing for one-click deployment of the entire system's runtime environment.

---

## Deployment & Configuration

### Configuration file description (`application-*.yml`)

The configuration file is divided into multiple parts, and the file extension indicates the scope of the configuration file.

After the project goes live, the email configuration may need to be changed.

The main spring configuration file is named `application.yml`.

The configuration file is named `application.properties`.

The configuration file of the database is named `application-db.yml`.

The configuration file of the redis is named `application-custom.yml`.

## Run and Deployment Steps

- Environment setup: JDK 25, Redis 6.0+ (must support Stream feature).
- Packaging command: tentative
- Startup command: tentative

## Ops & Troubleshooting

- Database fuzzy query is not case-sensitive: This is normal database behavior. If you need to force case sensitivity, please add the BINARY keyword before the query field or adjust the sorting rule to _bin.