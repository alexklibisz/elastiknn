package org.elasticsearch.plugin.elastiknn;

import org.apache.http.HttpHost;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;


@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE, numClientNodes = 1)
public class ElastiknnJavaClusterIT extends ESIntegTestCase {

    @Before
    public void setup() {
        ensureGreen();
    }

    public void testDummy() {
        assertTrue(true);
    }

    public void testPluginInstalled() {
        this.ensureGreen();
        HttpHost host = ESIntegTestCase.getRestClient().getNodes().get(0).getHost();
        assertTrue(true);
    }

}

