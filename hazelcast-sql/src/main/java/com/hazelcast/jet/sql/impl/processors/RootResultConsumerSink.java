/*
 * Copyright 2021 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl.processors;

import com.hazelcast.cluster.Address;
import com.hazelcast.jet.core.Inbox;
import com.hazelcast.jet.core.Outbox;
import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.core.ProcessorMetaSupplier;
import com.hazelcast.jet.core.ProcessorSupplier;
import com.hazelcast.jet.core.Watermark;
import com.hazelcast.jet.sql.impl.JetQueryResultProducer;
import com.hazelcast.jet.sql.impl.JetSqlCoreBackendImpl;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.sql.impl.JetSqlCoreBackend;
import com.hazelcast.sql.impl.QueryException;

import javax.annotation.Nonnull;
import java.util.concurrent.CancellationException;

import static com.hazelcast.jet.core.ProcessorMetaSupplier.forceTotalParallelismOne;
import static com.hazelcast.jet.impl.util.Util.getNodeEngine;
import static com.hazelcast.sql.impl.SqlErrorCode.CANCELLED_BY_USER;

public final class RootResultConsumerSink implements Processor {

    private JetQueryResultProducer rootResultConsumer;

    private RootResultConsumerSink() {
    }

    @Override
    public void init(@Nonnull Outbox outbox, @Nonnull Context context) {
        NodeEngineImpl nodeEngine = getNodeEngine(context.hazelcastInstance());
        JetSqlCoreBackendImpl jetSqlCoreBackend = nodeEngine.getService(JetSqlCoreBackend.SERVICE_NAME);
        rootResultConsumer = jetSqlCoreBackend.getResultConsumerRegistry().remove(context.jobId());
        assert rootResultConsumer != null;
    }

    @Override
    public boolean tryProcess() {
        try {
            rootResultConsumer.ensureNotDone();
        } catch (QueryException e) {
            if (e.getCode() == CANCELLED_BY_USER) {
                throw new CancellationException();
            }
            throw e;
        }
        return true;
    }

    @Override
    public void process(int ordinal, @Nonnull Inbox inbox) {
        try {
            rootResultConsumer.consume(inbox);
        } catch (QueryException e) {
            if (e.getCode() == CANCELLED_BY_USER) {
                throw new CancellationException();
            }
            throw e;
        }
    }

    @Override
    public boolean complete() {
        rootResultConsumer.done();
        return true;
    }

    @Override
    public boolean tryProcessWatermark(@Nonnull Watermark watermark) {
        return true;
    }

    public static ProcessorMetaSupplier rootResultConsumerSink(Address initiatorAddress) {
        ProcessorSupplier pSupplier = ProcessorSupplier.of(() -> new RootResultConsumerSink());
        return forceTotalParallelismOne(pSupplier, initiatorAddress);
    }
}
