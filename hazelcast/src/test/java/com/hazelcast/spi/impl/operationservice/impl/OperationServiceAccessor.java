/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.spi.impl.operationservice.impl;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.util.counters.Counter;

import static com.hazelcast.test.Accessors.getOperationService;

public final class OperationServiceAccessor {

    private OperationServiceAccessor() {
    }

    public static Counter getFailedBackupsCount(HazelcastInstance instance) {
        OperationServiceImpl operationService = getOperationService(instance);
        return operationService.failedBackupsCount;
    }

    public static int getAsyncOperationsCount(HazelcastInstance instance) {
        OperationServiceImpl operationService = getOperationService(instance);
        return operationService.asyncOperations.size();
    }
}
