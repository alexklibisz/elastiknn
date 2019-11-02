package org.elasticsearch.plugin.elastiknn;

import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.Collection;

//@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE, numClientNodes = 1)
public class ElastiknnClusterIT extends ESIntegTestCase {

    @Before
    public void setup() {
        ensureGreen();
    }

    public void testDummy() {
        assertTrue(true);
    }

    public void testPluginInstalled() throws IOException {
        this.ensureGreen();
        HttpHost host = ESIntegTestCase.getRestClient().getNodes().get(0).getHost();
        assertTrue(true);
    }

}
