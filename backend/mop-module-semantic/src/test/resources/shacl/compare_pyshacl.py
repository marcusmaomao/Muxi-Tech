"""Compare a shapes file and a data file with pySHACL. Prints focus nodes as JSON."""

import json
import sys

from pyshacl import validate
from rdflib import Graph
from rdflib.namespace import Namespace

SH = Namespace("http://www.w3.org/ns/shacl#")


def main() -> None:
    shapes = Graph()
    shapes.parse(sys.argv[1], format="turtle")
    data = Graph()
    data.parse(sys.argv[2], format="turtle")
    conforms, results, _text = validate(
        data,
        shacl_graph=shapes,
        inference="none",
        advanced=False,
        meta_shacl=False,
    )
    focus = sorted({str(node) for node in results.objects(None, SH.focusNode)})
    print(json.dumps({"conforms": bool(conforms), "focus": focus}, ensure_ascii=False))


if __name__ == "__main__":
    main()
