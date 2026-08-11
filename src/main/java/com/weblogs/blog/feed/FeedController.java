package com.weblogs.blog.feed;

import com.weblogs.blog.config.AppProperties;
import com.weblogs.blog.post.Post;
import com.weblogs.blog.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Serves the RSS 2.0 feed ({@code /feed.xml}) and the XML sitemap ({@code /sitemap.xml}).
 *
 * <p>Both endpoints are fully public and require no authentication.
 * XML is built as a plain string — no JAXB or Rome dependency needed for this
 * small, well-defined document structure.
 *
 * <p>Canonial post URLs point to the frontend application ({@code app.frontend-url}),
 * not the API server, so search engines and RSS readers link directly to the
 * human-readable blog UI.
 */
@RestController
@RequiredArgsConstructor
public class FeedController {

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PostRepository postRepository;
    private final AppProperties  appProperties;

    // ── RSS Feed ──────────────────────────────────────────────────────────────

    /**
     * GET /feed.xml
     *
     * <p>Returns an RSS 2.0 document containing the latest published posts.
     * The number of posts is controlled by {@code app.cache.feed-limit} (default 20).
     */
    @GetMapping(value = "/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    @Transactional(readOnly = true)
    public String rssFeed() {
        int feedLimit    = appProperties.getCache().getFeedLimit();
        String frontendUrl = appProperties.getFrontendUrl();

        List<Post> posts = postRepository.findLatestPublished(PageRequest.of(0, feedLimit));

        String now = ZonedDateTime.now(ZoneOffset.UTC).format(RFC_822);

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n")
          .append("  <channel>\n")
          .append("    <title>Weblogs</title>\n")
          .append("    <link>").append(escapeXml(frontendUrl)).append("</link>\n")
          .append("    <atom:link href=\"").append(escapeXml(appProperties.getSiteUrl()))
                  .append("/feed.xml\" rel=\"self\" type=\"application/rss+xml\"/>\n")
          .append("    <description>Latest posts from Weblogs</description>\n")
          .append("    <language>en-us</language>\n")
          .append("    <lastBuildDate>").append(now).append("</lastBuildDate>\n");

        for (Post post : posts) {
            String postUrl   = frontendUrl + "/posts/" + post.getSlug();
            String pubDate   = post.getPublishedAt() != null
                    ? ZonedDateTime.ofInstant(post.getPublishedAt(), ZoneOffset.UTC).format(RFC_822)
                    : now;
            String excerpt   = buildExcerpt(post);

            sb.append("    <item>\n")
              .append("      <title>").append(escapeXml(post.getTitle())).append("</title>\n")
              .append("      <link>").append(escapeXml(postUrl)).append("</link>\n")
              .append("      <guid isPermaLink=\"true\">").append(escapeXml(postUrl)).append("</guid>\n")
              .append("      <description>").append(escapeXml(excerpt)).append("</description>\n")
              .append("      <pubDate>").append(pubDate).append("</pubDate>\n")
              .append("    </item>\n");
        }

        sb.append("  </channel>\n")
          .append("</rss>");

        return sb.toString();
    }

    // ── Sitemap ───────────────────────────────────────────────────────────────

    /**
     * GET /sitemap.xml
     *
     * <p>Returns an XML Sitemap 0.9 document containing ALL published, non-deleted posts.
     * No pagination — the sitemap must be complete so search engines can index everything.
     * For very large blogs (&gt;50 000 posts), this should be split into a sitemap index.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    @Transactional(readOnly = true)
    public String sitemap() {
        String frontendUrl = appProperties.getFrontendUrl();

        // Fetch all published posts — no limit for sitemap completeness
        List<Post> posts = postRepository.findLatestPublished(PageRequest.of(0, Integer.MAX_VALUE));

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (Post post : posts) {
            String loc     = frontendUrl + "/posts/" + post.getSlug();
            String lastmod = post.getUpdatedAt() != null
                    ? ZonedDateTime.ofInstant(post.getUpdatedAt(), ZoneOffset.UTC).format(ISO_DATE)
                    : ZonedDateTime.ofInstant(post.getCreatedAt(), ZoneOffset.UTC).format(ISO_DATE);

            sb.append("  <url>\n")
              .append("    <loc>").append(escapeXml(loc)).append("</loc>\n")
              .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
              .append("    <changefreq>weekly</changefreq>\n")
              .append("    <priority>0.8</priority>\n")
              .append("  </url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a safe excerpt for the RSS item description.
     * Uses the explicit {@code excerpt} field if present; otherwise takes the
     * first 200 characters of the raw content.
     */
    private String buildExcerpt(Post post) {
        if (post.getExcerpt() != null && !post.getExcerpt().isBlank()) {
            return post.getExcerpt();
        }
        String content = post.getContent();
        return content.length() <= 200 ? content : content.substring(0, 200) + "…";
    }

    /**
     * Escapes XML special characters in text content and attributes.
     * Sufficient for ASCII/UTF-8 content without CDATA — avoids a parser dependency.
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&apos;");
    }
}
