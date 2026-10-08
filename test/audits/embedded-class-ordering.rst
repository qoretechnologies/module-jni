Embedded Java class ordering audit
==================================

Copyright 2026 Qore Technologies, s.r.o.

Scope: deterministic build-time class embedding and its CI regression.
The old generator fails the ordering test; fixed native amd64/arm64 runs pass.
All 3048 real Byte Buddy class payloads remain identical. Corrected Debian
package rebuilds and actual OBS acceptance are tracked separately.

.. list-table:: Complete audit-changes checklist
   :header-rows: 1

   * - Check
     - Status
     - Evidence

   * - 1. Entry exists in doxygen/lang/120_modules.dox.tmpl (for modules in the Qore repo; N/A for external module repos)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 2. Entry exists in doxygen/lang/900_release_notes.dox.tmpl (for modules in the Qore repo; external modules have release notes in their .qm)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 3. qore_user_module() or qore_external_user_module() call in CMakeLists.txt
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 4. Module added to QMOD list in CMakeLists.txt
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 5. .qm file has @section <lowercasemodname>intro as first doc section — must be all lowercase (e.g., avrodataproviderintro, not AvroDataProviderintro)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 6. %modern in .qm file — no redundant %new-style, %require-types, %strict-args, %enable-all-warnings
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 7. No parse directives (%requires, %modern, %new-style) in separated .qc files (check OUTSIDE of @code blocks only)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 8. No %include usage (deprecated for modules)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 9. Copyright 2026 on all new files
     - Pass
     - New Python test and this audit carry 2026 copyright.

   * - 10. Directory layout: .qm inside qlib/<ModuleName>/ directory (not at qlib/<ModuleName>.qm for multi-file modules)
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 11. No second .qm for the same module at qlib/<ModuleName>.qm
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 12. ns=Qore::XX matches the QoreNamespace constructor path
     - N/A
     - No new Qore module or QPP class; the changed Qore file is the existing build-time make-inc script.

   * - 13. %modern directive present
     - N/A
     - The new test is Python and invokes the generator explicitly; no Qore test-module imports change.

   * - 14. Executable permission set (chmod +x)
     - Pass
     - test/test-make-inc.py is executable and invoked explicitly by Python in CTest.

   * - 15. Uses %prepend-module-path  before %requires for in-repo modules (Qore and Qore modules only; not Qorus)
     - N/A
     - The new test is Python and invokes the generator explicitly; no Qore test-module imports change.

   * - 16. External module dependencies use %try-module — except modules delivered with the project itself (Qore ex: DataProvider, ConnectionProvider, QUnit, etc.) which use hard %requires
     - N/A
     - The new test is Python and invokes the generator explicitly; no Qore test-module imports change.

   * - 17. No filesystem operations (fopen, open, creat, unlink, remove, rename, mkdir, rmdir, stat, chmod) without sandbox checks
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 18. No network operations (connect, bind, socket, getaddrinfo, gethostbyname) without sandbox checks
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 19. If filesystem/network ops exist, verify QoreSandboxManagerHelper usage
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 20. No File::, Dir::, Socket::, HTTPClient:: usage without justification
     - Pass
     - The build generator uses existing Qore Dir/stream APIs to read the selected archive and write generated code. Only ordering changes; no new filesystem or network access.

   * - 21. All for/while loops that could iterate >100 times have qore_check_cancel() checks
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 22. Uses qore_check_cancel() (NOT deprecated qore_check_io_interrupt())
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 23. Check frequency: every 100 iterations for tight loops, every 10 for expensive iterations
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 24. No blocking operations without cancellation support
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 25. Every action has display_name, short_desc (plain text, <80 chars), desc (markdown)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 26. Every action has options populated via getActionOptionFromFields() — without this, the action shows an empty, unusable form
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 27. Every action has output_type set to a typed data type constant (e.g., MyResponseDataType) — not omitted
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 28. DPAT_API actions: provider has "supports_request": True and implements doRequestImpl()
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 29. DPAT_FIND actions: every option exists in SearchOptions, getRecordTypeImpl() returns *hash<string, AbstractDataField>
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 30. Scheme-based apps (with "scheme" in registerApp): actions use "path" and do NOT use "cls" — having both scheme and cls causes a runtime error
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 31. Single-key hash slices use trailing comma: Fields{"key",} (without trailing comma, Fields{"key"} returns the value, not a hash)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 32. Typed data type classes exist for request and response types — inherit HashDataType, have const Fields hash, call addQoreFields(Fields) in constructor, export public constant at bottom (e.g., public const MyDataType = new MyDataType();)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 33. Request/input types use public Fields (enables ClassName::Fields in action registration)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 34. Response/output types use private Fields
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 35. Each field in data types has display_name, type, and desc (markdown-formatted)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 36. Input fields have example_value where useful (string fields, endpoint URIs, SQL queries, etc.)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 37. Fields with finite allowed values use allowed_values with AllowedValueInfo containing both value and display_name (Title Case, human-readable) — never bare values, never described only in text
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 38. Password/secret fields have "sensitive": True
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 39. groups uses AppGroup enum values from qlib/DataProvider/AppGroup.qc
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 40. App logo stored as separate file, loaded at module level in Priv namespace
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 41. App desc uses markdown: bullet list of capabilities, links to project website, business-language explanation of value
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 42. display_name is user-friendly ("Apache Avro" not "avro")
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 43. short_desc is plain text, under 80 chars, single sentence — no markdown
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 44. desc uses markdown: backticks for code/field refs ( field_name ,  True ,  pdf ), \n\n for paragraphs, -  bullet lists for enumerations, bold for caveats
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 45. Descriptions use plain business language relating to common challenges — not just technical "what" but "why" and "when to use"
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 46. No bare True/False/NOTHING — must be backtick-wrapped in desc
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 47. No bare field/option names in prose — must use backticks
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 48. Long descriptions (>500 chars) use bold section headers and bullet lists
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 49. Factory registration in Qore repo: every factory name registered in qlib/DataProvider/DataProvider.qc → FactoryMap (without this, module loads but doesn't appear in Qorus apps)
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 50. getRecordTypeImpl() signature: must be private *hash<string, AbstractDataField> getRecordTypeImpl(*hash<auto> search_options) — NOT returning *AbstractDataProviderType
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 51. Dependency JARs committed (for JNI modules): JAR files in qlib/*/jar/ may be gitignored — use git add -f to ensure they're tracked, otherwise CI compilation fails
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 52. JAR install rules in CMakeLists.txt for all dependency JARs
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 53. Every producer publishes exact lightweight app/action inventory before schema materialization; inventory callbacks do not execute dynamic schema, connection, or action code
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 54. Every caught registration/discovery/factory/scheme/publication failure reaches structured qualification state; logs or empty output are never treated as success
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 55. Authoritative index publication requires a current authenticated single-use qualification token, and the revision check plus atomic writer share the catalog lock
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 56. External schemas are normalized once at a versioned boundary; absent/empty/null remain distinct and AllowedValueInfo conversion occurs only in declared choice positions
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 57. Recursive presentation extraction uses only static metadata and declared fallback types; it never invokes dynamic type/default/example/network/connection callbacks
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 58. Existing flat presentation IDs are unchanged; nested IDs cover structured fields, list elements, unions, and nested choices with cycle/DAG bounds
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 59. Every shipped locale has exact root ID/source parity after regeneration; source-language fallback does not qualify a release
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 60. Installed-artifact checks use an empty prefix with source module paths excluded and cover normal/AST/AOT/fresh-process modes as applicable
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 61. JNI provider runtime JARs come from one declarative inventory with exactly one logging policy; committed checksums, staging parity, SLF4J API/provider compatibility, service entries, duplicate providers, and bridge cycles are validated
     - N/A
     - No provider/app registration, field metadata, schema/catalogue/locale logic, Java dependency inventory or installed-module implementation changes.

   * - 62. No workarounds: No TODOs, FIXMEs, stubs, or partially-implemented features
     - Pass
     - Sorts all three directory/class lists that determine embedded data ordering; preserves subdirectory traversal and inner-before-outer precedence.

   * - 63. Exception safety: C++ uses ReferenceHolder for Qore allocations, std::unique_ptr for C++ allocations, *xsink checked after every fallible operation
     - Pass
     - New tests use TemporaryDirectory and context-managed ZIP files; subprocess failures propagate. Existing Qore resource handling unchanged.

   * - 64. Thread safety: All mutable shared state protected by std::lock_guard<std::mutex> or documented as immutable-after-construction
     - Pass
     - Generator state is local to one invocation; tests execute serially and COMMAND is initialized once before test execution.

   * - 65. Type safety: Strongly-typed code<return(args)> instead of untyped code; static_cast instead of C casts; typed hashdecls for results; enums where appropriate
     - Pass
     - Sorting preserves the existing list<string> type; test command paths are separate subprocess arguments.

   * - 66. Performance: No O(n²) where O(n) is possible; no unnecessary copies; coordinate descent uses incremental residuals not full matrix multiply
     - Pass
     - Canonical ordering requires O(n log n) sorting per directory; all 3048 real Byte Buddy classes retain their decompressed content.

   * - 67. Error handling: All inputs validated (dimensions, empty data, unfitted models); C++ I/O handles EAGAIN/EINTR if applicable
     - Pass
     - Tests assert generator success, exact class-map order, class counts and payload bytes, and fail on subprocess timeout/error.

   * - 68. Documentation: Doxygen @param, @return, @throw on all public methods; @par Example with realistic business scenarios; @note for important caveats
     - Pass
     - Generator comment explains filesystem-dependent ordering; regression docstring and comments describe generated-comment normalization. No public API changes.

   * - 69. QPP flags: [flags=CONSTANT] on methods that never throw; [flags=RET_VALUE_ONLY] on methods that throw but have no side effects
     - N/A
     - No C++ implementation or QPP flags change; ordering uses the existing Qore sort builtin.

   * - 70. Security: No user-controlled format strings; no buffer overflows; bounds checking on array indices; no credentials in code
     - Pass
     - Tests operate only in private temporary directories, invoke no shell, and contain no credentials; native generator access policy is unchanged.

   * - 71. Correctness: Algorithms verified against reference implementations; edge cases tested (empty data, single sample, all-zero features)
     - Pass
     - Regression fails on the previous generator and passes after the fix on native amd64/arm64; forward/reverse/shuffled archive order, nested directories, inner-class precedence and empty input are covered. Every real Byte Buddy class is content-identical. CTest registration and both CI selection filters include the regression.
