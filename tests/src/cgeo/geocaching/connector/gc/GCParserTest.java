package cgeo.geocaching.connector.gc;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.Geocache;
import cgeo.geocaching.Image;
import cgeo.geocaching.SearchResult;
import cgeo.geocaching.Trackable;
import cgeo.geocaching.Waypoint;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.test.AbstractResourceInstrumentationTestCase;
import cgeo.geocaching.test.R;
import cgeo.geocaching.test.RegExPerformanceTest;
import cgeo.geocaching.test.mock.MockedCache;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.test.Compare;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import android.os.Handler;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.ArrayList;
import java.util.List;

public class GCParserTest extends AbstractResourceInstrumentationTestCase {

    public void testUnpublishedCacheNotOwner() {
        final int cache = R.raw.cache_unpublished;
        assertUnpublished(cache);
    }

    private void assertUnpublished(final int cache) {
        final String page = getFileContent(cache);
        final SearchResult result = GCParser.parseCacheFromText(page, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(StatusCode.UNPUBLISHED_CACHE, result.getError());
    }

    public void testPublishedCacheWithUnpublishedInDescription1() {
        assertPublishedCache(R.raw.gc430fm_published, "Cache is Unpublished");
    }

    public void testPublishedCacheWithUnpublishedInDescription2() {
        assertPublishedCache(R.raw.gc431f2_published, "Needle in a Haystack");
    }

    private void assertPublishedCache(final int cachePage, final String cacheName) {
        final String page = getFileContent(cachePage);
        final SearchResult result = GCParser.parseCacheFromText(page, null);
        assertNotNull(result);
        assertEquals(1, result.getCount());
        final Geocache cache = result.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
        assertNotNull(cache);
        assert (cache != null); // eclipse bug
        assertEquals(cacheName, cache.getName());
    }

    public void testOwnCache() {
        final Geocache cache = parseCache(R.raw.own_cache);
        assertNotNull("Cache not parsed!", cache);
        assertTrue("No spoilers found!", CollectionUtils.isNotEmpty(cache.getSpoilers()));
        assertEquals("Wrong number of spoilers", 2, cache.getSpoilers().size());
        final Image spoiler = cache.getSpoilers().get(1);
        assertEquals("First spoiler image url wrong", "http://imgcdn.geocaching.com/cache/large/6ddbbe82-8762-46ad-8f4c-57d03f4b0564.jpeg", spoiler.getUrl());
        assertEquals("First spoiler image text wrong", "SPOILER", spoiler.getTitle());
        assertNull("First spoiler image description not empty", spoiler.getDescription());
    }

    private static Geocache createCache(int index) {
        final MockedCache mockedCache = RegExPerformanceTest.MOCKED_CACHES.get(index);
        // to get the same results we have to use the date format used when the mocked data was created
        final String oldCustomDate = Settings.getGcCustomDate();

        final SearchResult searchResult;
        try {
            Settings.setGcCustomDate(MockedCache.getDateFormat());
            searchResult = GCParser.parseCacheFromText(mockedCache.getData(), null);
        } finally {
            Settings.setGcCustomDate(oldCustomDate);
        }

        assertNotNull(searchResult);
        assertEquals(1, searchResult.getCount());

        final Geocache cache = searchResult.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
        assertNotNull(cache);
        return cache;
    }

    /**
     * Test {@link GCParser#parseCacheFromText(String, int, CancellableHandler)} with "mocked" data
     *
     */
    @MediumTest
    public static void testParseCacheFromTextWithMockedData() {
        final String gcCustomDate = Settings.getGcCustomDate();
        try {
            for (MockedCache mockedCache : RegExPerformanceTest.MOCKED_CACHES) {
                // to get the same results we have to use the date format used when the mocked data was created
                Settings.setGcCustomDate(MockedCache.getDateFormat());
                SearchResult searchResult = GCParser.parseCacheFromText(mockedCache.getData(), null);
                Geocache parsedCache = searchResult.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
                assertTrue(StringUtils.isNotBlank(mockedCache.getMockedDataUser()));
                Compare.assertCompareCaches(mockedCache, parsedCache, true);
            }
        } finally {
            Settings.setGcCustomDate(gcCustomDate);
        }
    }

    public static void testWaypointsFromNote() {
        final Geocache cache = createCache(0);

        final Geopoint[] empty = new Geopoint[] {};
        final Geopoint[] one = new Geopoint[] { new Geopoint("N51 21.523", "E7 2.680") };
        assertWaypointsFromNote(cache, empty, "  ");
        assertWaypointsFromNote(cache, empty, "some random strings 1 with n 2 numbers");
        assertWaypointsFromNote(cache, empty, "Station3 some coords");
        assertWaypointsFromNote(cache, one, "Station3: N51 21.523 / E07 02.680");
        assertWaypointsFromNote(cache, one, "N51 21.523 / E07 02.680");
        assertWaypointsFromNote(cache, empty, "N51 21.523");
        assertWaypointsFromNote(cache, one, "  n 51° 21.523 - E07 02.680");
        assertWaypointsFromNote(cache, new Geopoint[] {
                new Geopoint("N51 21.523", "E7 2.680"),
                new Geopoint("N52 21.523", "E12 2.680") },
                "Station3: N51 21.523 / E07 02.680\r\n Station4: N52 21.523 / E012 02.680");
        assertWaypointsFromNote(cache, empty, "51 21 523 / 07 02 680");
        assertWaypointsFromNote(cache, empty, "N51");
        assertWaypointsFromNote(cache, empty, "N 821 O 321"); // issue 922
        assertWaypointsFromNote(cache, empty, "N 821-211 O 322+11");
        assertWaypointsFromNote(cache, empty, "von 240 meter");
        assertWaypointsFromNote(cache, new Geopoint[] {
                new Geopoint("N 51 19.844", "E 7 03.625") },
                "A=7 bis B=12 Quellen\r\nC= 66 , Quersumme von 240 m NN\r\nD= 67 , Quersumme von 223 m NN\r\nParken:\r\nN 51 19.844\r\nE 7 03.625");
        assertWaypointsFromNote(cache, new Geopoint[] {
                new Geopoint("N51 21.444", "E07 02.600"),
                new Geopoint("N51 21.789", "E07 02.800"),
                new Geopoint("N51 21.667", "E07 02.800"),
                new Geopoint("N51 21.444", "E07 02.706"),
                new Geopoint("N51 21.321", "E07 02.700"),
                new Geopoint("N51 21.123", "E07 02.477"),
                new Geopoint("N51 21.734", "E07 02.500"),
                new Geopoint("N51 21.733", "E07 02.378"),
                new Geopoint("N51 21.544", "E07 02.566") },
                "Station3: N51 21.444 / E07 02.600\r\nStation4: N51 21.789 / E07 02.800\r\nStation5: N51 21.667 / E07 02.800\r\nStation6: N51 21.444 / E07 02.706\r\nStation7: N51 21.321 / E07 02.700\r\nStation8: N51 21.123 / E07 02.477\r\nStation9: N51 21.734 / E07 02.500\r\nStation10: N51 21.733 / E07 02.378\r\nFinal: N51 21.544 / E07 02.566");
    }

    @MediumTest
    public static void testEditModifiedCoordinates() {
        final Geocache cache = new Geocache();
        cache.setGeocode("GC2ZN4G");
        // upload coordinates
        GCParser.editModifiedCoordinates(cache, new Geopoint("N51 21.544", "E07 02.566"));
        cache.drop(new Handler());
        final String page = GCParser.requestHtmlPage(cache.getGeocode(), null, "n", "0");
        final Geocache cache2 = GCParser.parseCacheFromText(page, null).getFirstCacheFromResult(LoadFlags.LOAD_CACHE_ONLY);
        assertNotNull(cache2);
        assert (cache2 != null); // eclipse bug
        assertTrue(cache2.hasUserModifiedCoords());
        assertEquals(new Geopoint("N51 21.544", "E07 02.566"), cache2.getCoords());
        // delete coordinates
        GCParser.deleteModifiedCoordinates(cache2);
        cache2.drop(new Handler());
        final String page2 = GCParser.requestHtmlPage(cache.getGeocode(), null, "n", "0");
        final Geocache cache3 = GCParser.parseCacheFromText(page2, null).getFirstCacheFromResult(LoadFlags.LOAD_CACHE_ONLY);
        assertNotNull(cache3);
        assert (cache3 != null); // eclipse bug
        assertFalse(cache3.hasUserModifiedCoords());
    }

    private static void assertWaypointsFromNote(final Geocache cache, Geopoint[] expected, String note) {
        cache.setPersonalNote(note);
        cache.setWaypoints(new ArrayList<Waypoint>(), false);
        cache.parseWaypointsFromNote();
        final List<Waypoint> waypoints = cache.getWaypoints();
        assertEquals(expected.length, waypoints.size());
        for (int i = 0; i < expected.length; i++) {
            assertTrue(expected[i].equals(waypoints.get(i).getCoords()));
        }
    }

    public void testWaypointParsing() {
        Geocache cache = parseCache(R.raw.gc366bq);
        assertEquals(13, cache.getWaypoints().size());
        //make sure that waypoints are not duplicated
        cache = parseCache(R.raw.gc366bq);
        assertEquals(13, cache.getWaypoints().size());
    }

    public static void testNoteParsingWaypointTypes() {
        final Geocache cache = new Geocache();
        cache.setWaypoints(new ArrayList<Waypoint>(), false);
        cache.setPersonalNote("\"Parking area at PARKING=N 50° 40.666E 006° 58.222\n" +
                "My calculated final coordinates: FINAL=N 50° 40.777E 006° 58.111\n" +
                "Get some ice cream at N 50° 40.555E 006° 58.000\"");

        cache.parseWaypointsFromNote();
        final List<Waypoint> waypoints = cache.getWaypoints();

        assertEquals(3, waypoints.size());
        assertEquals(WaypointType.PARKING, waypoints.get(0).getWaypointType());
        assertEquals(WaypointType.FINAL, waypoints.get(1).getWaypointType());
        assertEquals(WaypointType.WAYPOINT, waypoints.get(2).getWaypointType());
    }

    private Geocache parseCache(int resourceId) {
        final String page = getFileContent(resourceId);
        final SearchResult result = GCParser.parseCacheFromText(page, null);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        return result.getFirstCacheFromResult(LoadFlags.LOAD_CACHE_OR_DB);
    }

    public void testTrackableNotActivated() {
        final String page = getFileContent(R.raw.tb123e_html);
        final Trackable trackable = GCParser.parseTrackable(page, "TB123E");
        assertNotNull(trackable);
        assertEquals("TB123E", trackable.getGeocode());
        final String expectedDetails = CgeoApplication.getInstance().getString(cgeo.geocaching.R.string.trackable_not_activated);
        assertEquals(expectedDetails, trackable.getDetails());
    }
}
