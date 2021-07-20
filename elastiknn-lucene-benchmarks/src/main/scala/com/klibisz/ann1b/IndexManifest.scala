package com.klibisz.ann1b

import com.klibisz.elastiknn.api.Mapping

import java.nio.file.Path

final case class IndexManifest(directory: Path, mapping: Mapping)
