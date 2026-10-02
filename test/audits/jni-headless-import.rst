JNI headless import audit
=========================

Copyright 2026 Qore Technologies, s.r.o.

Scope: static graphics-environment JNI call, deterministic headless regression, CMake registration and documentation corrections while merging upstream 96be433. All 62 checks evaluated. Upstream spreadsheet functionality remains the independently committed change, rechecked here with source/AOT suites. Evidence: qore-packaging/evidence/jni-headless-merge-20261002.json.

.. list-table:: Complete audit-changes checklist
   :header-rows: 1

   * - Check
     - Status
     - Evidence

   * - 1. Entry exists in doxygen/lang/120_modules.dox.tmpl (for modules in the Qore repo; N/A for external module repos)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 2. Entry exists in doxygen/lang/900_release_notes.dox.tmpl (for modules in the Qore repo; external modules have release notes in their .qm)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 3. qore_user_module() or qore_external_user_module() call in CMakeLists.txt
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 4. Module added to QMOD list in CMakeLists.txt
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 5. .qm file has @section <lowercasemodname>intro as first doc section — must be all lowercase (e.g., avrodataproviderintro, not AvroDataProviderintro)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 6. %modern in .qm file — no redundant %new-style, %require-types, %strict-args, %enable-all-warnings
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 7. No parse directives (%requires, %modern, %new-style) in separated .qc files (check OUTSIDE of @code blocks only)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 8. No %include usage (deprecated for modules)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 9. Copyright 2026 on all new files
     - Pass
     - New test files carry 2026 copyright; modified native file already carries 2026.

   * - 10. Directory layout: .qm inside qlib/<ModuleName>/ directory (not at qlib/<ModuleName>.qm for multi-file modules)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 11. No second .qm for the same module at qlib/<ModuleName>.qm
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 12. ns=Qore::XX matches the QoreNamespace constructor path
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 13. %modern directive present
     - Pass
     - .qr scripts enable modern syntax automatically.

   * - 14. Executable permission set (chmod +x)
     - Pass
     - Both new fixtures are executable.

   * - 15. Uses %prepend-module-path  before %requires for in-repo modules (Qore and Qore modules only; not Qorus)
     - Pass
     - The subprocess explicitly loads the chosen native module before importing its own JNI API.

   * - 16. External module dependencies use %try-module — except modules delivered with the project itself (Qore ex: DataProvider, ConnectionProvider, QUnit, etc.) which use hard %requires
     - Pass
     - Only this repository JNI module is required.

   * - 17. No filesystem operations (fopen, open, creat, unlink, remove, rename, mkdir, rmdir, stat, chmod) without sandbox checks
     - Pass
     - One static JNI call replaces an instance JNI call; no filesystem operation added.

   * - 18. No network operations (connect, bind, socket, getaddrinfo, gethostbyname) without sandbox checks
     - Pass
     - No network operation added.

   * - 19. If filesystem/network ops exist, verify QoreSandboxManagerHelper usage
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 20. No File::, Dir::, Socket::, HTTPClient:: usage without justification
     - Pass
     - Regression performs class import; no Qore file/network operation added.

   * - 21. All for/while loops that could iterate >100 times have qore_check_cancel() checks
     - Pass
     - No native loop added.

   * - 22. Uses qore_check_cancel() (NOT deprecated qore_check_io_interrupt())
     - Pass
     - No interruption API changed.

   * - 23. Check frequency: every 100 iterations for tight loops, every 10 for expensive iterations
     - Pass
     - No native loop or cancellation policy changed.

   * - 24. No blocking operations without cancellation support
     - Pass
     - Subprocess regression has a 30-second timeout and CTest has a 40-second timeout; no polling/sleep.

   * - 25. Every action has display_name, short_desc (plain text, <80 chars), desc (markdown)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 26. Every action has options populated via getActionOptionFromFields() — without this, the action shows an empty, unusable form
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 27. Every action has output_type set to a typed data type constant (e.g., MyResponseDataType) — not omitted
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 28. DPAT_API actions: provider has "supports_request": True and implements doRequestImpl()
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 29. DPAT_FIND actions: every option exists in SearchOptions, getRecordTypeImpl() returns *hash<string, AbstractDataField>
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 30. Scheme-based apps (with "scheme" in registerApp): actions use "path" and do NOT use "cls" — having both scheme and cls causes a runtime error
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 31. Single-key hash slices use trailing comma: Fields{"key",} (without trailing comma, Fields{"key"} returns the value, not a hash)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 32. Typed data type classes exist for request and response types — inherit HashDataType, have const Fields hash, call addQoreFields(Fields) in constructor, export public constant at bottom (e.g., public const MyDataType = new MyDataType();)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 33. Request/input types use public Fields (enables ClassName::Fields in action registration)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 34. Response/output types use private Fields
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 35. Each field in data types has display_name, type, and desc (markdown-formatted)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 36. Input fields have example_value where useful (string fields, endpoint URIs, SQL queries, etc.)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 37. Fields with finite allowed values use allowed_values with AllowedValueInfo containing both value and display_name (Title Case, human-readable) — never bare values, never described only in text
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 38. Password/secret fields have "sensitive": True
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 39. groups uses AppGroup enum values from qlib/DataProvider/AppGroup.qc
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 40. App logo stored as separate file, loaded at module level in Priv namespace
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 41. App desc uses markdown: bullet list of capabilities, links to project website, business-language explanation of value
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 42. display_name is user-friendly ("Apache Avro" not "avro")
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 43. short_desc is plain text, under 80 chars, single sentence — no markdown
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 44. desc uses markdown: backticks for code/field refs ( field_name ,  True ,  pdf ), \n\n for paragraphs, -  bullet lists for enumerations, bold for caveats
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 45. Descriptions use plain business language relating to common challenges — not just technical "what" but "why" and "when to use"
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 46. No bare True/False/NOTHING — must be backtick-wrapped in desc
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 47. No bare field/option names in prose — must use backticks
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 48. Long descriptions (>500 chars) use bold section headers and bullet lists
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 49. Factory registration in Qore repo: every factory name registered in qlib/DataProvider/DataProvider.qc → FactoryMap (without this, module loads but doesn't appear in Qorus apps)
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 50. getRecordTypeImpl() signature: must be private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) — NOT returning *AbstractDataProviderType
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 51. Dependency JARs committed (for JNI modules): JAR files in qlib/*/jar/ may be gitignored — use git add -f to ensure they're tracked, otherwise CI compilation fails
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 52. JAR install rules in CMakeLists.txt for all dependency JARs
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 53. No workarounds: No TODOs, FIXMEs, stubs, or partially-implemented features
     - Pass
     - Root cause confirmed in Debug GDB and reproduced by a standalone import; static API now matches the existing static method ID.

   * - 54. Exception safety: C++ uses ReferenceHolder for Qore allocations, std::unique_ptr for C++ allocations, *xsink checked after every fallible operation
     - Pass
     - The existing checked Env wrapper raises JavaException on failure; no ownership or allocation changes.

   * - 55. Thread safety: All mutable shared state protected by std::lock_guard<std::mutex> or documented as immutable-after-construction
     - Pass
     - Existing call_once graphics-class initialization is unchanged; no shared state added.

   * - 56. Type safety: Strongly-typed code<return(args)> instead of untyped code; static_cast instead of C casts; typed hashdecls for results; enums where appropriate
     - Pass
     - Static call takes jclass and returns jboolean; no casts or untyped production values introduced.

   * - 57. Performance: No O(n²) where O(n) is possible; no unnecessary copies; coordinate descent uses incremental residuals not full matrix multiply
     - Pass
     - One JNI call replaces one JNI call, with unchanged class-cache behavior.

   * - 58. Error handling: All inputs validated (dimensions, empty data, unfitted models); C++ I/O handles EAGAIN/EINTR if applicable
     - Pass
     - Checked-JNI subprocess verifies successful completion and exact clean stdout/stderr after import. The unpatched regression aborts with exit 134.

   * - 59. Documentation: Doxygen @param, @return, @throw on all public methods; @par Example with realistic business scenarios; @note for important caveats
     - Pass
     - README, release notes and a corrected qualified Excel documentation link pass strict docs plus all five output checks.

   * - 60. QPP flags: [flags=CONSTANT] on methods that never throw; [flags=RET_VALUE_ONLY] on methods that throw but have no side effects
     - N/A
     - No new module, QPP class, provider registration/schema/action, dependency JAR or corresponding production feature in this correction.

   * - 61. Security: No user-controlled format strings; no buffer overflows; bounds checking on array indices; no credentials in code
     - Pass
     - No user-controlled formats, credentials or buffers introduced.

   * - 62. Correctness: Algorithms verified against reference implementations; edge cases tested (empty data, single sample, all-zero features)
     - Pass
     - Release/Debug headless regression passes; 13 Release CTests pass; source/AOT Excel and ODS pass 782 assertions, reference safety passes 797. Headless Valgrind has zero unclassified contexts or native loss; exact prior approved JVM/glibc origins retained.
