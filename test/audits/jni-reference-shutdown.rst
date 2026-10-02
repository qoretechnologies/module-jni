JNI reference, shutdown and documentation audit
===============================================

Copyright 2026 Qore Technologies, s.r.o.

Scope: native ownership/exception fixes, cleanup Java classes, regression fixtures, documentation and CMake documentation/test rules. Excludes the RPM draft and unrelated .claude content. All 62 checks considered. Evidence: qore-packaging/evidence/jni-native-final-qualification-20261002.json and the linked raw logs. There are no new modules, QPP classes, actions, provider fields, factory registrations or dependency JARs; provider/other Java changes are comment-only.

.. list-table:: Complete audit-changes checklist
   :header-rows: 1

   * - Check
     - Status
     - Evidence

   * - 1. Entry exists in doxygen/lang/120_modules.dox.tmpl (for modules in the Qore repo; N/A for external module repos)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 2. Entry exists in doxygen/lang/900_release_notes.dox.tmpl (for modules in the Qore repo; external modules have release notes in their .qm)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 3. qore_user_module() or qore_external_user_module() call in CMakeLists.txt
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 4. Module added to QMOD list in CMakeLists.txt
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 5. .qm file has @section <lowercasemodname>intro as first doc section — must be all lowercase (e.g., avrodataproviderintro, not AvroDataProviderintro)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 6. %modern in .qm file — no redundant %new-style, %require-types, %strict-args, %enable-all-warnings
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 7. No parse directives (%requires, %modern, %new-style) in separated .qc files (check OUTSIDE of @code blocks only)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 8. No %include usage (deprecated for modules)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 9. Copyright 2026 on all new files
     - Pass
     - All new files and changed native headers carry 2026 copyright; third-party text is unchanged.

   * - 10. Directory layout: .qm inside qlib/<ModuleName>/ directory (not at qlib/<ModuleName>.qm for multi-file modules)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 11. No second .qm for the same module at qlib/<ModuleName>.qm
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 12. ns=Qore::XX matches the QoreNamespace constructor path
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 13. %modern directive present
     - Pass
     - Both new Qore fixtures and the modified main test use %modern.

   * - 14. Executable permission set (chmod +x)
     - Pass
     - Qore fixtures, qtests and Python test scripts are executable.

   * - 15. Uses %prepend-module-path  before %requires for in-repo modules (Qore and Qore modules only; not Qorus)
     - Pass
     - JNI tests prepend ../build; qualification explicitly loads the selected Release/Debug native module.

   * - 16. External module dependencies use %try-module — except modules delivered with the project itself (Qore ex: DataProvider, ConnectionProvider, QUnit, etc.) which use hard %requires
     - Pass
     - Optional XML/Python dependencies in the existing full suite retain %try-module; local JNI and the core QUnit framework remain mandatory.

   * - 17. No filesystem operations (fopen, open, creat, unlink, remove, rename, mkdir, rmdir, stat, chmod) without sandbox checks
     - Pass
     - No filesystem operations introduced in production C++.

   * - 18. No network operations (connect, bind, socket, getaddrinfo, gethostbyname) without sandbox checks
     - Pass
     - No network operations introduced in production C++.

   * - 19. If filesystem/network ops exist, verify QoreSandboxManagerHelper usage
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 20. No File::, Dir::, Socket::, HTTPClient:: usage without justification
     - Pass
     - Qore provider edits are documentation only; tests add callback/Program operations, no filesystem/network APIs.

   * - 21. All for/while loops that could iterate >100 times have qore_check_cancel() checks
     - Pass
     - The new O(n) shutdown drain is mandatory resource cleanup and deliberately cannot be cancelled; documented in Globals.cpp.

   * - 22. Uses qore_check_cancel() (NOT deprecated qore_check_io_interrupt())
     - Pass
     - No deprecated interruption API introduced.

   * - 23. Check frequency: every 100 iterations for tight loops, every 10 for expensive iterations
     - Pass
     - No cancellable work loop introduced; mandatory cleanup must drain every handle.

   * - 24. No blocking operations without cancellation support
     - Pass
     - The existing queue wait is deterministically released by the sentinel; shutdown joins the worker before draining the snapshot. Test races use latches and joins without sleeps.

   * - 25. Every action has display_name, short_desc (plain text, <80 chars), desc (markdown)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 26. Every action has options populated via getActionOptionFromFields() — without this, the action shows an empty, unusable form
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 27. Every action has output_type set to a typed data type constant (e.g., MyResponseDataType) — not omitted
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 28. DPAT_API actions: provider has "supports_request": True and implements doRequestImpl()
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 29. DPAT_FIND actions: every option exists in SearchOptions, getRecordTypeImpl() returns *hash<string, AbstractDataField>
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 30. Scheme-based apps (with "scheme" in registerApp): actions use "path" and do NOT use "cls" — having both scheme and cls causes a runtime error
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 31. Single-key hash slices use trailing comma: Fields{"key",} (without trailing comma, Fields{"key"} returns the value, not a hash)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 32. Typed data type classes exist for request and response types — inherit HashDataType, have const Fields hash, call addQoreFields(Fields) in constructor, export public constant at bottom (e.g., public const MyDataType = new MyDataType();)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 33. Request/input types use public Fields (enables ClassName::Fields in action registration)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 34. Response/output types use private Fields
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 35. Each field in data types has display_name, type, and desc (markdown-formatted)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 36. Input fields have example_value where useful (string fields, endpoint URIs, SQL queries, etc.)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 37. Fields with finite allowed values use allowed_values with AllowedValueInfo containing both value and display_name (Title Case, human-readable) — never bare values, never described only in text
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 38. Password/secret fields have "sensitive": True
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 39. groups uses AppGroup enum values from qlib/DataProvider/AppGroup.qc
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 40. App logo stored as separate file, loaded at module level in Priv namespace
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 41. App desc uses markdown: bullet list of capabilities, links to project website, business-language explanation of value
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 42. display_name is user-friendly ("Apache Avro" not "avro")
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 43. short_desc is plain text, under 80 chars, single sentence — no markdown
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 44. desc uses markdown: backticks for code/field refs ( field_name ,  True ,  pdf ), \n\n for paragraphs, -  bullet lists for enumerations, bold for caveats
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 45. Descriptions use plain business language relating to common challenges — not just technical "what" but "why" and "when to use"
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 46. No bare True/False/NOTHING — must be backtick-wrapped in desc
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 47. No bare field/option names in prose — must use backticks
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 48. Long descriptions (>500 chars) use bold section headers and bullet lists
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 49. Factory registration in Qore repo: every factory name registered in qlib/DataProvider/DataProvider.qc → FactoryMap (without this, module loads but doesn't appear in Qorus apps)
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 50. getRecordTypeImpl() signature: must be private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) — NOT returning *AbstractDataProviderType
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 51. Dependency JARs committed (for JNI modules): JAR files in qlib/*/jar/ may be gitignored — use git add -f to ensure they're tracked, otherwise CI compilation fails
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 52. JAR install rules in CMakeLists.txt for all dependency JARs
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 53. No workarounds: No TODOs, FIXMEs, stubs, or partially-implemented features
     - Pass
     - Root causes fixed directly: ownership, checked JNI calls, Program dependency references, complete shutdown drain, destructor exception routing and callback result release. No behavior-reducing workaround or broad suppression.

   * - 54. Exception safety: C++ uses ReferenceHolder for Qore allocations, std::unique_ptr for C++ allocations, *xsink checked after every fallible operation
     - Pass
     - Borrowed/local/global references distinguished; UTF buffers and converted Qore values use RAII; explicit dispatcher destruction owns its pointer with unique_ptr. Java-call failures are checked and original exceptions preserved.

   * - 55. Thread safety: All mutable shared state protected by std::lock_guard<std::mutex> or documented as immutable-after-construction
     - Pass
     - Registration and shutdown share the class monitor; each Ref serializes publication/claim, with volatile closure state. Explicit invocation destruction waits for invokers, then uses the same atomic ownership claim. Barrier-based races cover concurrent claims and registration.

   * - 56. Type safety: Strongly-typed code<return(args)> instead of untyped code; static_cast instead of C casts; typed hashdecls for results; enums where appropriate
     - Pass
     - JNI handles and result holders retain exact types. Tests use typed strings, integers and ExceptionInfo; reflected callback parameters use dynamic auto because Java supplies runtime method metadata.

   * - 57. Performance: No O(n²) where O(n) is possible; no unnecessary copies; coordinate descent uses incremental residuals not full matrix multiply
     - Pass
     - Shutdown takes one linear snapshot and one linear drain. No repeated global scan; global references are created once per borrowed class.

   * - 58. Error handling: All inputs validated (dimensions, empty data, unfitted models); C++ I/O handles EAGAIN/EINTR if applicable
     - Pass
     - Tests cover zero/negative array lengths, nullable fields, failed argument conversion, closed Programs, repeated destruction, late pointer publication, Java-live wrappers, empty shutdown and throwing destructors.

   * - 59. Documentation: Doxygen @param, @return, @throw on all public methods; @par Example with realistic business scenarios; @note for important caveats
     - Pass
     - README and 2.7 release notes document ownership, shutdown semantics, checked-JNI and Valgrind setup. Strict Doxygen plus five output checks pass; all provider/Java changes outside cleanup classes are comment-only.

   * - 60. QPP flags: [flags=CONSTANT] on methods that never throw; [flags=RET_VALUE_ONLY] on methods that throw but have no side effects
     - N/A
     - No new module/QPP declaration, provider runtime behavior, registration, dependency JAR or corresponding production feature in this diff.

   * - 61. Security: No user-controlled format strings; no buffer overflows; bounds checking on array indices; no credentials in code
     - Pass
     - No user-controlled format strings, credentials or new privileged operations. JNI lifetime/array bounds checks retained.

   * - 62. Correctness: Algorithms verified against reference implementations; edge cases tested (empty data, single sample, all-zero features)
     - Pass
     - Release and Debug each pass 12 CTests and 1447 Qore assertions (797 reference, 206 exception-location, 444 main). Final Valgrind runs pass 1147 Qore assertions plus the retained-wrapper subprocess; all contexts map to already approved external causes and no native loss/invalid access remains.
