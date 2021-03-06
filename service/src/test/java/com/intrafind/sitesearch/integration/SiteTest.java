/*
 * Copyright 2018 IntraFind Software AG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intrafind.sitesearch.integration;

import com.intrafind.sitesearch.SmokeTest;
import com.intrafind.sitesearch.controller.PageController;
import com.intrafind.sitesearch.controller.SiteController;
import com.intrafind.sitesearch.dto.CrawlStatus;
import com.intrafind.sitesearch.dto.FetchedPage;
import com.intrafind.sitesearch.dto.SiteCreation;
import com.intrafind.sitesearch.dto.SiteIndexSummary;
import com.intrafind.sitesearch.dto.SitePage;
import com.intrafind.sitesearch.dto.SiteProfile;
import com.intrafind.sitesearch.dto.SiteProfileUpdate;
import com.intrafind.sitesearch.dto.SitesCrawlStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SiteTest {
    private final static Logger LOG = LoggerFactory.getLogger(SiteTest.class);
    static final UUID ADMIN_SITE_SECRET = UUID.fromString(System.getenv("ADMIN_SITE_SECRET"));
    @Autowired
    private TestRestTemplate caller;

    public static SitePage buildPage() {
        final String url = "https://api.sitesearch.cloud";
        return new SitePage(
                "Cloud Solution",
                "Site Search is IntraFind's on-demand solution for site search.",
                url,
                Arrays.asList("mars", "Venus")
        );
    }

    @Before
    public void init() {
    }

    private SiteCreation createNewSite(SiteProfileUpdate siteProfileCreation) {
        ResponseEntity<SiteCreation> actual = caller.exchange(SiteController.ENDPOINT, HttpMethod.POST, new HttpEntity<>(siteProfileCreation), SiteCreation.class);

        assertEquals(HttpStatus.CREATED, actual.getStatusCode());
        assertNotNull(actual.getBody());
        assertNotNull(actual.getBody().getSiteId());
        assertNotNull(actual.getBody().getSiteSecret());
        assertEquals(SmokeTest.SITES_API + actual.getBody().getSiteId(), actual.getHeaders().get(HttpHeaders.LOCATION).get(0));

        return actual.getBody();
    }

    private FetchedPage createNewPage(UUID siteId, UUID siteSecret) {
        final SitePage simple = buildPage();
        final ResponseEntity<FetchedPage> newlyCreatedPage = caller.exchange(SiteController.ENDPOINT + "/" + siteId + "/pages?siteSecret=" + siteSecret, HttpMethod.PUT, new HttpEntity<>(simple), FetchedPage.class);
        assertEquals(HttpStatus.OK, newlyCreatedPage.getStatusCode());
        assertNotNull(newlyCreatedPage.getBody());
        assertNotNull(newlyCreatedPage.getBody().getBody());
        assertFalse(newlyCreatedPage.getBody().getBody().isEmpty());
        assertNotNull(newlyCreatedPage.getBody().getTitle());
        assertFalse(newlyCreatedPage.getBody().getTitle().isEmpty());
        assertNotNull(newlyCreatedPage.getBody().getUrl());
        assertFalse(newlyCreatedPage.getBody().getUrl().isEmpty());
        assertFalse(newlyCreatedPage.getBody().getSisLabels().isEmpty());
        assertEquals(Arrays.asList("mars", "Venus"), newlyCreatedPage.getBody().getSisLabels());

        return newlyCreatedPage.getBody();
    }

    @Test
    public void fetchAndUpdateCrawlStatus() {
        final ResponseEntity<SitesCrawlStatus> crawlStatus = caller.exchange(SiteController.ENDPOINT + "/crawl/status?serviceSecret=" +
                ADMIN_SITE_SECRET, HttpMethod.GET, HttpEntity.EMPTY, SitesCrawlStatus.class);
        assertEquals(HttpStatus.OK, crawlStatus.getStatusCode());
        int initSize = crawlStatus.getBody().getSites().size();
        assertTrue(1 <= initSize);
        assertNotNull(findSearchSiteCrawlStatus(crawlStatus.getBody()).getSiteId());
        assertTrue(Instant.now().isAfter(Instant.parse(findSearchSiteCrawlStatus(crawlStatus.getBody()).getCrawled())));

        // update crawl status of a specific site
        Instant now = Instant.now();
        final SitesCrawlStatus updatedCrawlStatus = crawlStatus.getBody();
        final CrawlStatus searchSiteCrawlStatus = findSearchSiteCrawlStatus(updatedCrawlStatus);
        assertNotEquals(now.toString(), searchSiteCrawlStatus.getCrawled());
        searchSiteCrawlStatus.setCrawled(now.toString());
        updatedCrawlStatus.getSites().add(searchSiteCrawlStatus);
        final ResponseEntity<SitesCrawlStatus> crawlStatusUpdate = caller.exchange(SiteController.ENDPOINT + "/crawl/status?serviceSecret=" +
                ADMIN_SITE_SECRET, HttpMethod.PUT, new HttpEntity<>(updatedCrawlStatus), SitesCrawlStatus.class);
        assertEquals(HttpStatus.OK, crawlStatus.getStatusCode());
        assertNotNull(findSearchSiteCrawlStatus(crawlStatusUpdate.getBody()).getSiteId());
        assertEquals(now, Instant.parse(findSearchSiteCrawlStatus(crawlStatusUpdate.getBody()).getCrawled()));
        assertTrue(Instant.now().isAfter(Instant.parse(findSearchSiteCrawlStatus(crawlStatusUpdate.getBody()).getCrawled())));

        // verify crawl status of a specific site
        final ResponseEntity<SitesCrawlStatus> crawlStatusUpdated = caller.exchange(SiteController.ENDPOINT + "/crawl/status?serviceSecret=" +
                ADMIN_SITE_SECRET, HttpMethod.GET, HttpEntity.EMPTY, SitesCrawlStatus.class);
        assertEquals(HttpStatus.OK, crawlStatusUpdated.getStatusCode());
        assertEquals(initSize, crawlStatusUpdated.getBody().getSites().size());
        assertNotNull(findSearchSiteCrawlStatus(crawlStatusUpdated.getBody()).getSiteId());
        assertEquals(now, Instant.parse(findSearchSiteCrawlStatus(crawlStatusUpdated.getBody()).getCrawled()));
        assertTrue(Instant.now().isAfter(Instant.parse(findSearchSiteCrawlStatus(crawlStatusUpdated.getBody()).getCrawled())));
    }

    private CrawlStatus findSearchSiteCrawlStatus(SitesCrawlStatus sitesCrawlStatus) {
        return sitesCrawlStatus.getSites().stream().filter(siteStatus -> siteStatus.getSiteId().equals(CrawlerTest.CRAWL_SITE_ID)).findAny().get();
    }

    @Test
    public void createNewSiteWithProfile() {
        final List<SiteProfile.Config> configs = Arrays.asList(
                new SiteProfile.Config(URI.create("https://subdomain.example.com"), SiteProfile.Config.DEFAULT_PAGE_BODY_CSS_SELECTOR, false),
                new SiteProfile.Config(URI.create("https://example.com"), SiteProfile.Config.DEFAULT_PAGE_BODY_CSS_SELECTOR, false)
        );
        final SiteProfileUpdate siteProfileCreation = new SiteProfileUpdate(
                configs,
                CrawlerTest.TEST_EMAIL_ADDRESS
        );
        final SiteCreation createdSiteProfile = createNewSite(siteProfileCreation);

        ResponseEntity<SiteProfile> actual = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() +
                "/profile?siteSecret=" + createdSiteProfile.getSiteSecret(), HttpMethod.GET, HttpEntity.EMPTY, SiteProfile.class);
        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(createdSiteProfile.getSiteId(), actual.getBody().getId());
        assertEquals(createdSiteProfile.getSiteSecret(), actual.getBody().getSecret());
        assertEquals(CrawlerTest.TEST_EMAIL_ADDRESS, actual.getBody().getEmail());
        assertEquals(configs, actual.getBody().getConfigs());

        ResponseEntity<SiteProfile> siteProfileWithAdminSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() +
                "/profile?siteSecret=" + ADMIN_SITE_SECRET, HttpMethod.GET, HttpEntity.EMPTY, SiteProfile.class);
        assertEquals(HttpStatus.OK, siteProfileWithAdminSecret.getStatusCode());

        ResponseEntity<SiteProfile> siteProfileWithInvalidSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() +
                "/profile?siteSecret=" + UUID.randomUUID(), HttpMethod.GET, HttpEntity.EMPTY, SiteProfile.class);
        assertEquals(HttpStatus.NOT_FOUND, siteProfileWithInvalidSecret.getStatusCode());

        // update site profile
        final List<SiteProfile.Config> updateSiteProfileConfigs = new ArrayList<>(configs);
        updateSiteProfileConfigs.add(new SiteProfile.Config(URI.create("https://update.example.com"), SiteProfile.Config.DEFAULT_PAGE_BODY_CSS_SELECTOR, false));
//        configs.add(new SiteProfile.Config(URI.create("https://update.example.com"), SiteProfile.Config.DEFAULT_PAGE_BODY_CSS_SELECTOR, false));

        final SiteProfileUpdate siteProfileUpdate = new SiteProfileUpdate(createdSiteProfile.getSiteSecret(), "update." + CrawlerTest.TEST_EMAIL_ADDRESS, configs);
        final ResponseEntity<SiteProfileUpdate> updatedSite = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + createdSiteProfile.getSiteSecret(),
                HttpMethod.PUT, new HttpEntity<>(siteProfileUpdate), SiteProfileUpdate.class);
        assertEquals(createdSiteProfile.getSiteSecret(), updatedSite.getBody().getSecret());
        assertEquals("update." + CrawlerTest.TEST_EMAIL_ADDRESS, updatedSite.getBody().getEmail());
        assertEquals(configs, updatedSite.getBody().getConfigs());
        assertEquals(configs.size(), updatedSite.getBody().getConfigs().size());

        // assure site profile is impossible with wrong site secret
        final ResponseEntity<SiteProfileUpdate> updatedSiteWithInvalidSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + UUID.randomUUID(),
                HttpMethod.PUT, new HttpEntity<>(siteProfileUpdate), SiteProfileUpdate.class);
        assertEquals(HttpStatus.NOT_FOUND, updatedSiteWithInvalidSecret.getStatusCode());

        final ResponseEntity<SiteProfileUpdate> updatedSiteWithAdminSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + ADMIN_SITE_SECRET,
                HttpMethod.PUT, new HttpEntity<>(siteProfileUpdate), SiteProfileUpdate.class);
        assertEquals(HttpStatus.NOT_FOUND, updatedSiteWithAdminSecret.getStatusCode());

        // update site profile's secret
        final UUID newSiteSecret = UUID.randomUUID();
        final SiteProfileUpdate siteProfileUpdateWithSecret = new SiteProfileUpdate(newSiteSecret, "update." + CrawlerTest.TEST_EMAIL_ADDRESS, updateSiteProfileConfigs);
        final ResponseEntity<SiteProfileUpdate> updatedSiteWithSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + createdSiteProfile.getSiteSecret(),
                HttpMethod.PUT, new HttpEntity<>(siteProfileUpdateWithSecret), SiteProfileUpdate.class);
        assertEquals(newSiteSecret, updatedSiteWithSecret.getBody().getSecret());
        assertEquals("update." + CrawlerTest.TEST_EMAIL_ADDRESS, updatedSiteWithSecret.getBody().getEmail());
        assertEquals(new HashSet<>(updateSiteProfileConfigs), new HashSet<>(updatedSiteWithSecret.getBody().getConfigs()));
        assertEquals(updateSiteProfileConfigs.size(), updatedSiteWithSecret.getBody().getConfigs().size());
        assertEquals(newSiteSecret, updatedSiteWithSecret.getBody().getSecret());

        // fetching profile with update site secret works
        final ResponseEntity<SiteProfileUpdate> fetchSiteProfileWithNewSiteSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + newSiteSecret,
                HttpMethod.GET, new HttpEntity<>(siteProfileUpdateWithSecret), SiteProfileUpdate.class);
        assertEquals(HttpStatus.OK, fetchSiteProfileWithNewSiteSecret.getStatusCode());
        assertEquals(newSiteSecret, fetchSiteProfileWithNewSiteSecret.getBody().getSecret());

        // fetching profile with old site secret does not work
        final ResponseEntity<SiteProfileUpdate> fetchSiteProfileWithOldSiteSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSiteProfile.getSiteId() + "/profile?siteSecret=" + createdSiteProfile.getSiteSecret(),
                HttpMethod.GET, new HttpEntity<>(siteProfileUpdateWithSecret), SiteProfileUpdate.class);
        assertEquals(HttpStatus.NOT_FOUND, fetchSiteProfileWithOldSiteSecret.getStatusCode());
    }

    @Test
    public void updateSiteViaUrl() throws Exception {
        final SiteCreation newSite = createNewSite(null);
        final FetchedPage newPage = createNewPage(newSite.getSiteId(), newSite.getSiteSecret());
        final String updatedBodyContent = "Updated via Hash(siteId, URL)";
        newPage.setBody(updatedBodyContent);

        TimeUnit.MILLISECONDS.sleep(8_000);

        // update
        final ResponseEntity<FetchedPage> updatedSite = caller.exchange(SiteController.ENDPOINT
                        + "/" + newSite.getSiteId() + "/pages?siteSecret=" + newSite.getSiteSecret(),
                HttpMethod.PUT, new HttpEntity<>(newPage), FetchedPage.class);
        assertEquals(HttpStatus.OK, updatedSite.getStatusCode());
        assertEquals(newPage.getId(), updatedSite.getBody().getId());
        assertEquals(updatedBodyContent, updatedSite.getBody().getBody());

        // fetch & check updated site
        assertEquals(SitePage.hashPageId(newPage.getSiteId(), newPage.getUrl()), newPage.getId());
        final ResponseEntity<FetchedPage> fetchedUpdatedSite = caller.exchange(PageController.ENDPOINT
                        + "/" + SitePage.hashPageId(newSite.getSiteId(), newPage.getUrl()),
                HttpMethod.GET, HttpEntity.EMPTY, FetchedPage.class);

        assertEquals(HttpStatus.OK, fetchedUpdatedSite.getStatusCode());
        assertEquals(newPage.getId(), fetchedUpdatedSite.getBody().getId());
        assertEquals(updatedBodyContent, fetchedUpdatedSite.getBody().getBody());

        // delete using an invalid siteSecret
        UUID invalidSiteSecret = UUID.randomUUID();
        final ResponseEntity<ResponseEntity> deletionWithInvalidSiteSecret = caller.exchange(SiteController.ENDPOINT + "/" + newSite.getSiteId() + "/pages?siteSecret=" + invalidSiteSecret + "&url=" + newPage.getUrl(), HttpMethod.DELETE, HttpEntity.EMPTY, ResponseEntity.class);
        assertEquals(HttpStatus.NOT_FOUND, deletionWithInvalidSiteSecret.getStatusCode());
        assertNull(deletionWithInvalidSiteSecret.getBody());

        // fetch via URL
        final ResponseEntity<FetchedPage> fetchViaUrl = caller.exchange(SiteController.ENDPOINT
                        + "/" + newSite.getSiteId() + "/pages?url=" + newPage.getUrl(),
                HttpMethod.GET, HttpEntity.EMPTY, FetchedPage.class);
        assertEquals(HttpStatus.OK, fetchViaUrl.getStatusCode());
        assertEquals(newPage.getId(), fetchViaUrl.getBody().getId());
        assertEquals(updatedBodyContent, fetchViaUrl.getBody().getBody());
        assertEquals(newPage.getUrl(), fetchViaUrl.getBody().getUrl());

        // delete using a valid siteSecret
        final ResponseEntity<ResponseEntity> deletion = caller.exchange(SiteController.ENDPOINT + "/" + newSite.getSiteId() + "/pages?siteSecret=" + newSite.getSiteSecret() + "&url=" + newPage.getUrl(), HttpMethod.DELETE, HttpEntity.EMPTY, ResponseEntity.class);
        assertEquals(HttpStatus.NO_CONTENT, deletion.getStatusCode());
        assertNull(deletion.getBody());

        // fetch via URL an already deleted page
        final ResponseEntity<SitePage> fetchViaUrlForNonExistingPage = caller.exchange(SiteController.ENDPOINT
                        + "/" + newSite.getSiteId() + "/pages?url=" + newPage.getUrl(),
                HttpMethod.GET, HttpEntity.EMPTY, SitePage.class);
        assertEquals(HttpStatus.NOT_FOUND, fetchViaUrlForNonExistingPage.getStatusCode());
    }

    @Test
    public void fetchUpdatedById() throws Exception {
        final SiteCreation newSiteYing = createNewSite(null);
        final FetchedPage ying = createNewPage(newSiteYing.getSiteId(), newSiteYing.getSiteSecret());
        final SiteCreation newSiteYang = createNewSite(null);
        final FetchedPage yang = createNewPage(newSiteYang.getSiteId(), newSiteYang.getSiteSecret());
        TimeUnit.MILLISECONDS.sleep(8_000);

        final ResponseEntity<FetchedPage> actualYing = caller.exchange(SiteController.ENDPOINT + "/"
                + newSiteYing.getSiteId() + "/pages/" + ying.getId() + "?siteSecret=" + newSiteYing.getSiteSecret(), HttpMethod.PUT, new HttpEntity<>(ying), FetchedPage.class);
        assertEquals(HttpStatus.OK, actualYing.getStatusCode());
        assertEquals(ying, actualYing.getBody());
        final ResponseEntity<FetchedPage> actualYang = caller.exchange(SiteController.ENDPOINT + "/"
                + newSiteYang.getSiteId() + "/pages/" + yang.getId() + "?siteSecret=" + newSiteYang.getSiteSecret(), HttpMethod.PUT, new HttpEntity<>(yang), FetchedPage.class);
        assertEquals(HttpStatus.OK, actualYang.getStatusCode());
        assertEquals(yang, actualYang.getBody());

        final ResponseEntity<FetchedPage> actualYingFetched = caller.getForEntity(PageController.ENDPOINT + "/" + ying.getId(), FetchedPage.class);
        assertEquals(HttpStatus.OK, actualYingFetched.getStatusCode());
        assertEquals(ying, actualYingFetched.getBody());

        final ResponseEntity<FetchedPage> actualYangFetched = caller.getForEntity(PageController.ENDPOINT + "/" + yang.getId(), FetchedPage.class);
        assertEquals(HttpStatus.OK, actualYangFetched.getStatusCode());
        assertEquals(yang, actualYangFetched.getBody());

        FetchedPage fetchedYing = actualYingFetched.getBody();
        FetchedPage fetchedYang = actualYangFetched.getBody();
        assertEquals(fetchedYing, fetchedYang);
        assertNotEquals(fetchedYing.getId(), fetchedYang.getId());
        assertNotEquals(fetchedYing.getSiteId(), fetchedYang.getSiteId());
    }

    @Test
    public void updatedSite() throws Exception {
        SiteCreation createdSite = createNewSite(null);
        FetchedPage createdPage = createNewPage(createdSite.getSiteId(), createdSite.getSiteSecret());

        TimeUnit.MILLISECONDS.sleep(8_000);

        final int pageChecksum = 923522;
        final ResponseEntity<SitePage> updateWithSiteIdOnly = caller.exchange(SiteController.ENDPOINT + "/" + createdSite.getSiteId()
                + "/pages/" + createdPage.getId(), HttpMethod.PUT, new HttpEntity<>(createdPage), SitePage.class);
        assertEquals("only valid siteId is provided", HttpStatus.BAD_REQUEST, updateWithSiteIdOnly.getStatusCode());
        assertEquals(pageChecksum, updateWithSiteIdOnly.getBody().hashCode());

        final ResponseEntity<SitePage> updateWithSiteSecretOnly = caller.exchange(SiteController.ENDPOINT + "/" + createdPage.getSiteId()
                + "/pages/" + createdPage.getId(), HttpMethod.PUT, new HttpEntity<>(createdPage), SitePage.class);
        assertEquals("only valid siteSecret is provided", HttpStatus.BAD_REQUEST, updateWithSiteSecretOnly.getStatusCode());
        assertEquals(pageChecksum, updateWithSiteSecretOnly.getBody().hashCode());

        final ResponseEntity<SitePage> updateWithWrongSiteSecret = caller.exchange(SiteController.ENDPOINT + "/" + createdSite.getSiteId()
                        + "/pages/" + createdPage.getId() + "?siteSecret=" + UUID.randomUUID(),
                HttpMethod.PUT, new HttpEntity<>(createdPage), SitePage.class);
        assertEquals("siteSecret is invalid", HttpStatus.NOT_FOUND, updateWithWrongSiteSecret.getStatusCode());
        assertNull(updateWithWrongSiteSecret.getBody());

        createdPage.setTitle("updated title");
        createdPage.setBody("updated body");
        createdPage.setUrl("https://example.com/updated");
        final Instant beforePageUpdate = Instant.now();
        final ResponseEntity<FetchedPage> updated = caller.exchange(SiteController.ENDPOINT + "/" + createdSite.getSiteId()
                        + "/pages/" + createdPage.getId() + "?siteSecret=" + createdSite.getSiteSecret(),
                HttpMethod.PUT, new HttpEntity<>(createdPage), FetchedPage.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(createdPage, updated.getBody());
        assertEquals("updated body", updated.getBody().getBody());
        assertEquals(Arrays.asList("mars", "Venus"), updated.getBody().getSisLabels());
        // assert correct timestamp update
        assertTrue(beforePageUpdate.isBefore(Instant.parse(updated.getBody().getTimestamp())));
        assertTrue(Instant.now().isAfter(Instant.parse(updated.getBody().getTimestamp())));

        final ResponseEntity<SitePage> updateWithInvalidPageId = caller.exchange(SiteController.ENDPOINT + "/" + createdSite.getSiteId()
                        + "/pages/" + "invalidSomething" + "?siteSecret=" + createdSite.getSiteSecret(),
                HttpMethod.PUT, new HttpEntity<>(createdPage), SitePage.class);
        assertEquals(HttpStatus.BAD_REQUEST, updateWithInvalidPageId.getStatusCode());
        assertNull(updateWithInvalidPageId.getBody());
    }

//    @Test
//    public void importFeed() throws Exception {
//        final ResponseEntity<SiteIndexSummary> exchange = caller.exchange(
//                SitesController.ENDPOINT + "/rss?feedUrl=http://www.mvv-muenchen.de/de/aktuelles/fahrplanaenderungen/detail/rss.xml",
//                HttpMethod.POST, HttpEntity.EMPTY, SiteIndexSummary.class);
//        final SiteIndexSummary creation = validateTenantSummary(exchange, 10);
//
//        TimeUnit.MILLISECONDS.sleep(8_000);
//        validateUpdatedSites(creation);
//    }

    @Test
    public void importFeedAndClearSite() throws Exception {
        final ResponseEntity<SiteIndexSummary> exchange = caller.exchange(SiteController.ENDPOINT + "/rss?feedUrl=http://intrafind.de/share/enterprise-search-blog.xml",
                HttpMethod.POST, HttpEntity.EMPTY, SiteIndexSummary.class);
        final SiteIndexSummary creation = validateTenantSummary(exchange, 25);

        TimeUnit.MILLISECONDS.sleep(13_000);
        LOG.info("siteId: " + creation.getSiteId());
        LOG.info("siteSecret: " + creation.getSiteSecret());
        validateUpdatedSites(creation);

        final ResponseEntity<Object> clearSite = caller.exchange(SiteController.ENDPOINT + "/" + creation.getSiteId() + "?siteSecret=" + creation.getSiteSecret(),
                HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
        assertNull(clearSite.getBody());
        assertEquals(HttpStatus.OK, clearSite.getStatusCode());
        TimeUnit.MILLISECONDS.sleep(18_000);
        assureClearedSite(creation);
    }

    private void assureClearedSite(SiteIndexSummary siteIndexSummary) {
        siteIndexSummary.getDocuments().forEach(docId -> {
            final ResponseEntity<FetchedPage> fetchedById = caller.exchange(
                    PageController.ENDPOINT + "/" + docId, HttpMethod.GET, HttpEntity.EMPTY, FetchedPage.class);
            assertEquals(HttpStatus.NOT_FOUND, fetchedById.getStatusCode());
            assertNull(fetchedById.getBody());
        });
    }

    private void validateUpdatedSites(SiteIndexSummary siteIndexSummary) {
        siteIndexSummary.getDocuments().forEach(documentId -> {
            final ResponseEntity<FetchedPage> fetchedById = caller.exchange(
                    PageController.ENDPOINT + "/" + documentId, HttpMethod.GET, HttpEntity.EMPTY, FetchedPage.class);
            assertTrue(HttpStatus.OK.equals(fetchedById.getStatusCode()));
            assertTrue(siteIndexSummary.getSiteId().equals(fetchedById.getBody().getSiteId()));
            assertFalse(fetchedById.getBody().getBody().isEmpty());
            assertNotNull(fetchedById.getBody().getUrl());
        });
    }

    @Test
    public void importFeedStrippingHtml() throws Exception {
        // create index with stripped HTML tags
        final ResponseEntity<SiteIndexSummary> initialIndexCreation = caller.exchange(
                SiteController.ENDPOINT + "/rss?feedUrl=http://intrafind.de/share/enterprise-search-blog.xml&stripHtmlTags=true",
                HttpMethod.POST, HttpEntity.EMPTY, SiteIndexSummary.class);
        TimeUnit.MILLISECONDS.sleep(8_000);
        final SiteIndexSummary siteIndexSummaryCreation = validateTenantSummary(initialIndexCreation, 25);

        UUID siteIdFromCreation = siteIndexSummaryCreation.getSiteId();
        UUID siteSecretFromCreation = siteIndexSummaryCreation.getSiteSecret();

        LOG.info("siteIdFromCreation: " + siteIdFromCreation);
        LOG.info("siteSecretFromCreation: " + siteSecretFromCreation);
    }

    @Test
    public void importFeedAndUpdate() throws Exception {
        // create index
        final ResponseEntity<SiteIndexSummary> initialIndexCreation = caller.exchange(
                SiteController.ENDPOINT + "/rss?feedUrl=https://raw.githubusercontent.com/intrafind/if-sitesearch/master/service/src/test/resources/steem-blockchain-rss-feed-init.xml",
                HttpMethod.POST, HttpEntity.EMPTY, SiteIndexSummary.class);
        TimeUnit.MILLISECONDS.sleep(13_000);
        final SiteIndexSummary siteIndexSummaryCreation = validateTenantSummary(initialIndexCreation, 10);

        UUID siteIdFromCreation = siteIndexSummaryCreation.getSiteId();
        UUID siteSecretFromCreation = siteIndexSummaryCreation.getSiteSecret();

        LOG.info("siteIdFromCreation: " + siteIdFromCreation);
        LOG.info("siteSecretFromCreation: " + siteSecretFromCreation);

        final ResponseEntity<SiteIndexSummary> updateWithoutSecret = caller.exchange(
                SiteController.ENDPOINT + "/" + siteIdFromCreation + "/rss?feedUrl=http://intrafind.de/share/enterprise-search-blog.xml",
                HttpMethod.PUT, HttpEntity.EMPTY, SiteIndexSummary.class);
        assertEquals(HttpStatus.BAD_REQUEST, updateWithoutSecret.getStatusCode());

        final ResponseEntity<SiteIndexSummary> updateWithInvalidSecret = caller.exchange(
                SiteController.ENDPOINT + "/" + siteIdFromCreation + "/rss?feedUrl=http://intrafind.de/share/enterprise-search-blog.xml"
                        + "&siteSecret=" + UUID.randomUUID(),
                HttpMethod.PUT, HttpEntity.EMPTY, SiteIndexSummary.class);
        assertEquals(HttpStatus.BAD_REQUEST, updateWithInvalidSecret.getStatusCode());


        // update index
        final ResponseEntity<SiteIndexSummary> anotherFeedReplacement = caller.exchange(
                SiteController.ENDPOINT + "/" + siteIdFromCreation + "/rss?feedUrl=http://intrafind.de/share/enterprise-search-blog.xml"
                        + "&siteSecret=" + siteSecretFromCreation,
                HttpMethod.PUT, HttpEntity.EMPTY, SiteIndexSummary.class);
        final SiteIndexSummary siteIndexSummaryUpdate = validateTenantSummary(anotherFeedReplacement, 25);

        validateUpdatedSites(siteIndexSummaryUpdate);

        tryDeletionOfSites(siteIdFromCreation, siteSecretFromCreation);
    }

    private void tryDeletionOfSites(UUID siteIdFromCreation, UUID siteSecretFromCreation) {
        final ResponseEntity<List> fetchAll = caller.exchange(SiteController.ENDPOINT + "/" + siteIdFromCreation, HttpMethod.GET, HttpEntity.EMPTY, List.class);
        assertTrue(HttpStatus.OK.equals(fetchAll.getStatusCode()));
        @SuppressWarnings("unchecked")
        List<String> pages = fetchAll.getBody();
        assertTrue(1 < pages.size());
        int siteCountBeforeDeletion = pages.size();

        for (String pageId : pages) {
            LOG.info("pageId: " + pageId);

            // delete using an invalid siteSecret
            UUID invalidSiteSecret = UUID.randomUUID();
            final ResponseEntity<ResponseEntity> deletionWithInvalidSiteSecret = caller.exchange(SiteController.ENDPOINT + "/" + siteIdFromCreation + "/pages/" + pageId + "?siteSecret=" + invalidSiteSecret, HttpMethod.DELETE, HttpEntity.EMPTY, ResponseEntity.class);
            assertEquals(HttpStatus.NOT_FOUND, deletionWithInvalidSiteSecret.getStatusCode());
            assertNull(deletionWithInvalidSiteSecret.getBody());

            // delete using a valid siteSecret
            final ResponseEntity<ResponseEntity> deletion = caller.exchange(SiteController.ENDPOINT + "/" + siteIdFromCreation + "/pages/" + pageId + "?siteSecret=" + siteSecretFromCreation, HttpMethod.DELETE, HttpEntity.EMPTY, ResponseEntity.class);
            assertEquals(HttpStatus.NO_CONTENT, deletion.getStatusCode());
            assertNull(deletion.getBody());
        }

//        pages.stream().forEach(pageId -> {
//            LOG.info("pageId: " + pageId);
//            final ResponseEntity<ResponseEntity> deletion = caller.exchange(SitesController.ENDPOINT + "/" + siteIdFromCreation+"/pages/"+pageId +"?siteSecret="+siteSecretFromCreation, HttpMethod.DELETE, HttpEntity.EMPTY, ResponseEntity.class);
//            assertEquals(HttpStatus.NO_CONTENT, deletion.getStatusCode());
//            assertNull(deletion.getBody());
//        });
        LOG.info("siteCountBeforeDeletion: " + siteCountBeforeDeletion);
    }

    private SiteIndexSummary validateTenantSummary(ResponseEntity<SiteIndexSummary> anotherFeedReplacement, int indexEntriesCount) {
        assertEquals(HttpStatus.OK, anotherFeedReplacement.getStatusCode());
        final SiteIndexSummary siteIndexSummaryUpdate = anotherFeedReplacement.getBody();
        assertTrue(siteIndexSummaryUpdate.getSiteId() != null);
        assertTrue(siteIndexSummaryUpdate.getSiteSecret() != null);
        assertEquals(indexEntriesCount, siteIndexSummaryUpdate.getSuccessCount());
        assertEquals(indexEntriesCount, siteIndexSummaryUpdate.getDocuments().size());
        assertTrue(siteIndexSummaryUpdate.getFailed().isEmpty());
        return siteIndexSummaryUpdate;
    }

    @Test
    public void indexIntrafindDe() throws Exception {
        final SiteCreation newSite = createNewSite(null);
        List<String> enIndexDocuments = new ArrayList<>();
        enIndexDocuments.add("en/2b4c27b0-6636-4a13-a911-4f495f99b604.xml");
        enIndexDocuments.add("en/32d2557e-7f03-48d9-ad60-bf7c0b70c487.xml");
        enIndexDocuments.add("en/534706ba-da98-4b45-b920-8ec0486d79fb.xml");
        enIndexDocuments.add("en/79f4cd25-39d1-42ad-8b2a-9247aabd7d13.xml");

        // create index without clearance
        SiteIndexSummary siteIndexSummary = indexCrawlerPage(enIndexDocuments.get(0),
                newSite.getSiteId(),
                newSite.getSiteSecret(), false
        );

        validateUpdatedSites(siteIndexSummary);
        TimeUnit.MILLISECONDS.sleep(13_000);

        final ResponseEntity<List> allPages = caller.exchange(SiteController.ENDPOINT + "/" + newSite.getSiteId(),
                HttpMethod.GET, HttpEntity.EMPTY, List.class);
        @SuppressWarnings("unchecked")
        List<String> pageIds = allPages.getBody();
        assertEquals(siteIndexSummary.getDocuments().size(), pageIds.size());

        // update index without clearance
        SiteIndexSummary siteIndexSummaryAfterUpdate = indexCrawlerPage(enIndexDocuments.get(1),
                newSite.getSiteId(),
                newSite.getSiteSecret(), false
        );
        validateUpdatedSites(siteIndexSummaryAfterUpdate);
        TimeUnit.MILLISECONDS.sleep(13_000);

        final ResponseEntity<List> allPagesAfterUpdate = caller.exchange(SiteController.ENDPOINT + "/" + newSite.getSiteId(),
                HttpMethod.GET, HttpEntity.EMPTY, List.class);
        @SuppressWarnings("unchecked")
        List<String> allPageIdsAfterUpdate = allPagesAfterUpdate.getBody();
        assertEquals(siteIndexSummary.getDocuments().size() + siteIndexSummaryAfterUpdate.getDocuments().size(), allPageIdsAfterUpdate.size());

        // create index with clearance
        SiteIndexSummary siteIndexSummaryAfterClearance = indexCrawlerPage(enIndexDocuments.get(2),
                newSite.getSiteId(),
                newSite.getSiteSecret(), true
        );
        validateUpdatedSites(siteIndexSummaryAfterClearance);
        TimeUnit.MILLISECONDS.sleep(13_000);

        final ResponseEntity<List> allPagesAfterClearance = caller.exchange(SiteController.ENDPOINT + "/" + newSite.getSiteId(),
                HttpMethod.GET, HttpEntity.EMPTY, List.class);
        @SuppressWarnings("unchecked")
        List<String> allPageIdsAfterClearance = allPagesAfterClearance.getBody();
        assertEquals(siteIndexSummaryAfterClearance.getDocuments().size(), allPageIdsAfterClearance.size());
    }

    private SiteIndexSummary indexCrawlerPage(String indexedDocumentsPage, UUID siteId, UUID siteSecret, Boolean clearIndex) {
        final ResponseEntity<SiteIndexSummary> response = caller.exchange(SiteController.ENDPOINT + "/" + siteId + "/xml" +
                        "?xmlUrl=https://raw.githubusercontent.com/intrafind/if-sitesearch/master/service/src/test/resources/intrafind-de/" +
                        indexedDocumentsPage + "&siteSecret=" + siteSecret + "&clearIndex=" + clearIndex,
                HttpMethod.PUT, HttpEntity.EMPTY, SiteIndexSummary.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getFailed().isEmpty());
        assertFalse(response.getBody().getDocuments().isEmpty());
        assertTrue(response.getBody().getSuccessCount() > 0);

        return response.getBody();
    }
}