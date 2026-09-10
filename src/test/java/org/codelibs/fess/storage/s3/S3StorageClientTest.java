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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import org.codelibs.fess.exception.StorageException;
import org.junit.jupiter.api.Test;

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

    /** Listing and the two probes swallow failures instead, because the admin UI calls them to render. */
    @Test
    public void test_probesSwallowFailures() {
        try (S3StorageClient client = new S3StorageClient()) {
            assertTrue(client.listObjects("prefix", 100).isEmpty());
            assertFalse(client.isAvailable());
            assertDoesNotThrow(client::ensureBucketExists);
        }
    }
}
