package com.soouya.cloudstack.test;

import com.soouya.cloudstack.cmc.ClusterManagerClientFactory;
import com.soouya.cloudstack.cmc.IClusterManagerClient;
import org.junit.Test;

import java.util.List;

/**
 * Created by xuyuli on 17-5-11.
 */
public class CmcTest {

    @Test
    public void testCmc() {
        IClusterManagerClient client = ClusterManagerClientFactory.createClient();
        List<String> liveServers = client.getLiveServers();
        if (liveServers!=null) {
            for (String liveServer : liveServers) {
                System.out.println(liveServer);
            }
        }
    }
}
