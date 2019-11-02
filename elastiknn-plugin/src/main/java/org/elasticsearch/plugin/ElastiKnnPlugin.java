package org.elasticsearch.plugin;

import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.HashMap;
import java.util.Map;

public class ElastiKnnPlugin extends Plugin implements IngestPlugin {

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        return new HashMap<>(1);
    }

}