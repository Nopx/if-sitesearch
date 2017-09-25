/*
 * Copyright 2017 IntraFind Software AG. All rights reserved.
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

import com.intrafind.sitesearch.controller.SearchController;
import com.intrafind.sitesearch.controller.StatsController;
import com.intrafind.sitesearch.dto.FoundPage;
import com.intrafind.sitesearch.dto.Hits;
import com.intrafind.sitesearch.dto.Stats;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SearchTest {
    public static final UUID SEARCH_SITE_ID = UUID.fromString("18e1cb09-b3ec-40e0-8279-dd005771f172");
    private static final Logger LOG = LoggerFactory.getLogger(SearchTest.class);
    @Autowired
    private TestRestTemplate caller;

    private long fetchQueryCountForDefaultTenant() {
        final ResponseEntity<Stats> response = caller.getForEntity(StatsController.ENDPOINT + "?siteId=" + SEARCH_SITE_ID, Stats.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        return response.getBody().getQueryCount();
    }

    @Test
    public void simpleSearch() throws Exception {
        long beforeCount = fetchQueryCountForDefaultTenant();
        final ResponseEntity<Hits> searchResults = caller.getForEntity(SearchController.ENDPOINT + "?query=Knowledge&siteId=" + SEARCH_SITE_ID, Hits.class);

        assertEquals(HttpStatus.OK, searchResults.getStatusCode());
        assertNotNull(searchResults.getBody());
        assertEquals("Knowledge", searchResults.getBody().getQuery());
        assertEquals(1, searchResults.getBody().getResults().size());
        FoundPage found = searchResults.getBody().getResults().get(0);
        assertEquals("Wie die Semantische Suche vom <span class='if-teaser-highlight'>Knowledge</span> Graph profitiert", found.getTitle());
        assertEquals("http:&#x2F;&#x2F;intrafind.de&#x2F;blog&#x2F;wie-die-semantische-suche-vom-<span class='if-teaser-highlight'>knowledge</span>-graph-profitiert", found.getUrl());
        assertTrue(found.getBody().startsWith("&lt;p&gt;Der <span class='if-teaser-highlight'>Knowledge</span> Graph ist vielen Nutzern bereits durch Google oder Facebook bekannt. Aber auch"));

        assertEquals(++beforeCount, fetchQueryCountForDefaultTenant());
    }

    @Test
    public void simpleSearchNotFound() throws Exception {
        long beforeCount = fetchQueryCountForDefaultTenant();

        final ResponseEntity<Hits> actual = caller.getForEntity(SearchController.ENDPOINT + "?query=not_found&siteId=" + SEARCH_SITE_ID, Hits.class);

        assertEquals(HttpStatus.NOT_FOUND, actual.getStatusCode());
        assertNull(actual.getBody());

        assertEquals(beforeCount, fetchQueryCountForDefaultTenant());
    }

    @Test
    public void searchWithoutSiteId() throws Exception {
        long beforeCount = fetchQueryCountForDefaultTenant();

        final ResponseEntity<Hits> actual = caller.getForEntity(SearchController.ENDPOINT + "?query=not_found", Hits.class);

        assertEquals(HttpStatus.BAD_REQUEST, actual.getStatusCode());
//        assertNotNull(actual.getBody()); // TODO reenable once legacy hack for searchbar is removed

        assertEquals(beforeCount, fetchQueryCountForDefaultTenant());
    }
}

