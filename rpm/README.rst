RPM source preparation
======================

Copyright 2026 Qore Technologies, s.r.o.

The RPM build uses the same pinned Java and Kotlin dependency inventory as
the Debian packages. Third-party runtime JARs retain their original bytes;
matching source archives, POM files and upstream notices travel in a separate
source component. The optional private Kotlin compiler does not replace the
system compiler or alternatives.

Prepare verified vendor inputs using ``debian/prepare-vendor.py`` as described
in ``debian/README.source``. Then create the aggregate source component::

    python3 rpm/prepare-vendor.py --vendor vendor --cache /path/to/cache \
        --manifest /path/to/vendor-sources.json

The resulting manifest pins the generator, input inventory and copyright
catalog. Compare it with ``rpm/vendor-sources.json`` before preparing a release.
The qore-packaging source tool verifies these committed pins and the aggregate
archive checksum. A missing or corrupt aggregate is an error; no network
access is needed during rpmbuild.

``debian/install-notices.py`` accepts ``--runtime-root`` and ``--kotlin-root``
for an RPM staging directory. With no arguments it retains its Debian package
paths. It extracts verbatim upstream notices and provenance without modifying
runtime JARs. Test both preparation and notice installation with::

    python3 -B -W error -m unittest discover -s rpm -v

RPM recipe and cross-distribution installed qualification are in progress.
