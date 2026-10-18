/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.storage.s3;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.codelibs.fess.exception.StorageException;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

public class S3StorageClientTest {

    /**
     * The no-argument constructor is how LastaDi builds the prototype component, and it opens no
     * connection: init() does that. Nothing below reaches the network for that reason.
     */
    @Test
    public void test_closeBeforeInit() {
        assertDoesNotThrow(() -> new S3StorageClient().close());
    }

    /** The SDK builds a synchronous client offline, which is what the shaded jar has to allow. */
    @Test
    public void test_buildWithCustomEndpoint() {
        assertDoesNotThrow(() -> {
            try (S3StorageClient client = new S3StorageClient("http://localhost:9000", "accessKey", "secretKey", "fess", "us-east-1")) {
                // built, not used: any request would need a server
            }
        });
    }

    /** Single-object operations report a failure as StorageException rather than the SDK's own. */
    @Test
    public void test_failuresBecomeStorageException() {
        try (S3StorageClient client = new S3StorageClient()) {
            final InputStream in = new ByteArrayInputStream(new byte[0]);
            assertThrows(StorageException.class, () -> client.uploadObject("test.txt", in, 0, "text/plain"));
            assertThrows(StorageException.class, () -> client.downloadObject("test.txt", new ByteArrayOutputStream()));
            assertThrows(StorageException.class, () -> client.deleteObject("test.txt"));
            assertThrows(StorageException.class, () -> client.getObjectTags("test.txt"));
            assertThrows(StorageException.class, () -> client.setObjectTags("test.txt", Map.of("key", "value")));
        }
    }

    /** A failed listing must not read as an empty bucket: the caller has to be able to tell the two apart. */
    @Test
    public void test_listObjectsFailureBecomesStorageException() {
        try (S3StorageClient client = new S3StorageClient()) {
            final StorageException e = assertThrows(StorageException.class, () -> client.listObjects("prefix", 100));
            assertTrue(e.getMessage().contains("prefix"), e.getMessage());
            assertNotNull(e.getCause());
        }
    }

    /** A bucket that does not exist is a failure too, and the message names the bucket rather than the listing. */
    @Test
    public void test_listObjectsMissingBucketBecomesStorageException() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            final byte[] body = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NoSuchBucket</Code>"
                    + "<Message>The specified bucket does not exist</Message><BucketName>fess</BucketName></Error>")
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            final String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            try (S3StorageClient client = new S3StorageClient(endpoint, "accessKey", "secretKey", "fess", "us-east-1")) {
                final StorageException e = assertThrows(StorageException.class, () -> client.listObjects(null, 100));
                assertEquals("Bucket does not exist: fess", e.getMessage());
                assertTrue(e.getCause() instanceof NoSuchBucketException, String.valueOf(e.getCause()));
            }
        } finally {
            server.stop(0);
        }
    }

    /** The two probes swallow failures instead, because the admin UI calls them to render. */
    @Test
    public void test_probesSwallowFailures() {
        try (S3StorageClient client = new S3StorageClient()) {
            assertFalse(client.isAvailable());
            assertDoesNotThrow(client::ensureBucketExists);
        }
    }
}
