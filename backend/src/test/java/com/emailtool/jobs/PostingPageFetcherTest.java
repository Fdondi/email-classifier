package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostingPageFetcherTest {

    private static final String POSTING = "<html><body><h1>Senior Engineer</h1><p>" + "We build things. ".repeat(40)
            + "</p></body></html>";

    private MockWebServer server;
    private PostingPageFetcher fetcher;
    private final List<String> rendered = new ArrayList<>();
    private final PostingPageFetcher.Renderer browser = url -> {
        rendered.add(url);
        return url.contains("wall")
                ? "<html><head><title>Sign Up | LinkedIn</title></head><body>" + "Join now. ".repeat(60) + "</body></html>"
                : POSTING;
    };

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                return switch (path) {
                    case "/robots.txt" -> new MockResponse().setBody("User-agent: *\nDisallow: /private\n");
                    case "/job/1" -> html(POSTING);
                    case "/track/1" -> new MockResponse().setResponseCode(302).setHeader("Location", "/job/1");
                    case "/track/linkedin" -> new MockResponse().setResponseCode(302)
                            .setHeader("Location", "https://www.linkedin.com/jobs/view/1/");
                    case "/spa" -> html("<html><body><div id=root></div></body></html>");
                    default -> new MockResponse().setResponseCode(404);
                };
            }
        });
        server.start();
        fetcher = new PostingPageFetcher(List.of("linkedin.com", "jobs.ch"), "test-agent", h -> Jsoup.parse(h).text(), browser);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void followsAllowedRedirectsAndReturnsPageText() {
        PostingPageFetcher.Result r = fetcher.fetch(server.url("/track/1").toString());
        assertThat(r.linkOnlyReason()).isNull();
        assertThat(r.page().finalUrl()).endsWith("/job/1");
        assertThat(r.page().text()).startsWith("Senior Engineer");
        assertThat(r.page().html()).contains("<h1>");
    }

    @Test
    void opensJobBoardsOnlyInTheBrowserAtTheCleanedUrl() throws Exception {
        PostingPageFetcher.Result r = fetcher.fetch(
                "https://www.linkedin.com/comm/jobs/view/42/?trackingId=abc&otpToken=secret&refId=x");
        assertThat(r.linkOnlyReason()).isNull();
        assertThat(r.page().text()).startsWith("Senior Engineer");
        // A redirect from a tracker onto a job board hands over to the browser too.
        fetcher.fetch(server.url("/track/linkedin").toString());
        assertThat(rendered).containsExactly("https://www.linkedin.com/jobs/view/42/", "https://www.linkedin.com/jobs/view/1/");
        int count = server.getRequestCount();
        for (int i = 0; i < count; i++) {
            assertThat(server.takeRequest().getPath()).isIn("/robots.txt", "/track/linkedin");
        }
    }

    @Test
    void sendsOnlyTheLinkForLoginWallsAndNonWebSchemes() {
        assertThat(fetcher.fetch("https://www.jobs.ch/en/wall").linkOnlyReason()).contains("sign in");
        assertThat(fetcher.fetch("file://attacker/share/job.html").linkOnlyReason()).isEqualTo("not a web address");
        assertThat(rendered).containsExactly("https://www.jobs.ch/en/wall");
        PostingPageFetcher noBrowser = new PostingPageFetcher(List.of("linkedin.com"), "test-agent", h -> Jsoup.parse(h).text(), null);
        assertThat(noBrowser.fetch("https://www.linkedin.com/jobs/view/1/").linkOnlyReason()).contains("none is configured");
    }

    @Test
    void obeysRobotsTxtAndRendersScriptOnlyPages() throws Exception {
        assertThat(fetcher.fetch(server.url("/private/job").toString()).linkOnlyReason()).contains("robots.txt");
        PostingPageFetcher.Result spa = fetcher.fetch(server.url("/spa").toString());
        assertThat(spa.page().text()).startsWith("Senior Engineer");
        assertThat(rendered).containsExactly(server.url("/spa").toString());
        assertThat(fetcher.fetch(server.url("/gone").toString()).linkOnlyReason()).contains("HTTP 404");
        // robots.txt is read once per site
        long robotsRequests = 0;
        int count = server.getRequestCount();
        for (int i = 0; i < count; i++) {
            if ("/robots.txt".equals(server.takeRequest().getPath())) {
                robotsRequests++;
            }
        }
        assertThat(robotsRequests).isEqualTo(1);
    }

    private static MockResponse html(String body) {
        return new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body);
    }
}
