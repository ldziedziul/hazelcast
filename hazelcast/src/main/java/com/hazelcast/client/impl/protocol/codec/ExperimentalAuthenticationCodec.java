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

package com.hazelcast.client.impl.protocol.codec;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.*;
import com.hazelcast.client.impl.protocol.codec.custom.*;

import javax.annotation.Nullable;

import static com.hazelcast.client.impl.protocol.ClientMessage.*;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.*;

/*
 * This file is auto-generated by the Hazelcast Client Protocol Code Generator.
 * To change this file, edit the templates or the protocol
 * definitions on the https://github.com/hazelcast/hazelcast-client-protocol
 * and regenerate it.
 */

/**
 * Makes an authentication request to the cluster.
 */
@Generated("60f398705164e9047e87b7c5400ed4da")
public final class ExperimentalAuthenticationCodec {
    //hex: 0xFD0100
    public static final int REQUEST_MESSAGE_TYPE = 16580864;
    //hex: 0xFD0101
    public static final int RESPONSE_MESSAGE_TYPE = 16580865;
    private static final int REQUEST_UUID_FIELD_OFFSET = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_SERIALIZATION_VERSION_FIELD_OFFSET = REQUEST_UUID_FIELD_OFFSET + UUID_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE = REQUEST_SERIALIZATION_VERSION_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;
    private static final int RESPONSE_STATUS_FIELD_OFFSET = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;
    private static final int RESPONSE_MEMBER_UUID_FIELD_OFFSET = RESPONSE_STATUS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;
    private static final int RESPONSE_SERIALIZATION_VERSION_FIELD_OFFSET = RESPONSE_MEMBER_UUID_FIELD_OFFSET + UUID_SIZE_IN_BYTES;
    private static final int RESPONSE_PARTITION_COUNT_FIELD_OFFSET = RESPONSE_SERIALIZATION_VERSION_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;
    private static final int RESPONSE_CLUSTER_ID_FIELD_OFFSET = RESPONSE_PARTITION_COUNT_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_FAILOVER_SUPPORTED_FIELD_OFFSET = RESPONSE_CLUSTER_ID_FIELD_OFFSET + UUID_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_FAILOVER_SUPPORTED_FIELD_OFFSET + BOOLEAN_SIZE_IN_BYTES;

    private ExperimentalAuthenticationCodec() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class RequestParameters {

        /**
         * Cluster name that client will connect to.
         */
        public java.lang.String clusterName;

        /**
         * Name of the user for authentication.
         * Used in case Client Identity Config, otherwise it should be passed null.
         */
        public @Nullable java.lang.String username;

        /**
         * Password for the user.
         * Used in case Client Identity Config, otherwise it should be passed null.
         */
        public @Nullable java.lang.String password;

        /**
         * Unique string identifying the connected client uniquely.
         */
        public @Nullable java.util.UUID uuid;

        /**
         * The type of the client. E.g. JAVA, CPP, CSHARP, etc.
         */
        public java.lang.String clientType;

        /**
         * client side supported version to inform server side
         */
        public byte serializationVersion;

        /**
         * The Hazelcast version of the client. (e.g. 3.7.2)
         */
        public java.lang.String clientHazelcastVersion;

        /**
         * the name of the client instance
         */
        public java.lang.String clientName;

        /**
         * User defined labels of the client instance
         */
        public java.util.List<java.lang.String> labels;
    }

    public static ClientMessage encodeRequest(java.lang.String clusterName, @Nullable java.lang.String username, @Nullable java.lang.String password, @Nullable java.util.UUID uuid, java.lang.String clientType, byte serializationVersion, java.lang.String clientHazelcastVersion, java.lang.String clientName, java.util.Collection<java.lang.String> labels) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("Experimental.Authentication");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeUUID(initialFrame.content, REQUEST_UUID_FIELD_OFFSET, uuid);
        encodeByte(initialFrame.content, REQUEST_SERIALIZATION_VERSION_FIELD_OFFSET, serializationVersion);
        clientMessage.add(initialFrame);
        StringCodec.encode(clientMessage, clusterName);
        CodecUtil.encodeNullable(clientMessage, username, StringCodec::encode);
        CodecUtil.encodeNullable(clientMessage, password, StringCodec::encode);
        StringCodec.encode(clientMessage, clientType);
        StringCodec.encode(clientMessage, clientHazelcastVersion);
        StringCodec.encode(clientMessage, clientName);
        ListMultiFrameCodec.encode(clientMessage, labels, StringCodec::encode);
        return clientMessage;
    }

    public static ExperimentalAuthenticationCodec.RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        request.uuid = decodeUUID(initialFrame.content, REQUEST_UUID_FIELD_OFFSET);
        request.serializationVersion = decodeByte(initialFrame.content, REQUEST_SERIALIZATION_VERSION_FIELD_OFFSET);
        request.clusterName = StringCodec.decode(iterator);
        request.username = CodecUtil.decodeNullable(iterator, StringCodec::decode);
        request.password = CodecUtil.decodeNullable(iterator, StringCodec::decode);
        request.clientType = StringCodec.decode(iterator);
        request.clientHazelcastVersion = StringCodec.decode(iterator);
        request.clientName = StringCodec.decode(iterator);
        request.labels = ListMultiFrameCodec.decode(iterator, StringCodec::decode);
        return request;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class ResponseParameters {

        /**
         * A byte that represents the authentication status. It can be AUTHENTICATED(0), CREDENTIALS_FAILED(1),
         * SERIALIZATION_VERSION_MISMATCH(2) or NOT_ALLOWED_IN_CLUSTER(3).
         */
        public byte status;

        /**
         * Address of the Hazelcast member which sends the authentication response.
         */
        public @Nullable com.hazelcast.cluster.Address address;

        /**
         * UUID of the Hazelcast member which sends the authentication response.
         */
        public @Nullable java.util.UUID memberUuid;

        /**
         * client side supported version to inform server side
         */
        public byte serializationVersion;

        /**
         * Version of the Hazelcast member which sends the authentication response.
         */
        public java.lang.String serverHazelcastVersion;

        /**
         * Partition count of the cluster.
         */
        public int partitionCount;

        /**
         * UUID of the cluster that the client authenticated.
         */
        public java.util.UUID clusterId;

        /**
         * Returns true if server supports clients with failover feature.
         */
        public boolean failoverSupported;

        /**
         * Returns the list of TPC ports or null if TPC is disabled.
         */
        public @Nullable java.util.List<java.lang.Integer> tpcPorts;
    }

    public static ClientMessage encodeResponse(byte status, @Nullable com.hazelcast.cluster.Address address, @Nullable java.util.UUID memberUuid, byte serializationVersion, java.lang.String serverHazelcastVersion, int partitionCount, java.util.UUID clusterId, boolean failoverSupported, @Nullable java.util.Collection<java.lang.Integer> tpcPorts) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        encodeByte(initialFrame.content, RESPONSE_STATUS_FIELD_OFFSET, status);
        encodeUUID(initialFrame.content, RESPONSE_MEMBER_UUID_FIELD_OFFSET, memberUuid);
        encodeByte(initialFrame.content, RESPONSE_SERIALIZATION_VERSION_FIELD_OFFSET, serializationVersion);
        encodeInt(initialFrame.content, RESPONSE_PARTITION_COUNT_FIELD_OFFSET, partitionCount);
        encodeUUID(initialFrame.content, RESPONSE_CLUSTER_ID_FIELD_OFFSET, clusterId);
        encodeBoolean(initialFrame.content, RESPONSE_FAILOVER_SUPPORTED_FIELD_OFFSET, failoverSupported);
        clientMessage.add(initialFrame);

        CodecUtil.encodeNullable(clientMessage, address, AddressCodec::encode);
        StringCodec.encode(clientMessage, serverHazelcastVersion);
        CodecUtil.encodeNullable(clientMessage, tpcPorts, ListIntegerCodec::encode);
        return clientMessage;
    }

    public static ExperimentalAuthenticationCodec.ResponseParameters decodeResponse(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        ResponseParameters response = new ResponseParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        response.status = decodeByte(initialFrame.content, RESPONSE_STATUS_FIELD_OFFSET);
        response.memberUuid = decodeUUID(initialFrame.content, RESPONSE_MEMBER_UUID_FIELD_OFFSET);
        response.serializationVersion = decodeByte(initialFrame.content, RESPONSE_SERIALIZATION_VERSION_FIELD_OFFSET);
        response.partitionCount = decodeInt(initialFrame.content, RESPONSE_PARTITION_COUNT_FIELD_OFFSET);
        response.clusterId = decodeUUID(initialFrame.content, RESPONSE_CLUSTER_ID_FIELD_OFFSET);
        response.failoverSupported = decodeBoolean(initialFrame.content, RESPONSE_FAILOVER_SUPPORTED_FIELD_OFFSET);
        response.address = CodecUtil.decodeNullable(iterator, AddressCodec::decode);
        response.serverHazelcastVersion = StringCodec.decode(iterator);
        response.tpcPorts = CodecUtil.decodeNullable(iterator, ListIntegerCodec::decode);
        return response;
    }
}
