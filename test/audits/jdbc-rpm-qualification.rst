JDBC and Flyway qualification audit
===================================

Copyright 2026 Qore Technologies, s.r.o.

Scope: pending JNI JDBC native fixes, Flyway output contracts, their regression fixtures and configured-fixture failure handling. RPM recipe and vendor bundling are reviewed separately. Existing synchronous JDBC driver calls retain their API contract; this audit does not certify interruption of arbitrary third-party JDBC driver calls. Validation evidence is retained in qore-packaging/results and evidence/jni-external-diagnostics-20261002.json. The user approved the documented external JVM/glibc diagnostic exception on 2026-10-02.

.. list-table:: Complete audit-changes checklist
   :header-rows: 1

   * - Check
     - Status
     - Evidence

   * - 1. Entry exists in doxygen/lang/120_modules.dox.tmpl (for modules in the Qore repo; N/A for external module repos)
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 2. Entry exists in doxygen/lang/900_release_notes.dox.tmpl (for modules in the Qore repo; external modules have release notes in their .qm)
     - Pass
     - FlywayDataProvider.qm release notes and docs/mainpage.dox.tmpl describe corrected action output, transaction boundaries, cursor lifetime and batch semantics.

   * - 3. qore_user_module() or qore_external_user_module() call in CMakeLists.txt
     - Pass
     - Existing qore_external_user_module("qlib/FlywayDataProvider") includes the new separated response classes; source and rebuilt AOT tests pass.

   * - 4. Module added to QMOD list in CMakeLists.txt
     - Pass
     - FlywayDataProvider remains in QMOD; the two Java regression fixtures are added to JAVA_TEST_JAR_SRC.

   * - 5. .qm file has @section <lowercasemodname>intro as first doc section — must be all lowercase (e.g., avrodataproviderintro, not AvroDataProviderintro)
     - Pass
     - Existing Flyway module introduction uses flywaydataproviderintro.

   * - 6. %modern in .qm file — no redundant %new-style, %require-types, %strict-args, %enable-all-warnings
     - Pass
     - FlywayDataProvider.qm retains %modern without redundant directives.

   * - 7. No parse directives (%requires, %modern, %new-style) in separated .qc files (check OUTSIDE of @code blocks only)
     - Pass
     - The new FlywayActionResponseDataTypes.qc and changed separated classes contain no parse directives.

   * - 8. No %include usage (deprecated for modules)
     - Pass
     - No new %include directive.

   * - 9. Copyright 2026 on all new files
     - Pass
     - All new files and changed native/test files carry 2026 copyright notices.

   * - 10. Directory layout: .qm inside qlib/<ModuleName>/ directory (not at qlib/<ModuleName>.qm for multi-file modules)
     - Pass
     - The new response classes are under qlib/FlywayDataProvider alongside the existing main module.

   * - 11. No second .qm for the same module at qlib/<ModuleName>.qm
     - Pass
     - No duplicate module entry point is introduced.

   * - 12. ns=Qore::XX matches the QoreNamespace constructor path
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 13. %modern directive present
     - Pass
     - All modified/new Qore tests use %modern.

   * - 14. Executable permission set (chmod +x)
     - Pass
     - All modified/new .qtest files are executable (0755).

   * - 15. Uses %prepend-module-path  before %requires for in-repo modules (Qore and Qore modules only; not Qorus)
     - Pass
     - Provider tests prepend local qlib; JNI/JDBC tests prepend the local build before requiring jni. Qualification explicitly loads the chosen build qmod.

   * - 16. External module dependencies use %try-module — except modules delivered with the project itself (Qore ex: DataProvider, ConnectionProvider, QUnit, etc.) which use hard %requires
     - Pass
     - jni and the provider modules are delivered by this repository and require hard dependencies; optional external xml/python imports retain %try-module.

   * - 17. No filesystem operations (fopen, open, creat, unlink, remove, rename, mkdir, rmdir, stat, chmod) without sandbox checks
     - Pass
     - The changed native paths introduce no raw filesystem access; JDBC work uses an existing datasource.

   * - 18. No network operations (connect, bind, socket, getaddrinfo, gethostbyname) without sandbox checks
     - Pass
     - The changed native paths introduce no raw socket calls or new network endpoints; JDBC work uses the existing DBI API.

   * - 19. If filesystem/network ops exist, verify QoreSandboxManagerHelper usage
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 20. No File::, Dir::, Socket::, HTTPClient:: usage without justification
     - Pass
     - Flyway negative validation uses FsUtil::TmpDir and Qore File APIs to create two private migration fixtures; automatic TmpDir cleanup owns their lifetime.

   * - 21. All for/while loops that could iterate >100 times have qore_check_cancel() checks
     - Pass
     - Batch detection, length validation and update-count loops check cancellation every 100 entries. Expensive parameter binding checks every 10 parameters.

   * - 22. Uses qore_check_cancel() (NOT deprecated qore_check_io_interrupt())
     - Pass
     - New checks use qore_check_cancel, with the minimum supported Qore version already 3.0.

   * - 23. Check frequency: every 100 iterations for tight loops, every 10 for expensive iterations
     - Pass
     - The 100/10 check intervals match the affected loop costs; the 200-entry count test crosses a check boundary.

   * - 24. No blocking operations without cancellation support
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 25. Every action has display_name, short_desc (plain text, <80 chars), desc (markdown)
     - Pass
     - Existing migrate/validate/clean/repair actions retain display_name, short_desc and desc; action registration regression checks the corrected outputs.

   * - 26. Every action has options populated via getActionOptionFromFields() — without this, the action shows an empty, unusable form
     - Pass
     - Each affected action retains options from getActionOptionFromFields with the existing constructor fields.

   * - 27. Every action has output_type set to a typed data type constant (e.g., MyResponseDataType) — not omitted
     - Pass
     - Migrate now advertises a list of typed migration results; validation has its own typed response; clean and repair share the success response.

   * - 28. DPAT_API actions: provider has "supports_request": True and implements doRequestImpl()
     - Pass
     - The four providers retain supports_request and doRequestImpl; tests execute actual PostgreSQL operations.

   * - 29. DPAT_FIND actions: every option exists in SearchOptions, getRecordTypeImpl() returns *hash<string, AbstractDataField>
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 30. Scheme-based apps (with "scheme" in registerApp): actions use "path" and do NOT use "cls" — having both scheme and cls causes a runtime error
     - Pass
     - The Flyway scheme actions use path entries, without cls.

   * - 31. Single-key hash slices use trailing comma: Fields{"key",} (without trailing comma, Fields{"key"} returns the value, not a hash)
     - Pass
     - Changed code adds no ambiguous single-key hash slices.

   * - 32. Typed data type classes exist for request and response types — inherit HashDataType, have const Fields hash, call addQoreFields(Fields) in constructor, export public constant at bottom (e.g., public const MyDataType = new MyDataType();)
     - Pass
     - The new output classes inherit HashDataType, declare Fields, call addQoreFields and export constants; input remains NOTHING.

   * - 33. Request/input types use public Fields (enables ClassName::Fields in action registration)
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 34. Response/output types use private Fields
     - Pass
     - Both new response classes declare Fields privately.

   * - 35. Each field in data types has display_name, type, and desc (markdown-formatted)
     - Pass
     - Every new response field provides display_name, type and desc.

   * - 36. Input fields have example_value where useful (string fields, endpoint URIs, SQL queries, etc.)
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 37. Fields with finite allowed values use allowed_values with AllowedValueInfo containing both value and display_name (Title Case, human-readable) — never bare values, never described only in text
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 38. Password/secret fields have "sensitive": True
     - Pass
     - Existing connection password metadata remains sensitive; test passwords are private fixture values, not credentials.

   * - 39. groups uses AppGroup enum values from qlib/DataProvider/AppGroup.qc
     - Pass
     - The existing Flyway application uses AppGroup::Databases.

   * - 40. App logo stored as separate file, loaded at module level in Priv namespace
     - Pass
     - The existing Flyway logo remains a separate SVG loaded at module scope.

   * - 41. App desc uses markdown: bullet list of capabilities, links to project website, business-language explanation of value
     - Pass
     - The existing application description includes capabilities and the Flyway project link.

   * - 42. display_name is user-friendly ("Apache Avro" not "avro")
     - Pass
     - Existing Flyway display names remain user-facing.

   * - 43. short_desc is plain text, under 80 chars, single sentence — no markdown
     - Pass
     - No markdown or long short_desc is added; existing action summaries remain plain text.

   * - 44. desc uses markdown: backticks for code/field refs ( field_name ,  True ,  pdf ), \n\n for paragraphs, -  bullet lists for enumerations, bold for caveats
     - Pass
     - New descriptions use backticks for field names and NOTHING.

   * - 45. Descriptions use plain business language relating to common challenges — not just technical "what" but "why" and "when to use"
     - Pass
     - Response descriptions explain successful completion and migration validation to consumers.

   * - 46. No bare True/False/NOTHING — must be backtick-wrapped in desc
     - Pass
     - The nullable validation error description wraps NOTHING in backticks.

   * - 47. No bare field/option names in prose — must use backticks
     - Pass
     - Names referenced by the invalid-migration description are wrapped in backticks.

   * - 48. Long descriptions (>500 chars) use bold section headers and bullet lists
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 49. Factory registration in Qore repo: every factory name registered in qlib/DataProvider/DataProvider.qc → FactoryMap (without this, module loads but doesn't appear in Qorus apps)
     - Pass
     - Qore DataProvider FactoryMap already maps flyway to FlywayDataProvider; factory and catalog tests pass.

   * - 50. getRecordTypeImpl() signature: must be private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) — NOT returning *AbstractDataProviderType
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 51. Dependency JARs committed (for JNI modules): JAR files in qlib/*/jar/ may be gitignored — use git add -f to ensure they're tracked, otherwise CI compilation fails
     - Pass
     - No third-party Java dependency is added by these native/QLIB fixes. Existing pinned vendor inputs and generated first-party JAR build rules supply the test/runtime classes.

   * - 52. JAR install rules in CMakeLists.txt for all dependency JARs
     - Pass
     - CMake retains installation of the generated Flyway JAR; the new tests are compiled into qore-jni-test.jar.

   * - 53. No workarounds: No TODOs, FIXMEs, stubs, or partially-implemented features
     - Pass
     - Changes fix transaction mode, JNI reference ownership, batch accounting and action type contracts directly. Fixture absence and configured failure are explicitly distinguished.

   * - 54. Exception safety: C++ uses ReferenceHolder for Qore allocations, std::unique_ptr for C++ allocations, *xsink checked after every fallible operation
     - Pass
     - ResultSet and statement handles use GlobalReference RAII and independent cleanup; pending Java exceptions retain owned local handles until rethrow. Failed transaction boundaries abort the datasource; bind errors stop execution. Failure-injection tests verify cleanup and reconnection.

   * - 55. Thread safety: All mutable shared state protected by std::lock_guard<std::mutex> or documented as immutable-after-construction
     - Pass
     - Datasource/statement state is serialized by the existing DBI ownership contract. Cached jmethodID is initialized with the other immutable method IDs. Java fixture state and proxy callbacks use the class monitor.

   * - 56. Type safety: Strongly-typed code<return(args)> instead of untyped code; static_cast instead of C casts; typed hashdecls for results; enums where appropriate
     - Pass
     - The native count conversion validates jint values and int overflow; response fields use typed HashDataType objects; test outputs use typed hashes/lists.

   * - 57. Performance: No O(n²) where O(n) is possible; no unnecessary copies; coordinate descent uses incremental residuals not full matrix multiply
     - Pass
     - Batch validation and count accumulation are linear; no nested rescan of all rows is introduced. Persistent results avoid invalid per-frame local handles.

   * - 58. Error handling: All inputs validated (dimensions, empty data, unfitted models); C++ I/O handles EAGAIN/EINTR if applicable
     - Pass
     - Tests cover empty and mismatched batches, failed binds, failed connection mode changes and transaction boundaries, unknown/overflow counts, empty/non-query results, repeated exceptions and statement reuse.

   * - 59. Documentation: Doxygen @param, @return, @throw on all public methods; @par Example with realistic business scenarios; @note for important caveats
     - Pass
     - Mainpage release notes document behavior and an explicit transaction example; Flyway release notes and return descriptions match the corrected output contracts.

   * - 60. QPP flags: [flags=CONSTANT] on methods that never throw; [flags=RET_VALUE_ONLY] on methods that throw but have no side effects
     - N/A
     - Not introduced or modified in this scope: no new module entry point/QPP class, request schema, finite input enumeration, find action, record signature, or additional raw I/O waiting loop.

   * - 61. Security: No user-controlled format strings; no buffer overflows; bounds checking on array indices; no credentials in code
     - Pass
     - Constant exception formats and validated counts avoid format injection/overflow. Integration servers are private fixtures; no real credentials are embedded.

   * - 62. Correctness: Algorithms verified against reference implementations; edge cases tested (empty data, single sample, all-zero features)
     - Pass
     - Release and Debug builds and 14 CTest checks pass. JDBC mock: 11 cases/102 assertions; PostgreSQL: 4/34; JDBC suite: 3 registered cases/22 assertions. Flyway source and AOT each pass 9/40 including checksum failure. Updated service suites pass 1278 assertions. Raw Valgrind findings are separately root-caused and have a narrowly scoped user-approved exception (2026-10-02); no claim of a warning-free memory run is made.
