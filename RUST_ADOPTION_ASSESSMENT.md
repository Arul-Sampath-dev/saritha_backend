# Rust Adoption Assessment

**Project reviewed:** SarithaFashion backend  
**Review date:** 2026-11-23  
**Purpose:** identify realistic Rust learning and implementation opportunities, with attention to memory, security, and concurrency.

## Executive summary

This repository is a **Java 17 / Spring Boot 3 backend**, not a Python application. It uses MongoDB, Spring MVC/Security, Apache POI, and an external Cloud Run service for DOCX-to-PDF conversion. The Python-to-Rust package commonly meant here is **PyO3** (often paired with **maturin** to build/install a Python extension). PyO3 does not convert Python source to Rust automatically, and it does not integrate directly with this Java application.

There are Rust/JVM integration paths (JNI, Java Native Access (JNA), or a separate Rust HTTP/gRPC service), but introducing them has deployment, packaging, data-copy, and operational costs. The current code gives no evidence that core request handling or ordinary CRUD is CPU-bound or memory-bound enough to warrant such a boundary. First measure the JVM and workload.

**Best fit if this were a Python project:** a PyO3 + maturin extension for a CPU-heavy, well-bounded function (for example image resizing/validation, document parsing, or a batch transform), keeping the web framework, database access, and business rules in Python.

**Best fit for this Java project:** keep business logic and MongoDB access in Spring; consider a separate Rust worker/service only for an independently scalable CPU-heavy file-processing workload. PDF conversion is already offloaded to a remote service, and report Excel is already streamed with Apache POI SXSSF, so neither currently proves a need for Rust.

## Project profile from the repository

- `pom.xml` sets Java 17 and Spring Boot 3.4.3; it includes MongoDB, Spring Web/Security, validation, WebSockets, Caffeine, Apache POI, and cloud/messaging SDKs.
- Invoice, payment, expense, production, employee, shop, authentication, and reporting functions are organized as Spring modules and use MongoDB repositories / `MongoTemplate`.
- `DocxToPdfConverter` sends a complete DOCX byte array to a remote converter and receives a complete PDF byte array. This is principally network and conversion-service work, not an obvious Rust hot loop in this repository.
- `BranchReportExcelWriter` uses `SXSSFWorkbook(200)`, Apache POI's streaming workbook implementation. The report service limits requested periods to 366 days, but it also collects multiple date-range result lists into memory before export; result volume can exceed the day count.
- Branch PDF generation builds a DOCX in memory and converts it remotely. Its PDF writer caps some displayed lists at 500 rows, while the report data builder still reads the source lists and assembles report data.
- The application has a Spring task executor sized 10 core / 20 max with a queue of 30, a separate notification executor, Caffeine cache with a maximum size of 1,000, and a per-IP rate-limiter map in a `ConcurrentHashMap`.

These observations are repository-based. They are not measurements of production heap, allocation rate, latency, traffic, or CPU.

## Ranked Rust opportunities

| Priority | Area | Fit | Why / current evidence | Suggested boundary |
|---|---|---|---|---|
| 1 (learning prototype) | File/image processing, if profiling finds it expensive | **Potentially good** | Invoice-related image operations or document processing can be CPU- and allocation-heavy. Repository scan alone does not show a hot image-processing loop; verify the implementation and profile first. | Isolated Rust HTTP worker or JNI function taking a bounded input and returning transformed bytes/metadata. Preserve authorization, storage, and invoice rules in Spring. |
| 2 (if report sizes justify it) | Large export formatting / aggregation | **Conditional** | Exports materialize report data, while XLSX is already streamed. Rust could be useful for compute-heavy transformations, but Excel generation itself and upstream Mongo queries are not established bottlenecks. | Rust batch worker receiving a bounded, versioned report payload; stream output to object storage rather than returning huge arrays. Compare against Java streaming and database aggregation first. |
| 3 (security/learning exercise) | Small, pure security primitive | **Low production value** | Rust can implement a narrow parser or verification primitive, but Spring Security/JWT already has Java libraries. FFI adds complexity and does not itself fix authorization or credential management. | Do not move auth policy, password handling, JWT lifecycle, or tenant checks into Rust. At most prototype an isolated parser with fuzzing and clear ownership semantics. |
| 4 | PDF conversion | **Poor current fit** | DOCX conversion is already delegated to a remote service with timeouts. A Rust service would duplicate a boundary unless the external converter is replaced for a concrete cost/latency reason. | Retain the service boundary; if replacing, benchmark a Rust PDF stack against fidelity, fonts, layout, operational needs, and compatibility first. |
| 5 | CRUD, MongoDB repositories, controllers, financial business rules | **Poor fit** | These are database- and network-oriented Spring functions. Rust does not reduce MongoDB server memory or make database round trips cheaper; bridging adds copies and complexity. | Keep in Java. Improve query projection/indexing/pagination and bound concurrency if measurements point there. |

## Memory assessment

Rust can reduce per-process runtime overhead and avoid garbage collection for a standalone worker, but it will **not automatically lower this application's total memory**. A JNI library shares the JVM process and its address space; native allocations still count toward container memory and can make OOM diagnosis harder. A separate Rust service adds another process and usually increases baseline memory. For a Python extension, PyO3 can reduce object overhead in a CPU/data path, but crossing the Python/Rust boundary and converting Python objects may erase gains.

Likely memory pressure points to measure here:

1. **PDF byte-array path:** DOCX and returned PDF bytes coexist during a conversion, alongside multipart/request buffers. Concurrent exports multiply that peak. Stream to/from temporary storage or use a bounded export queue if profiles confirm this peak.
2. **Branch report data:** several raw Mongo result lists and derived report lists coexist. Use projections, pagination/cursors, aggregation, and bounded export sizes. A date limit of 366 days is not a row limit.
3. **Concurrency limits:** up to 20 general executor threads plus request threads and other executors can amplify per-request document memory. Set explicit limits for expensive export/PDF operations and backpressure.
4. **Cache policy:** `maximumSize(1000)` limits entries, not bytes. Large cached values can still occupy substantial heap; use weights/size-aware limits if cached values are large.
5. **Native or Rust usage:** track process RSS/container memory as well as JVM heap. Avoid passing unbounded file payloads through JNI or storing duplicate `byte[]` copies.

## Security and concurrency considerations

### Security

- A language switch does not fix application-level authorization, input validation, tenant isolation, or secret handling. Keep those controls at the Spring API boundary and test them there.
- Production and testing properties appear to contain literal credential-like values (database URI, JWT secret, email/SMS credentials, and cloud credential configuration). Treat exposed values as compromised: rotate them, remove them from version control/history as appropriate, and inject them from a secret manager/environment. This report intentionally does not repeat the values.
- The production properties configure verbose framework/application logging and debug output; review these settings before deployment to avoid logging sensitive data and excessive overhead.
- `RateLimitingFilter` creates one bucket for every remote IP in an unbounded map. High-cardinality IP traffic can grow memory without bound. Use an expiring bounded cache or a shared rate limiter, and ensure proxy-derived client IP handling is trusted/configured correctly.
- A Rust native library runs in-process with JVM privileges. Memory safety helps prevent certain memory-corruption errors in Rust code, but does not sandbox the code or stop logic bugs, denial of service, or unsafe dependencies.

### Concurrency

- Rust's ownership model and `Send`/`Sync` checks help prevent data races in safe Rust. JNI/JNA boundaries can still introduce races, lifetime bugs, deadlocks, and unsafe code if buffers or callbacks cross threads.
- A separate service makes failure and resource limits clearer but introduces network failure, retries, authentication between services, observability, and deployment work.
- The most immediate concurrency win may be controlling expensive PDF/export operations. Define a small bounded executor/queue, timeouts, maximum payload/row sizes, and overload behavior. Avoid `CompletableFuture.runAsync` on the common pool for work that needs explicit capacity management.
- The current PDF warmup uses `CompletableFuture.runAsync`; its task is short and rate-limited, but a dedicated managed executor would make lifecycle and capacity clearer.

## What to do before adopting Rust

1. Establish a baseline under representative load: container RSS, JVM heap after GC, allocation rate, GC pauses, CPU, request p95/p99, Mongo query duration, and concurrent PDF/export counts.
2. Profile representative slow/high-memory endpoints. Determine whether the time is in Mongo queries, Java mapping/formatting, POI, serialization, or remote conversion.
3. Fix unbounded inputs/state and avoidable copies first: bound report rows/bytes, stream results/files, project only needed Mongo fields, review cache weight, and cap export concurrency.
4. Benchmark one isolated candidate against the improved Java implementation, including build/deployment complexity and end-to-end latency/memory (not just the inner function).
5. Adopt Rust only if it materially improves a measured bottleneck without weakening correctness or making the system harder to operate.

## Practical learning path

### If the goal is Python-to-Rust interoperability

Use **PyO3 + maturin** in a small, separate Python prototype. Implement one pure function such as parsing/normalizing a large CSV or validating/resizing image data. Keep Python responsible for HTTP, database access, auth, and business policy. Compare output parity, throughput, peak RSS, and conversion overhead against pure Python. This teaches Rust integration without misrepresenting the current repository as Python.

### If the goal is Rust with this Spring Boot application

Start with a separate Rust HTTP worker for a bounded CPU-heavy batch task. Define a versioned JSON or binary contract, request-size limits, service authentication, timeouts, idempotency, and metrics. First prototype with a non-critical input and compare with a Java implementation. Do not begin with JNI: native deployment/build setup is harder and shares process failure/memory characteristics with the JVM.

### JNI/JNA versus a service

- **JNI:** lower call overhead, but more complex native builds, ABI/platform packaging, ownership/lifetime hazards, and JVM crash exposure from unsafe native code.
- **JNA:** simpler binding for many C-compatible APIs, often more overhead and still native-memory/lifetime concerns.
- **Separate Rust service:** straightforward language isolation and independent resource limits, but network and operations overhead. Usually the safer first experiment for a backend team learning Rust.

## Recommendation

Do not plan a broad Java-to-Rust rewrite for memory savings. The strongest practical project is to measure and bound the PDF/report pipeline and fix unbounded state/credential handling first. For learning, build a small PyO3/maturin extension if Python is the target skill, or a standalone Rust worker if the goal is to integrate Rust with this Spring application. Only promote either into production after an end-to-end benchmark shows a meaningful gain.

## Evidence files reviewed

- `pom.xml`
- `Dockerfile`
- `src/main/java/com/saritha/fashions/modules/core/pdf/DocxToPdfConverter.java`
- `src/main/java/com/saritha/fashions/modules/core/pdf/SalesDocumentPdfService.java`
- `src/main/java/com/saritha/fashions/modules/report/export/BranchReportService.java`
- `src/main/java/com/saritha/fashions/modules/report/export/BranchReportPdfWriter.java`
- `src/main/java/com/saritha/fashions/modules/report/export/BranchReportExcelWriter.java`
- `src/main/java/com/saritha/fashions/config/AsyncConfig.java`
- `src/main/java/com/saritha/fashions/config/CacheConfig.java`
- `src/main/java/com/saritha/fashions/security/RateLimitingFilter.java`
- `src/main/resources/application-production.properties`

