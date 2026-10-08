#!/usr/bin/env python3
"""Validate Java provider runtime-JAR inventories and logging policies."""

import argparse
from collections import defaultdict
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile


SLF4J_API_RE = re.compile(r"^slf4j-api-(.+)\.jar$")
SLF4J_NOP_RE = re.compile(r"^slf4j-nop-(.+)\.jar$")
SLF4J_PROVIDER_SERVICE = "META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
SLF4J_LEGACY_BINDER = "org/slf4j/impl/StaticLoggerBinder.class"
PROVIDER_JAR_PATTERNS = (
    re.compile(r"^slf4j-(?:nop|simple|jdk14|reload4j)-"),
    re.compile(r"^logback-classic-"),
    re.compile(r"^log4j-slf4j2-impl-"),
)
BRIDGE_CYCLES = (
    ("log4j-to-slf4j-", "log4j-slf4j2-impl-"),
    ("jcl-over-slf4j-", "slf4j-jcl-"),
    ("jul-to-slf4j-", "slf4j-jdk14-"),
    ("log4j-over-slf4j-", "slf4j-reload4j-"),
)


def fail(errors, message):
    errors.append(message)


def write_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp.{os.getpid()}")
    try:
        temporary.write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def checked_sha256(path, errors):
    try:
        return sha256(path)
    except OSError as error:
        fail(errors, f"cannot hash runtime JAR {path}: {error}")
        return None


def read_profiles(path, errors):
    profiles = defaultdict(lambda: {"policy": None, "jars": []})
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        fail(errors, f"cannot read provider profiles {path}: {error}")
        return profiles
    for number, raw_line in enumerate(lines, 1):
        if not raw_line or raw_line.startswith("#"):
            continue
        fields = raw_line.split("\t")
        if len(fields) != 3:
            fail(errors, f"{path}:{number}: expected module, policy, and runtime JAR")
            continue
        module, policy, runtime_jar = fields
        profile = profiles[module]
        if profile["policy"] not in (None, policy):
            fail(errors, f"{module}: conflicting logging policies")
        profile["policy"] = policy
        if runtime_jar in profile["jars"]:
            fail(errors, f"{module}: duplicate runtime JAR declaration: {runtime_jar}")
        profile["jars"].append(runtime_jar)
    return profiles


def read_checksums(path, errors):
    expected = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        fail(errors, f"cannot read dependency checksums {path}: {error}")
        return expected
    for number, raw_line in enumerate(lines, 1):
        if not raw_line:
            continue
        fields = raw_line.split(None, 1)
        if len(fields) != 2 or not re.fullmatch(r"[0-9a-f]{64}", fields[0]):
            fail(errors, f"{path}:{number}: malformed SHA-256 entry")
            continue
        dependency = fields[1].lstrip("*")
        if dependency in expected:
            fail(errors, f"{path}:{number}: duplicate checksum entry: {dependency}")
            continue
        expected[dependency] = fields[0]
    return expected


def inspect_jar(path, errors):
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            service = None
            if SLF4J_PROVIDER_SERVICE in names:
                service = archive.read(SLF4J_PROVIDER_SERVICE).decode("utf-8", "replace")
                service = tuple(
                    line.split("#", 1)[0].strip()
                    for line in service.splitlines()
                    if line.split("#", 1)[0].strip()
                )
                for provider in service:
                    provider_class = provider.replace(".", "/") + ".class"
                    if provider_class not in names:
                        fail(
                            errors,
                            f"{path}: SLF4J service provider class is missing: {provider_class}",
                        )
            return names, service
    except (OSError, zipfile.BadZipFile) as error:
        fail(errors, f"{path}: invalid JAR: {error}")
        return set(), None


def validate_logging(module, policy, paths, errors):
    names = [path.name for path in paths]
    api_versions = [match.group(1) for name in names if (match := SLF4J_API_RE.match(name))]
    nop_versions = [match.group(1) for name in names if (match := SLF4J_NOP_RE.match(name))]
    provider_jars = [name for name in names if any(pattern.match(name) for pattern in PROVIDER_JAR_PATTERNS)]
    services = []
    binders = []
    for path in paths:
        entries, service = inspect_jar(path, errors)
        if service:
            services.extend((path.name, provider) for provider in service)
        if SLF4J_LEGACY_BINDER in entries:
            binders.append(path.name)

    if policy == "slf4j-nop":
        if len(api_versions) != 1:
            fail(errors, f"{module}: slf4j-nop policy requires exactly one slf4j-api JAR; found {api_versions}")
        if len(nop_versions) != 1:
            fail(errors, f"{module}: slf4j-nop policy requires exactly one slf4j-nop JAR; found {nop_versions}")
        if api_versions and nop_versions and api_versions[0] != nop_versions[0]:
            fail(errors, f"{module}: slf4j-api {api_versions[0]} and slf4j-nop {nop_versions[0]} differ")
        if len(provider_jars) != 1:
            fail(errors, f"{module}: expected one logging provider JAR; found {provider_jars}")
        if api_versions and api_versions[0].startswith("1."):
            if len(binders) != 1:
                fail(errors, f"{module}: SLF4J 1.x requires one StaticLoggerBinder; found {binders}")
            if services:
                fail(errors, f"{module}: SLF4J 1.x must not contain 2.x provider services: {services}")
        elif api_versions:
            if len(services) != 1:
                fail(errors, f"{module}: SLF4J 2.x requires one provider service; found {services}")
            if binders:
                fail(errors, f"{module}: SLF4J 2.x must not contain legacy binders: {binders}")
    elif policy == "none":
        if api_versions or provider_jars or services or binders:
            fail(errors, f"{module}: logging policy is none but SLF4J artifacts were declared")
    else:
        fail(errors, f"{module}: unknown logging policy: {policy}")

    for outbound, inbound in BRIDGE_CYCLES:
        outbound_jars = [name for name in names if name.startswith(outbound)]
        inbound_jars = [name for name in names if name.startswith(inbound)]
        if outbound_jars and inbound_jars:
            fail(errors, f"{module}: logging bridge cycle: {outbound_jars} with {inbound_jars}")


def committed_paths(repo, errors):
    result = subprocess.run(
        ["git", "-C", str(repo), "ls-files", "-z", "--", "qlib/*/jar/*.jar"],
        check=False,
        stdout=subprocess.PIPE,
    )
    if result.returncode:
        fail(errors, f"cannot read committed JAR inventory from {repo}")
        return set()
    return {entry.decode() for entry in result.stdout.split(b"\0") if entry}


def probe_installed_modules(profiles, errors):
    outcomes = []
    environment = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"):
        environment.pop(name, None)
    for module in sorted(profiles):
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", module):
            fail(errors, f"invalid Qore module name in provider profile: {module}")
            continue
        program = (
            "DataProvider::setOptions(DPO_DisableOnDemandInitialization "
            "| DPO_EnableOnDemandActions); "
            f'load_module("{module}");'
        )
        try:
            result = subprocess.run(
                ["qore", "-l", "DataProvider", "-ne", program],
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=environment,
            )
        except OSError as error:
            fail(errors, f"{module}: cannot run fresh-process load: {error}")
            outcomes.append({"module": module, "started": False})
            continue
        outcomes.append({
            "module": module,
            "started": True,
            "return_code": result.returncode,
            "stdout_empty": not result.stdout,
            "stderr_empty": not result.stderr,
        })
        if result.returncode:
            fail(
                errors,
                f"{module}: fresh-process load failed with status {result.returncode}: "
                f"{result.stdout}{result.stderr}",
            )
        elif result.stdout or result.stderr:
            fail(
                errors,
                f"{module}: emitted output during a fresh-process load: "
                f"{result.stdout}{result.stderr}",
            )
    return outcomes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profiles", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--reference-root", type=Path)
    parser.add_argument("--checksums", type=Path)
    inventory = parser.add_mutually_exclusive_group()
    inventory.add_argument("--require-committed", action="store_true")
    inventory.add_argument("--source-archive", action="store_true",
                           help="verify all archive dependency JARs against the checksum inventory")
    parser.add_argument("--probe-installed", action="store_true")
    parser.add_argument("--report", type=Path)
    parser.add_argument("--scope", default="java-provider-profiles")
    args = parser.parse_args()

    if args.source_archive and not args.checksums:
        parser.error("--source-archive requires --checksums")
    require_inventory = args.require_committed or args.source_archive
    errors = []
    profiles = read_profiles(args.profiles, errors)
    if not profiles:
        fail(errors, f"{args.profiles}: no Java provider profiles declared")
    expected_checksums = read_checksums(args.checksums, errors) if args.checksums else {}
    repo = args.root.parent
    if args.source_archive:
        committed = {
            (Path("qlib") / path.relative_to(args.root)).as_posix()
            for path in args.root.glob("*/jar/*.jar")
            if not path.name.startswith("qore-dataprovider-")
        }
    else:
        committed = committed_paths(repo, errors) if args.require_committed else set()
    seen_committed = set()
    referenced_dependency_paths = set()

    for module, profile in sorted(profiles.items()):
        resolved = []
        for runtime_jar in profile["jars"]:
            path = (args.root / module / runtime_jar).resolve()
            try:
                relative = path.relative_to(args.root.resolve())
            except ValueError:
                fail(errors, f"{module}: runtime JAR escapes provider root: {runtime_jar}")
                continue
            if not path.is_file():
                fail(errors, f"{module}: missing runtime JAR: {path}")
                continue
            resolved.append(path)
            relative_text = (Path("qlib") / relative).as_posix()
            if not path.name.startswith("qore-dataprovider-"):
                referenced_dependency_paths.add(relative_text)
                if require_inventory:
                    if relative_text not in committed:
                        fail(errors, f"{module}: dependency JAR is not in the source inventory: {relative_text}")
                    else:
                        seen_committed.add(relative_text)
                    expected = expected_checksums.get(relative_text)
                    actual = checked_sha256(path, errors)
                    if expected is None:
                        fail(errors, f"{module}: dependency JAR has no source checksum: {relative_text}")
                    elif actual is not None and expected != actual:
                        fail(errors, f"{module}: checksum mismatch for {relative_text}")
            if args.reference_root:
                reference = (args.reference_root / module / runtime_jar).resolve()
                if not reference.is_file():
                    fail(errors, f"{module}: reference runtime JAR missing: {reference}")
                else:
                    reference_digest = checked_sha256(reference, errors)
                    staged_digest = checked_sha256(path, errors)
                    if (reference_digest is not None and staged_digest is not None
                            and reference_digest != staged_digest):
                        fail(errors, f"{module}: staged runtime JAR differs from source: {runtime_jar}")
        validate_logging(module, profile["policy"], resolved, errors)

    if require_inventory:
        unreferenced = sorted(committed - seen_committed)
        if unreferenced:
            fail(errors, "source dependency JARs are not declared by a provider: " + ", ".join(unreferenced))
        stale_checksums = sorted(set(expected_checksums) - referenced_dependency_paths)
        missing_checksums = sorted(referenced_dependency_paths - set(expected_checksums))
        if stale_checksums:
            fail(errors, "checksum inventory contains undeclared JARs: " + ", ".join(stale_checksums))
        if missing_checksums:
            fail(errors, "checksum inventory omits declared JARs: " + ", ".join(missing_checksums))

    probes = []
    if args.probe_installed and not errors:
        probes = probe_installed_modules(profiles, errors)

    count = sum(len(profile["jars"]) for profile in profiles.values())
    if args.report:
        try:
            build_info = Path("/etc/qore-test-base-build-info").read_text(encoding="utf-8")
        except OSError:
            build_info = None
        write_report(args.report, {
            "schema_version": 1,
            "complete": not errors,
            "provenance": {
                "repository": "qoretechnologies/module-jni",
                "source_revision": os.environ.get("CI_COMMIT_SHA", "local"),
                "ci_job": os.environ.get("CI_JOB_NAME", "local"),
                "ci_job_url": os.environ.get("CI_JOB_URL"),
                "base_image": os.environ.get("CI_JOB_IMAGE"),
                "base_image_build_info": build_info,
            },
            "qualification": {
                "scope": args.scope,
                "root": str(args.root.resolve()),
                "reference_root": (
                    str(args.reference_root.resolve()) if args.reference_root else None
                ),
                "module_count": len(profiles),
                "runtime_jar_declarations": count,
                "require_committed": args.require_committed,
                "source_archive": args.source_archive,
                "fresh_process_probes": probes,
            },
            "errors": errors,
        })

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
    suffix = " with fresh-process loads" if args.probe_installed else ""
    print(
        f"Java provider profiles passed: {len(profiles)} modules, "
        f"{count} runtime JAR declarations{suffix}"
    )


if __name__ == "__main__":
    main()
