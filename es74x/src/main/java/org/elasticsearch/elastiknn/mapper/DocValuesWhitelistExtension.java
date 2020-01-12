package org.elasticsearch.elastiknn.mapper;

import org.elasticsearch.painless.spi.PainlessExtension;
import org.elasticsearch.painless.spi.Whitelist;
import org.elasticsearch.painless.spi.WhitelistLoader;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScriptContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DocValuesWhitelistExtension implements PainlessExtension {

    private static final Whitelist WHITELIST =
            WhitelistLoader.loadFromResourceFiles(DocValuesWhitelistExtension.class, "whitelist.txt");

    @Override
    public Map<ScriptContext<?>, List<Whitelist>> getContextWhitelists() {
        return Collections.singletonMap(ScoreScript.CONTEXT, Collections.singletonList(WHITELIST));
    }
}
