#!/bin/bash
set -e
rsync -av --exclude={'data','results','venv'} . annb:~/ann-benchmarks
