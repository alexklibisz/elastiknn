package com.klibisz.elastiknn.query

import com.klibisz.elastiknn.mapper.ElastiKnnVectorFieldMapper.FieldType
import org.apache.lucene.document.Field

class MyField(name: String, fieldType: FieldType) extends Field(name, fieldType) {}
