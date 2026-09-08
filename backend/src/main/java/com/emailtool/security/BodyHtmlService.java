package com.emailtool.security;

import com.emailtool.domain.Email;
import com.emailtool.domain.EmailRemoteResource;
import com.emailtool.persist.EmailRemoteResourceRepository;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BodyHtmlService {

    private final EmailRemoteResourceRepository resourceRepository;

    public BodyHtmlService(EmailRemoteResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public String render(Email email) {
        String html = email.getHtmlSanitized() == null ? "" : email.getHtmlSanitized();
        if (email.isImagesLoaded()) {
            html = replacePlaceholders(html, resourceRepository.findByEmailIdOrderByResourceIndexAsc(email.getId()));
        }
        return wrap(html);
    }

    private static String replacePlaceholders(String html, List<EmailRemoteResource> resources) {
        String result = html;
        for (EmailRemoteResource resource : resources) {
            if (resource.getCachedBytes() == null) {
                continue;
            }
            String needle = "data-remote-index=\"" + resource.getResourceIndex() + "\"";
            int attr = result.indexOf(needle);
            if (attr < 0) {
                continue;
            }
            int imgStart = result.lastIndexOf("<img", attr);
            if (imgStart < 0) {
                continue;
            }
            int src = result.indexOf("src=\"", imgStart);
            if (src < 0 || src > attr) {
                continue;
            }
            int srcEnd = result.indexOf('"', src + 5);
            if (srcEnd < 0) {
                continue;
            }
            result = result.substring(0, src + 5) + toDataUri(resource) + result.substring(srcEnd);
        }
        return result;
    }

    private static String toDataUri(EmailRemoteResource resource) {
        String type = resource.getContentType() == null ? "image/png" : resource.getContentType().split(";")[0].trim();
        if (!type.startsWith("image/")) {
            type = "image/png";
        }
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(resource.getCachedBytes());
    }

    private static String wrap(String body) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>" + body + "</body></html>";
    }
}
