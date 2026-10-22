package org.lazycent.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeoController {

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getRobotsTxt() {
        return "User-agent: *\n" +
               "Allow: /\n" +
               "Allow: /login\n" +
               "Allow: /register\n" +
               "Allow: /privacy\n" +
               "Allow: /terms\n" +
               "Disallow: /home\n" +
               "Disallow: /budget\n" +
               "Disallow: /totals\n" +
               "Disallow: /all-days\n" +
               "Disallow: /notes\n" +
               "Disallow: /settings\n" +
               "Disallow: /day/\n" +
               "\n" +
               "Sitemap: https://lazycent.com/sitemap.xml";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String getSitemapXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" +
               "  <url>\n" +
               "    <loc>https://lazycent.com/</loc>\n" +
               "    <lastmod>2026-10-22</lastmod>\n" +
               "    <changefreq>weekly</changefreq>\n" +
               "    <priority>1.0</priority>\n" +
               "  </url>\n" +
               "  <url>\n" +
               "    <loc>https://lazycent.com/login</loc>\n" +
               "    <changefreq>monthly</changefreq>\n" +
               "    <priority>0.8</priority>\n" +
               "  </url>\n" +
               "  <url>\n" +
               "    <loc>https://lazycent.com/register</loc>\n" +
               "    <changefreq>monthly</changefreq>\n" +
               "    <priority>0.8</priority>\n" +
               "  </url>\n" +
               "  <url>\n" +
               "    <loc>https://lazycent.com/privacy</loc>\n" +
               "    <changefreq>yearly</changefreq>\n" +
               "    <priority>0.5</priority>\n" +
               "  </url>\n" +
               "  <url>\n" +
               "    <loc>https://lazycent.com/terms</loc>\n" +
               "    <changefreq>yearly</changefreq>\n" +
               "    <priority>0.5</priority>\n" +
               "  </url>\n" +
               "</urlset>";
    }
}