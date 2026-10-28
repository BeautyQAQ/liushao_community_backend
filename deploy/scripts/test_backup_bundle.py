#!/usr/bin/env python3
"""Exercise backup bundle validation without Docker or deployment credentials."""
import copy
import gzip
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().with_name("backup.py")
SPEC = importlib.util.spec_from_file_location("liushao_backup_under_test", SCRIPT)
BACKUP = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = BACKUP
SPEC.loader.exec_module(BACKUP)

IMAGE_A = "01234567-89ab-4cde-8f01-23456789abcd.png"
IMAGE_B = "fedcba98-7654-4321-8abc-def012345678.png"
PAYLOAD = b"backup fixture image bytes\x00\xff"
SQL = b"-- Backup verification fixture\nSELECT 1;\n"
HASHED_FILES = ("database.sql.gz", "uploads.tar.gz", "manifest.json")


def digest(data):
    return hashlib.sha256(data).hexdigest()


class VerifyBundleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="liushao-backup-unit-")
        self.addCleanup(self.temporary.cleanup)
        self.bundle = Path(self.temporary.name) / "bundle"
        self.bundle.mkdir(mode=0o700)
        self.manifest = {
            "schemaVersion": 2,
            "id": "20261019T000000Z-unit-test",
            "source": {"project": "liushao-backup-unit"},
            "databases": ["liushao_community"],
            "images": {
                service: "sha256:" + str(index) * 64
                for index, service in enumerate(
                    ("mysql", "redis", "admin-api", "community-api", "admin-web", "community-web"),
                    start=1,
                )
            },
            "uploads": {
                "files": [{"name": IMAGE_A, "size": len(PAYLOAD), "sha256": digest(PAYLOAD)}],
                "databaseRows": [{"name": IMAGE_A, "size": len(PAYLOAD)}],
            },
        }
        (self.bundle / "database.sql.gz").write_bytes(gzip.compress(SQL, mtime=0))
        self.write_archive([(IMAGE_A, PAYLOAD)])
        self.write_manifest()
        self.write_checksums()

    def write_archive(self, members, include_root=True):
        """Write (name, bytes) files or (TarInfo, bytes|None) special members."""
        with tarfile.open(self.bundle / "uploads.tar.gz", "w:gz") as archive:
            if include_root:
                root = tarfile.TarInfo(".")
                root.type = tarfile.DIRTYPE
                root.mode = 0o700
                archive.addfile(root)
            for name_or_info, content in members:
                if isinstance(name_or_info, tarfile.TarInfo):
                    member = name_or_info
                else:
                    member = tarfile.TarInfo(name_or_info)
                    member.size = len(content)
                    member.mode = 0o600
                archive.addfile(member, io.BytesIO(content) if content is not None else None)

    def write_manifest(self):
        (self.bundle / "manifest.json").write_text(
            json.dumps(self.manifest, ensure_ascii=False) + "\n", encoding="utf-8"
        )

    def write_checksums(self):
        (self.bundle / "SHA256SUMS").write_text(
            "".join(f"{digest((self.bundle / name).read_bytes())}  {name}\n" for name in HASHED_FILES),
            encoding="ascii",
        )

    def assert_invalid(self):
        with self.assertRaises((getattr(BACKUP, "BackupError", ValueError), ValueError)):
            BACKUP.verify_bundle(self.bundle)

    def test_valid_bundle_returns_manifest(self):
        self.assertEqual(BACKUP.verify_bundle(self.bundle), self.manifest)

    def test_legacy_two_database_bundle_can_still_be_verified(self):
        self.manifest["schemaVersion"] = 1
        self.manifest["databases"] = ["vueadmin", "liushao_community"]
        self.write_manifest()
        self.write_checksums()
        self.assertEqual(BACKUP.verify_bundle(self.bundle), self.manifest)

    def test_backup_version_must_match_database_layout(self):
        self.manifest["schemaVersion"] = 1
        self.write_manifest()
        self.write_checksums()
        self.assert_invalid()

    def test_legacy_restore_is_rejected_before_creating_resources(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        self.manifest["schemaVersion"] = 1
        self.manifest["databases"] = ["vueadmin", "liushao_community"]
        self.write_manifest()
        self.write_checksums()
        with patch.object(BACKUP, 'clean_environment') as environment:
            with self.assertRaisesRegex(BACKUP.BackupError, 'pre-consolidation deployment'):
                BACKUP.restore_backup(SimpleNamespace(bundle=self.bundle))
            environment.assert_not_called()

    def test_valid_empty_uploads_with_optional_archive_root(self):
        self.manifest["uploads"] = {"files": [], "databaseRows": []}
        self.write_manifest()
        for include_root in (False, True):
            with self.subTest(include_root=include_root):
                self.write_archive([], include_root=include_root)
                self.write_checksums()
                self.assertEqual(BACKUP.verify_bundle(self.bundle), self.manifest)

    def test_unreferenced_legal_image_is_preserved(self):
        extra = b"orphan image fixture"
        self.manifest["uploads"]["files"].append(
            {"name": IMAGE_B, "size": len(extra), "sha256": digest(extra)}
        )
        self.write_archive([(IMAGE_A, PAYLOAD), (IMAGE_B, extra)])
        self.write_manifest()
        self.write_checksums()
        self.assertEqual(BACKUP.verify_bundle(self.bundle), self.manifest)

    def test_changed_payload_is_rejected_by_checksums(self):
        with (self.bundle / "database.sql.gz").open("ab") as stream:
            stream.write(b"changed after backup")
        self.assert_invalid()

    def test_missing_bundle_file_is_rejected(self):
        for name in (*HASHED_FILES, "SHA256SUMS"):
            with self.subTest(name=name):
                path = self.bundle / name
                original = path.read_bytes()
                path.unlink()
                try:
                    self.assert_invalid()
                finally:
                    path.write_bytes(original)

    def test_unexpected_bundle_file_is_rejected(self):
        (self.bundle / "unexpected.txt").write_text("unexpected", encoding="utf-8")
        self.assert_invalid()

    def test_checksum_manifest_rejects_external_or_uncovered_paths(self):
        original = (self.bundle / "SHA256SUMS").read_text(encoding="ascii")
        for path in ("../outside", "/tmp/liushao-outside", "nested/manifest.json", "unknown.bin"):
            with self.subTest(path=path):
                (self.bundle / "SHA256SUMS").write_text(
                    original + f"{'0' * 64}  {path}\n", encoding="ascii"
                )
                self.assert_invalid()

    def test_checksum_manifest_must_cover_every_payload_once(self):
        original = (self.bundle / "SHA256SUMS").read_text(encoding="ascii")
        lines = original.splitlines(keepends=True)
        for damaged in ("".join(lines[:-1]), original + lines[0]):
            with self.subTest(checksums=damaged):
                (self.bundle / "SHA256SUMS").write_text(damaged, encoding="ascii")
                self.assert_invalid()

    def test_checksum_manifest_requires_two_space_separator(self):
        path = self.bundle / "SHA256SUMS"
        path.write_text(path.read_text(encoding="ascii").replace("  ", " "), encoding="ascii")
        self.assert_invalid()

    def test_sql_gzip_crc_is_checked_after_outer_checksum(self):
        path = self.bundle / "database.sql.gz"
        damaged = bytearray(path.read_bytes())
        damaged[-8] ^= 0x01
        path.write_bytes(damaged)
        self.write_checksums()
        self.assert_invalid()

    def test_truncated_sql_gzip_is_rejected(self):
        path = self.bundle / "database.sql.gz"
        path.write_bytes(path.read_bytes()[:-5])
        self.write_checksums()
        self.assert_invalid()

    def test_empty_sql_gzip_is_rejected(self):
        (self.bundle / "database.sql.gz").write_bytes(gzip.compress(b"", mtime=0))
        self.write_checksums()
        self.assert_invalid()

    def test_archive_rejects_path_traversal_absolute_and_nested_names(self):
        for name in ("../escape.png", "/escape.png", f"nested/{IMAGE_B}", f"../{IMAGE_B}"):
            with self.subTest(name=name):
                self.write_archive([(IMAGE_A, PAYLOAD), (name, b"must not be accepted")])
                self.write_checksums()
                self.assert_invalid()
        self.assertFalse((self.bundle.parent / "escape.png").exists())

    def test_archive_rejects_symbolic_and_hard_links(self):
        for member_type in (tarfile.SYMTYPE, tarfile.LNKTYPE):
            with self.subTest(member_type=member_type):
                link = tarfile.TarInfo(IMAGE_B)
                link.type = member_type
                link.linkname = "../outside.png"
                self.write_archive([(IMAGE_A, PAYLOAD), (link, None)])
                self.write_checksums()
                self.assert_invalid()

    def test_archive_rejects_special_files_and_extra_directories(self):
        for member_type in (tarfile.CHRTYPE, tarfile.BLKTYPE, tarfile.FIFOTYPE, tarfile.DIRTYPE):
            with self.subTest(member_type=member_type):
                member = tarfile.TarInfo(IMAGE_B)
                member.type = member_type
                self.write_archive([(IMAGE_A, PAYLOAD), (member, None)])
                self.write_checksums()
                self.assert_invalid()

    def test_archive_rejects_duplicate_file_names(self):
        self.write_archive([(IMAGE_A, PAYLOAD), (IMAGE_A, PAYLOAD)])
        self.write_checksums()
        self.assert_invalid()

    def test_archive_file_must_be_listed_in_manifest(self):
        self.write_archive([(IMAGE_A, PAYLOAD), (IMAGE_B, b"unlisted")])
        self.write_checksums()
        self.assert_invalid()

    def test_manifest_image_must_exist_in_archive(self):
        self.write_archive([])
        self.write_checksums()
        self.assert_invalid()

    def test_manifest_image_digest_must_match_real_bytes(self):
        self.manifest["uploads"]["files"][0]["sha256"] = "0" * 64
        self.write_manifest()
        self.write_checksums()
        self.assert_invalid()

    def test_manifest_image_size_must_match_real_bytes(self):
        self.manifest["uploads"]["files"][0]["size"] += 1
        self.write_manifest()
        self.write_checksums()
        self.assert_invalid()

    def test_database_reference_must_have_archived_image(self):
        self.manifest["uploads"]["databaseRows"].append({"name": IMAGE_B, "size": 1})
        self.write_manifest()
        self.write_checksums()
        self.assert_invalid()

    def test_database_reference_size_must_match_archived_image(self):
        self.manifest["uploads"]["databaseRows"][0]["size"] += 1
        self.write_manifest()
        self.write_checksums()
        self.assert_invalid()

    def test_invalid_manifest_metadata_is_rejected(self):
        valid = copy.deepcopy(self.manifest)
        mutations = (
            ("schemaVersion", 3),
            ("id", ""),
            ("source", {"project": "../production"}),
            ("databases", ["mysql", "liushao_community"]),
            ("images", {}),
        )
        for key, value in mutations:
            with self.subTest(key=key):
                self.manifest = copy.deepcopy(valid)
                self.manifest[key] = value
                self.write_manifest()
                self.write_checksums()
                self.assert_invalid()

    def test_manifest_upload_names_cannot_escape_bundle(self):
        for name in ("../outside.png", f"nested/{IMAGE_A}", "not-a-uuid.png"):
            with self.subTest(name=name):
                self.manifest["uploads"]["files"][0]["name"] = name
                self.write_manifest()
                self.write_checksums()
                self.assert_invalid()


if __name__ == "__main__":
    unittest.main()
