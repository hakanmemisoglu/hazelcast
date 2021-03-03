/*
 * Copyright (c) 2008-2021, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.cluster;

import static com.hazelcast.internal.nio.IOUtil.closeResource;
import static com.hazelcast.test.HazelcastTestSupport.assertTrueEventually;
import static com.hazelcast.test.HazelcastTestSupport.smallInstanceConfig;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.hazelcast.config.Config;
import com.hazelcast.config.JavaSerializationFilterConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.instance.impl.HazelcastInstanceFactory;
import com.hazelcast.internal.cluster.impl.MulticastService;
import com.hazelcast.internal.serialization.impl.SerializationConstants;
import com.hazelcast.internal.nio.Packet;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.OverridePropertyRule;
import com.hazelcast.test.annotation.QuickTest;

import example.serialization.TestDeserialized;

/**
 * Tests if deserialization blacklisting works for MulticastService.
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class MulticastDeserializationTest {

    private static final int MULTICAST_PORT = 53535;
    private static final String MULTICAST_GROUP = "224.0.0.219";
    // TTL = 0 : restricted to the same host, won't be output by any interface
    private static final int MULTICAST_TTL = 0;

    @Rule
    public OverridePropertyRule multicastGroupOverride = OverridePropertyRule
            .clear(MulticastService.SYSTEM_PROPERTY_MULTICAST_GROUP);

    @Before
    public void before() {
        cleanup();
    }

    @AfterClass
    public static void cleanup() {
        HazelcastInstanceFactory.terminateAll();
        TestDeserialized.isDeserialized = false;
    }

    /**
     * Given: Multicast is configured.
     * When: DatagramPacket with a correct Packet comes. The Packet references
     * Java serializer and the serialized object is not a Join message.
     * Then: The object from the Packet is not deserialized.
     */
    @Test
    public void test() throws Exception {
        Config config = createConfig(true);
        Hazelcast.newHazelcastInstance(config);

        sendJoinDatagram(new TestDeserialized());
        Thread.sleep(500L);
        assertFalse("Untrusted deserialization is possible", TestDeserialized.isDeserialized);
    }

    @Test
    public void testWithoutFilter() throws Exception {
        Config config = createConfig(false);
        Hazelcast.newHazelcastInstance(config);

        sendJoinDatagram(new TestDeserialized());
        assertTrueEventually(() -> assertTrue("Object was not deserialized", TestDeserialized.isDeserialized));
    }

    private Config createConfig(boolean withFilter) {
        Config config = smallInstanceConfig();
        if (withFilter) {
            JavaSerializationFilterConfig javaSerializationFilterConfig = new JavaSerializationFilterConfig()
                    .setDefaultsDisabled(true);
            javaSerializationFilterConfig.getBlacklist()
                    .addClasses(TestDeserialized.class.getName());
            config.getSerializationConfig()
                    .setJavaSerializationFilterConfig(javaSerializationFilterConfig);
        }
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getTcpIpConfig()
                .setEnabled(false);
        join.getMulticastConfig()
                .setEnabled(true)
                .setMulticastPort(MULTICAST_PORT)
                .setMulticastGroup(MULTICAST_GROUP)
                .setMulticastTimeToLive(MULTICAST_TTL);
        return config;
    }

    private void sendJoinDatagram(Object object) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        try {
            oos.writeObject(object);
        } finally {
            closeResource(oos);
        }
        byte[] data = bos.toByteArray();
        MulticastSocket multicastSocket = null;
        try {
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            // multicastSocket.setInterface(InetAddress.getByName("127.0.0.1"));
            multicastSocket.setTimeToLive(MULTICAST_TTL + 128);
            System.out.println("LOOPBACK MODE: " +  multicastSocket.getLoopbackMode());
            multicastSocket.setLoopbackMode(false);
            System.out.println("LOOPBACK MODE: " + multicastSocket.getLoopbackMode());
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            //multicastSocket.joinGroup(new InetSocketAddress(MULTICAST_GROUP, MULTICAST_PORT), NetworkInterface.getByName("127.0.0.1"));
            multicastSocket.joinGroup(group);
            int msgSize = data.length;

            ByteBuffer bbuf = ByteBuffer.allocate(1 + 4 + msgSize);
            bbuf.put(Packet.VERSION);
            bbuf.putInt(SerializationConstants.JAVA_DEFAULT_TYPE_SERIALIZABLE);
            bbuf.put(data);
            byte[] packetData = bbuf.array();
            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, group, MULTICAST_PORT);
            System.out.println("SENDING PACKET");
            multicastSocket.send(packet);
            System.out.println("SENT PACKET");

            InetAddress interAddr = multicastSocket.getInterface();
            System.out.println("INTERFACE ADDRESS: " + interAddr);

            NetworkInterface nif = multicastSocket.getNetworkInterface();
//
//            System.out.println("NIF: " + nif);
//            System.out.println();
//            System.out.println("NIF ADDRESSES:\n" + Arrays.toString(nif.getInterfaceAddresses().toArray()));

            //multicastSocket.leaveGroup(new InetSocketAddress(MULTICAST_GROUP, MULTICAST_PORT), NetworkInterface.getByName("127.0.0.1"));
            multicastSocket.leaveGroup(group);
        } finally {
            if (multicastSocket != null) {
                multicastSocket.close();
            }
        }
    }
}
