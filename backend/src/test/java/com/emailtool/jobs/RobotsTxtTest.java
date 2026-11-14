package com.emailtool.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsTxtTest {

    @Test
    void starGroupLongestMatchAndWildcards() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: Googlebot
                Disallow: /

                User-agent: *
                Disallow: /search
                Disallow: /*?apply=
                Allow: /search/jobs
                Disallow: /*.pdf$
                """, "email-tool");
        assertThat(r.allows("/job/123")).isTrue();
        assertThat(r.allows("/search?q=x")).isFalse();
        assertThat(r.allows("/search/jobs/123")).isTrue();
        assertThat(r.allows("/job/1?apply=1")).isFalse();
        assertThat(r.allows("/a.pdf")).isFalse();
        assertThat(r.allows("/a.pdf?x")).isTrue();
    }

    @Test
    void ownGroupWinsOverStar() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: *
                Allow: /

                User-agent: email-tool
                Disallow: /jobs
                """, "email-tool");
        assertThat(r.allows("/jobs/1")).isFalse();
        assertThat(r.allows("/about")).isTrue();
    }

    @Test
    void consecutiveAgentsShareOneGroupAndEmptyDisallowAllowsAll() {
        RobotsTxt r = RobotsTxt.parse("""
                User-agent: bingbot
                User-agent: *
                Disallow:
                """, "email-tool");
        assertThat(r.allows("/anything")).isTrue();
        assertThat(RobotsTxt.DISALLOW_ALL.allows("/x")).isFalse();
    }
}
