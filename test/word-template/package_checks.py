"""Prepare DOCX variants and verify rendered package contents with the Python standard library."""
from pathlib import Path
import sys
from xml.etree import ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
MAIN = "word/document.xml"


def prepare(source, directory):
    # Each variant changes just one declaration in the approved letter.
    changes = {
        "control": (MAIN, b"<w:text/>", b"<w:date/>"),
        "macro": ("[Content_Types].xml", b"openxmlformats-officedocument.wordprocessingml.document.main+xml",
                  b"ms-word.document.macroEnabled.main+xml"),
        "external": ("word/_rels/document.xml.rels", b"<Relationship ",
                     b'<Relationship TargetMode="External" '),
        "empty": (MAIN, b"<w:body>", b"<w:other>"),
    }
    for label, (part, before, after) in changes.items():
        with ZipFile(source) as original, ZipFile(directory / (label + ".docx"), "w", ZIP_DEFLATED) as out:
            for name in original.namelist():
                data = original.read(name)
                if name == part:
                    assert before in data
                    data = data.replace(before, after, 1)
                out.writestr(name, data)


def verify(source, directory):
    with ZipFile(source) as original, ZipFile(directory / "out.docx") as result:
        assert set(result.namelist()) == set(original.namelist())
        for name in original.namelist():
            if name != MAIN:
                assert original.read(name) == result.read(name), name
        required = {"word/header1.xml", "word/footer1.xml", "word/styles.xml", "word/media/image1.png"}
        assert required.issubset(result.namelist())
        doc = ET.fromstring(result.read(MAIN))
        para = doc.find(f"{W}body/{W}p")
        assert [text.text for text in para.iter(W + "t")] == ["Shipment for ", " A&B <Ltd> "]
        assert para.find(f"{W}pPr/{W}pStyle").get(W + "val") == "ShipmentLetter"
        assert para.findall(W + "r")[1].find(f"{W}rPr/{W}b") is not None
        table = doc.find(f"{W}body/{W}tbl")
        rows = [[cell.find(f"{W}p/{W}r/{W}t").text for cell in row.findall(W + "tc")]
                for row in table.findall(W + "tr")]
        assert rows == [["Item", "Quantity"], ["Tea", "2"], ["Rice", "3"]], rows
        assert doc.find(".//" + W + "sdt") is None
    with ZipFile(directory / "zero.docx") as zero:
        assert len(ET.fromstring(zero.read(MAIN)).findall(f".//{W}tr")) == 1


if __name__ == "__main__":
    {"prepare": prepare, "verify": verify}[sys.argv[1]](Path(sys.argv[2]), Path(sys.argv[3]))
