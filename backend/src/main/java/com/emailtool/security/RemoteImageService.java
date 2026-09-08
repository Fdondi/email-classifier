package com.emailtool.security;

import com.emailtool.domain.Email;
import com.emailtool.domain.EmailRemoteResource;
import com.emailtool.persist.EmailRemoteResourceRepository;
import com.emailtool.persist.EmailRepository;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RemoteImageService {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final EmailRepository emailRepository;
    private final EmailRemoteResourceRepository resourceRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public RemoteImageService(EmailRepository emailRepository, EmailRemoteResourceRepository resourceRepository) {
        this.emailRepository = emailRepository;
        this.resourceRepository = resourceRepository;
    }

    public int loadImages(long emailId) {
        Email email = emailRepository
                .findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        List<EmailRemoteResource> resources = resourceRepository.findByEmailIdOrderByResourceIndexAsc(emailId);
        int loaded = 0;
        for (EmailRemoteResource resource : resources) {
            if (resource.getCachedBytes() != null) {
                loaded++;
                continue;
            }
            FetchedImage fetched = fetchAllowlisted(resource.getOriginalUrl());
            resource.setCachedBytes(fetched.bytes());
            resource.setContentType(fetched.contentType());
            resource.setFetchedAt(Instant.now());
            resourceRepository.save(resource);
            loaded++;
        }
        email.setImagesLoaded(true);
        emailRepository.save(email);
        return loaded;
    }

    public EmailRemoteResource requireResource(long emailId, int index) {
        return resourceRepository
                .findByEmailIdAndResourceIndex(emailId, index)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not allowlisted"));
    }

    public FetchedImage fetchAllowlisted(String url) {
        URI uri = validateUrl(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "email-tool-image-proxy")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote image fetch failed");
            }
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Remote URL was not an image");
            }
            byte[] body = response.body();
            if (body.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Remote image too large");
            }
            return new FetchedImage(body, contentType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Remote image fetch interrupted");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote image fetch failed");
        }
    }

    public record FetchedImage(byte[] bytes, String contentType) {}

    public URI validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL scheme not allowed");
        }
        if (uri.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL host missing");
        }
        try {
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isMulticastAddress()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL host not allowed");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL host not resolvable");
        }
        return uri;
    }
}
