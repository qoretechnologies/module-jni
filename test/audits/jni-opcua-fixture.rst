JNI OPC UA fixture API audit
============================

Copyright 2026 Qore Technologies, s.r.o.

Scope: OPC UA fixture switches to the builder constructor specified by Milo 1.1.4 source, and its shell/Debian fixture compilers enforce -Xlint:deprecation -Werror. Full 62-check audit. Evidence: qore-packaging/results/{fedora,leap,el10}-jni-opcua-current-api-1 and results/jni-opcua-script-20261003.json.

.. list-table:: Complete audit-changes checklist
   :header-rows: 1

   * - Check
     - Status
     - Evidence

   * - 1. Entry exists in doxygen/lang/120_modules.dox.tmpl (for modules in the Qore repo; N/A for external module repos)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 2. Entry exists in doxygen/lang/900_release_notes.dox.tmpl (for modules in the Qore repo; external modules have release notes in their .qm)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 3. qore_user_module() or qore_external_user_module() call in CMakeLists.txt
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 4. Module added to QMOD list in CMakeLists.txt
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 5. .qm file has @section <lowercasemodname>intro as first doc section — must be all lowercase (e.g., avrodataproviderintro, not AvroDataProviderintro)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 6. %modern in .qm file — no redundant %new-style, %require-types, %strict-args, %enable-all-warnings
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 7. No parse directives (%requires, %modern, %new-style) in separated .qc files (check OUTSIDE of @code blocks only)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 8. No %include usage (deprecated for modules)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 9. Copyright 2026 on all new files
     - Pass
     - All modified fixture files and this report carry 2026 copyright.

   * - 10. Directory layout: .qm inside qlib/<ModuleName>/ directory (not at qlib/<ModuleName>.qm for multi-file modules)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 11. No second .qm for the same module at qlib/<ModuleName>.qm
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 12. ns=Qore::XX matches the QoreNamespace constructor path
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 13. %modern directive present
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 14. Executable permission set (chmod +x)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 15. Uses %prepend-module-path  before %requires for in-repo modules (Qore and Qore modules only; not Qorus)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 16. External module dependencies use %try-module — except modules delivered with the project itself (Qore ex: DataProvider, ConnectionProvider, QUnit, etc.) which use hard %requires
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 17. No filesystem operations (fopen, open, creat, unlink, remove, rename, mkdir, rmdir, stat, chmod) without sandbox checks
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 18. No network operations (connect, bind, socket, getaddrinfo, gethostbyname) without sandbox checks
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 19. If filesystem/network ops exist, verify QoreSandboxManagerHelper usage
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 20. No File::, Dir::, Socket::, HTTPClient:: usage without justification
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 21. All for/while loops that could iterate >100 times have qore_check_cancel() checks
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 22. Uses qore_check_cancel() (NOT deprecated qore_check_io_interrupt())
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 23. Check frequency: every 100 iterations for tight loops, every 10 for expensive iterations
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 24. No blocking operations without cancellation support
     - Pass
     - Existing synchronous compiler subprocesses retained; no waits or polling added.

   * - 25. Every action has display_name, short_desc (plain text, <80 chars), desc (markdown)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 26. Every action has options populated via getActionOptionFromFields() — without this, the action shows an empty, unusable form
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 27. Every action has output_type set to a typed data type constant (e.g., MyResponseDataType) — not omitted
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 28. DPAT_API actions: provider has "supports_request": True and implements doRequestImpl()
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 29. DPAT_FIND actions: every option exists in SearchOptions, getRecordTypeImpl() returns *hash<string, AbstractDataField>
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 30. Scheme-based apps (with "scheme" in registerApp): actions use "path" and do NOT use "cls" — having both scheme and cls causes a runtime error
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 31. Single-key hash slices use trailing comma: Fields{"key",} (without trailing comma, Fields{"key"} returns the value, not a hash)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 32. Typed data type classes exist for request and response types — inherit HashDataType, have const Fields hash, call addQoreFields(Fields) in constructor, export public constant at bottom (e.g., public const MyDataType = new MyDataType();)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 33. Request/input types use public Fields (enables ClassName::Fields in action registration)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 34. Response/output types use private Fields
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 35. Each field in data types has display_name, type, and desc (markdown-formatted)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 36. Input fields have example_value where useful (string fields, endpoint URIs, SQL queries, etc.)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 37. Fields with finite allowed values use allowed_values with AllowedValueInfo containing both value and display_name (Title Case, human-readable) — never bare values, never described only in text
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 38. Password/secret fields have "sensitive": True
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 39. groups uses AppGroup enum values from qlib/DataProvider/AppGroup.qc
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 40. App logo stored as separate file, loaded at module level in Priv namespace
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 41. App desc uses markdown: bullet list of capabilities, links to project website, business-language explanation of value
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 42. display_name is user-friendly ("Apache Avro" not "avro")
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 43. short_desc is plain text, under 80 chars, single sentence — no markdown
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 44. desc uses markdown: backticks for code/field refs ( field_name ,  True ,  pdf ), \n\n for paragraphs, -  bullet lists for enumerations, bold for caveats
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 45. Descriptions use plain business language relating to common challenges — not just technical "what" but "why" and "when to use"
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 46. No bare True/False/NOTHING — must be backtick-wrapped in desc
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 47. No bare field/option names in prose — must use backticks
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 48. Long descriptions (>500 chars) use bold section headers and bullet lists
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 49. Factory registration in Qore repo: every factory name registered in qlib/DataProvider/DataProvider.qc → FactoryMap (without this, module loads but doesn't appear in Qorus apps)
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 50. getRecordTypeImpl() signature: must be private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) — NOT returning *AbstractDataProviderType
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 51. Dependency JARs committed (for JNI modules): JAR files in qlib/*/jar/ may be gitignored — use git add -f to ensure they're tracked, otherwise CI compilation fails
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 52. JAR install rules in CMakeLists.txt for all dependency JARs
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 53. No workarounds: No TODOs, FIXMEs, stubs, or partially-implemented features
     - Pass
     - The deprecated factory is a one-line wrapper around the public constructor now used; no warning suppression.

   * - 54. Exception safety: C++ uses ReferenceHolder for Qore allocations, std::unique_ptr for C++ allocations, *xsink checked after every fallible operation
     - Pass
     - Builder construction, chaining and thrown exceptions unchanged; no new native allocation/lifetime.

   * - 55. Thread safety: All mutable shared state protected by std::lock_guard<std::mutex> or documented as immutable-after-construction
     - Pass
     - Fixture thread state unchanged.

   * - 56. Type safety: Strongly-typed code<return(args)> instead of untyped code; static_cast instead of C casts; typed hashdecls for results; enums where appropriate
     - Pass
     - Public nested UaVariableNodeBuilder constructor has the same UaNodeContext argument and returned builder type.

   * - 57. Performance: No O(n²) where O(n) is possible; no unnecessary copies; coordinate descent uses incremental residuals not full matrix multiply
     - Pass
     - Direct constructor replaces the deprecated forwarding factory with identical behavior.

   * - 58. Error handling: All inputs validated (dimensions, empty data, unfitted models); C++ I/O handles EAGAIN/EINTR if applicable
     - Pass
     - OPC UA suites cover read/write, methods, subscriptions/history and error cases; optional external connection is explicitly skipped.

   * - 59. Documentation: Doxygen @param, @return, @throw on all public methods; @par Example with realistic business scenarios; @note for important caveats
     - Pass
     - Audit records upstream deprecation replacement and exact validation scope; no production API/release behavior changed.

   * - 60. QPP flags: [flags=CONSTANT] on methods that never throw; [flags=RET_VALUE_ONLY] on methods that throw but have no side effects
     - N/A
     - No new Qore module, QPP class, provider registration/schema/action, dependency JAR or production C++ operation in this fixture-only correction.

   * - 61. Security: No user-controlled format strings; no buffer overflows; bounds checking on array indices; no credentials in code
     - Pass
     - Existing local server boundaries retained; no credentials or format strings introduced.

   * - 62. Correctness: Algorithms verified against reference implementations; edge cases tested (empty data, single sample, all-zero features)
     - Pass
     - All three targets pass 47 OPC UA cases / 829 assertions, including the embedded server. Both direct strict javac compilation and the actual build-test-server.sh pass warning-free.
