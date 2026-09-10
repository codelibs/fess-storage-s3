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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.client.CrawlerClientCreator;
import org.codelibs.fess.crawler.container.CrawlerContainer;
import org.codelibs.fess.util.ComponentUtil;

import jakarta.annotation.Resource;

/**
 * Makes {@code s3:} URLs crawlable once this plugin is installed.
 *
 * <p>Fess registers no client for {@code s3:} on its own, and its default
 * {@code crawler.file.protocols} does not list the scheme, so both have to be added here. This runs
 * from the {@code postConstruct} of crawler/client++.xml, in each of the four processes that build
 * the container: the webapp validates crawling paths against the protocol list, and the crawler
 * process is the one that actually needs the client.</p>
 */
public class S3ClientCreator {

    private static final Logger logger = LogManager.getLogger(S3ClientCreator.class);

    /** Protocol of the URLs this plugin handles, without the colon. */
    protected static final String PROTOCOL = "s3";

    /** URL pattern the crawler client is registered for. */
    protected static final String URL_PATTERN = "s3:.*";

    /** Component key of Fess's ProtocolHelper. ComponentUtil keeps its own copy private. */
    protected static final String PROTOCOL_HELPER = "protocolHelper";

    /** The crawler container, which owns crawlerClientCreator and the client component. */
    @Resource
    protected CrawlerContainer crawlerContainer;

    /**
     * Default constructor.
     */
    public S3ClientCreator() {
        // Default constructor
    }

    /**
     * Registers the crawler client and adds the protocol to the file protocol list.
     *
     * @param componentName the name of the crawler client component to register
     */
    public void register(final String componentName) {
        final CrawlerClientCreator creator = crawlerContainer.getComponent("crawlerClientCreator");
        if (creator == null) {
            throw new IllegalStateException("crawlerClientCreator is not available, so " + URL_PATTERN + " cannot be mapped to "
                    + componentName + ". Check that crawler/client.xml is included by app.xml.");
        }
        creator.register(URL_PATTERN, componentName);

        // ProtocolHelper reads crawler.file.protocols in its own postConstruct, and app.xml includes
        // fess.xml before crawler/client.xml, so the helper is already initialized here.
        // addFileProtocol is idempotent, which matters because the configured value may already list
        // the protocol. hasComponent is asked first rather than testing the result for null:
        // ComponentUtil.getProtocolHelper() throws ComponentNotFoundException when the component is
        // absent, so a null test would let a container built without fess.xml fail to initialize
        // over a protocol registration instead of skipping it.
        if (ComponentUtil.hasComponent(PROTOCOL_HELPER)) {
            ComponentUtil.getProtocolHelper().addFileProtocol(PROTOCOL);
            if (logger.isDebugEnabled()) {
                logger.debug("Registered {} as {} and added {}: to the file protocols.", URL_PATTERN, componentName, PROTOCOL);
            }
        } else if (logger.isDebugEnabled()) {
            logger.debug("Registered {} as {}, but protocolHelper is unavailable, so {}: was not added to the file protocols.", URL_PATTERN,
                    componentName, PROTOCOL);
        }
    }
}
