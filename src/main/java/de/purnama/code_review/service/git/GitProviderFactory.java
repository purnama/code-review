package de.purnama.code_review.service.git;

import java.util.List;
import org.springframework.stereotype.Service;

import de.purnama.code_review.exception.GitProviderException;
import lombok.RequiredArgsConstructor;

/**
 * Factory for selecting the appropriate Git provider based on the URL
 */
@Service
@RequiredArgsConstructor
public class GitProviderFactory {

    private final List<GitProvider> providers;

    /**
     * Get the appropriate Git provider for a URL
     *
     * @param url The URL to find a provider for
     * @return The appropriate Git provider
     * @throws GitProviderException If no provider can handle the URL
     */
    public GitProvider getProviderForUrl(String url) throws GitProviderException {
        return providers.stream()
                .filter(provider -> provider.canHandle(url))
                .findFirst()
                .orElseThrow(() -> new GitProviderException("No Git provider found for URL: " + url));
    }

    /**
     * Get the appropriate Git provider for a URL (alias for getProviderForUrl)
     *
     * @param url The URL to find a provider for
     * @return The appropriate Git provider
     * @throws GitProviderException If no provider can handle the URL
     */
    public GitProvider getProvider(String url) throws GitProviderException {
        return getProviderForUrl(url);
    }
}
