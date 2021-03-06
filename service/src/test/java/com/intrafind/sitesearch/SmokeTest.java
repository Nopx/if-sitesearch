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

package com.intrafind.sitesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intrafind.sitesearch.dto.Autocomplete;
import com.intrafind.sitesearch.dto.FetchedPage;
import com.intrafind.sitesearch.dto.FoundPage;
import com.intrafind.sitesearch.dto.Hits;
import com.intrafind.sitesearch.dto.SitePage;
import com.intrafind.sitesearch.integration.SearchTest;
import com.intrafind.sitesearch.integration.SiteTest;
import com.intrafind.sitesearch.jmh.LoadIndex2Users;
import com.intrafind.sitesearch.service.SiteCrawler;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger(SmokeTest.class);
    static final String SEARCH_SERVICE_DOMAIN = "@main.sitesearch.cloud/";
    static final String INVALID_CREDENTIALS = "https://sitesearch:invalid" + System.getenv("SERVICE_SECRET");
    private static final String BASIC_ENCODED_PASSWORD = System.getenv("BASIC_ENCODED_PASSWORD");
    public static final String API_FRONTPAGE_MARKER = "<title>Site Search</title>";
    public static final String SITES_API = "https://api.sitesearch.cloud/sites/";
    private static final UUID BW_BANK_SITE_ID = UUID.fromString("269b0538-120b-44b1-a365-488c2f3fcc15");
    private static final int HEADER_SIZE = 399;

    @Autowired
    private TestRestTemplate caller;

    @Test
    public void assureCrawlerProtection() throws Exception {
        final Request request = new Request.Builder()
                .url("https://crawler.sitesearch.cloud")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.code());
    }

    @Test
    public void assureTaggerProtection() throws Exception {
        final Request request = new Request.Builder()
                .url("https://tagger.analyzelaw.com")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.code());
    }

    @Test
    public void assureTaggerContent() throws Exception {
        final Request request = new Request.Builder()
                .header(HttpHeaders.AUTHORIZATION, BASIC_ENCODED_PASSWORD)
                .url("https://tagger.analyzelaw.com/json/tagger?method=tag&param0=test")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.OK.value(), response.code());
    }

    @Test
    public void assureCDNavailability() throws Exception {
        final Request request = new Request.Builder()
                .url("https://cdn.sitesearch.cloud/searchbar/2018-01-15/config/sitesearch-roles.json") // lightweight file
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.OK.value(), response.code());
    }

    @Test
    public void assureSiteSearchServiceBasicAuthProtectionForJsonPost() {
        final ResponseEntity<String> secureEndpointJson = caller.postForEntity(URI.create(INVALID_CREDENTIALS + SEARCH_SERVICE_DOMAIN + "json/index?method=index"), HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, secureEndpointJson.getStatusCode());
    }

    private static final String PRODUCT_FRONTPAGE_MARKER = "<title>Site Search - Get the best search results from your Website</title>";

    @Test
    public void redirectFromHttpNakedDomain() { // fails quite often because of 1&1
        final ResponseEntity<String> response = caller.exchange(
                "http://sitesearch.cloud",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
    }

    @Test
    public void redirectFromUnencryptedWWW() { // fails quite often because of 1&1
        final ResponseEntity<String> response = caller.exchange(
                "http://www.sitesearch.cloud",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
    }

    @Test
    public void redirectFromWWW() { // fails quite often because of 1&1
        final ResponseEntity<String> response = caller.exchange(
                "https://www.sitesearch.cloud",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
    }

    @Test
    public void productFrontpageContent() {
        final ResponseEntity<String> response = caller.exchange(
                "https://sitesearch.cloud",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains(PRODUCT_FRONTPAGE_MARKER));
    }

    @Test
    public void redirectFromHttpApiDomain() {
        final ResponseEntity<String> response = caller.exchange(
                "http://api.sitesearch.cloud",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class
        );
        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    private static final Map<String, String> CORS_TRIGGERING_REQUEST_HEADER = new HashMap<String, String>() {
        {
            put("origin", "https://example.com");
        }
    };

    private void assureCorsHeaders(Headers headers, int byteCount) {
        assertEquals(byteCount, headers.byteCount());
        assertEquals("https://example.com", headers.get("access-control-allow-origin"));
        assertEquals("true", headers.get("access-control-allow-credentials"));
    }

    @Test
    public void apiFrontpageContent() throws Exception {
        final Request request = new Request.Builder()
                .url("https://api.sitesearch.cloud")
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.OK.value(), response.code());
        assertNotNull(response.body());
        assertTrue(response.body().string().contains(API_FRONTPAGE_MARKER));

        assertEquals(HttpStatus.OK.value(), response.code());
        assertNull(response.headers().get("x-frame-options"));
        assertNull(response.headers().get("X-Frame-Options"));
        assureCorsHeaders(response.headers(), 447);
    }

    @Test
    public void searchDeprecated() throws Exception {
        Request request = new Request.Builder()
                .url("https://api.sitesearch.cloud/search?query=Knowledge&siteId=" + SearchTest.SEARCH_SITE_ID)
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        assertNotNull(response.body());
        Hits result = MAPPER.readValue(response.body().bytes(), Hits.class);
        assertEquals("Knowledge", result.getQuery());
        assertEquals(1, result.getResults().size());
        FoundPage found = result.getResults().get(0);
        assertEquals("Wie die Semantische Suche vom <span class=\"if-teaser-highlight\">Knowledge</span> Graph profitiert", found.getTitle());
        assertEquals("http:&#x2F;&#x2F;intrafind.de&#x2F;blog&#x2F;wie-die-semantische-suche-vom-<span class=\"if-teaser-highlight\">knowledge</span>-graph-profitiert", found.getUrl());
        assertEquals("http://intrafind.de/blog/wie-die-semantische-suche-vom-knowledge-graph-profitiert", found.getUrlRaw());
        assertTrue(found.getBody().startsWith("&lt;p&gt;Der <span class=\"if-teaser-highlight\">Knowledge</span> Graph ist vielen Nutzern bereits durch Google oder Facebook bekannt. Aber auch"));

        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }


    @Test
    public void searchBwBank() throws Exception {
        final String query = "bank";
        Request request = new Request.Builder()
                .url(SITES_API + BW_BANK_SITE_ID + "/search?query=" + query)
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        assertNotNull(response.body());
        Hits result = MAPPER.readValue(response.body().bytes(), Hits.class);
        assertEquals(query, result.getQuery());
        assertTrue(40 < result.getResults().size());
        FoundPage found = result.getResults().get(0);
        assertTrue(100 < found.getBody().length());

        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }

    @Test
    public void search() throws Exception {
        Request request = new Request.Builder()
                .url(SITES_API + SearchTest.SEARCH_SITE_ID + "/search?query=Knowledge")
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        assertNotNull(response.body());
        Hits result = MAPPER.readValue(response.body().bytes(), Hits.class);
        assertEquals("Knowledge", result.getQuery());
        assertEquals(1, result.getResults().size());
        FoundPage found = result.getResults().get(0);
        assertEquals("Wie die Semantische Suche vom <span class=\"if-teaser-highlight\">Knowledge</span> Graph profitiert", found.getTitle());
        assertEquals("http:&#x2F;&#x2F;intrafind.de&#x2F;blog&#x2F;wie-die-semantische-suche-vom-<span class=\"if-teaser-highlight\">knowledge</span>-graph-profitiert", found.getUrl());
        assertEquals("http://intrafind.de/blog/wie-die-semantische-suche-vom-knowledge-graph-profitiert", found.getUrlRaw());
        assertTrue(found.getBody().startsWith("&lt;p&gt;Der <span class=\"if-teaser-highlight\">Knowledge</span> Graph ist vielen Nutzern bereits durch Google oder Facebook bekannt. Aber auch"));

        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }

    @Test
    public void autocompleteDeprecated() throws Exception {
        Request request = new Request.Builder()
                .url("https://api.sitesearch.cloud/autocomplete?query=Knowledge&siteId=" + SearchTest.SEARCH_SITE_ID)
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        Autocomplete results = MAPPER.readValue(response.body().bytes(), Autocomplete.class);
        assertEquals(1, results.getResults().size());
        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }

    @Test
    public void autocomplete() throws Exception {
        Request request = new Request.Builder()
                .url(SITES_API + SearchTest.SEARCH_SITE_ID + "/autocomplete?query=Knowledge")
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        Autocomplete result = MAPPER.readValue(response.body().bytes(), Autocomplete.class);
        assertEquals(1, result.getResults().size());
        assertEquals("knowledge graph", result.getResults().get(0).toLowerCase());
        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }

    @Test
    public void autocompleteBwBank() throws Exception {
        Request request = new Request.Builder()
                .url(SITES_API + BW_BANK_SITE_ID + "/autocomplete?query=bank")
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.OK.value(), response.code());
        Autocomplete result = MAPPER.readValue(response.body().bytes(), Autocomplete.class);
        assertTrue(3 < result.getResults().size());
        assureCorsHeaders(response.headers(), HEADER_SIZE);
    }

    @Test
    public void logsUp() throws Exception {
        Request request = new Request.Builder()
                .url("https://logs.sitesearch.cloud")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.code());
    }

    @Test
    public void dockerRegistryIsSecure() throws Exception {
        Request request = new Request.Builder()
                .url("https://docker-registry.sitesearch.cloud")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.code());
    }

    @Test
    public void dockerRegistryIsUp() throws Exception {
        Request request = new Request.Builder()
                .header(HttpHeaders.AUTHORIZATION, BASIC_ENCODED_PASSWORD)
                .url("https://docker-registry.sitesearch.cloud")
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();
        assertEquals(HttpStatus.OK.value(), response.code());
    }

    @Test
    public void updatePage() throws Exception {
        String entropyToCheckInUpdate = "https://example.com/" + UUID.randomUUID();
        final SitePage pageToUpdate = SiteTest.buildPage();
        pageToUpdate.setUrl(entropyToCheckInUpdate);
        Request request = new Request.Builder()
                .url(SITES_API + LoadIndex2Users.SEARCH_SITE_ID + "/pages?siteSecret=" + LoadIndex2Users.SEARCH_SITE_SECRET)
                .headers(Headers.of(CORS_TRIGGERING_REQUEST_HEADER))
                .put(RequestBody.create(SiteCrawler.JSON_MEDIA_TYPE, MAPPER.writeValueAsBytes(pageToUpdate)))
                .build();
        final Response response = HTTP_CLIENT.newCall(request).execute();

        assertEquals(HttpStatus.OK.value(), response.code());
        assertNull(response.headers().get(HttpHeaders.LOCATION));
        assureCorsHeaders(response.headers(), HEADER_SIZE);
        FetchedPage fetchedPage = MAPPER.readValue(response.body().byteStream(), FetchedPage.class);
        assertEquals(entropyToCheckInUpdate, fetchedPage.getUrl());
        assertFalse(fetchedPage.getBody().isEmpty());
        assertFalse(fetchedPage.getTitle().isEmpty());
    }
}
