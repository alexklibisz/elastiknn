import sys

assert len(sys.argv) == 2, "Usage: <script> <version>"
version = sys.argv[1]

cfg = f"""
title: Elastiknn ({version})
baseurl: /archive/{version}
"""

with open("_config_versioned.yml", "w") as fp:
    fp.write(f"title: Elastiknn ({version})\n")
    if 
    