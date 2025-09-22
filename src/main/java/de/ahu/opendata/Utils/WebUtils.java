package de.ahu.opendata.Utils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import de.ahu.opendata.Konfiguration.Konfiguration;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Slf4j
public class WebUtils {
	private static final WebClient webClient = initWebClient();
	private static final WebClient webClientSimple = WebClient.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1000 * 1024 * 1024)).build();

	public static String fetchContentSimpleWebClient(String url) {
		try {
			return webClientSimple.get().uri(url).retrieve().bodyToMono(String.class)
					.subscribeOn(Schedulers.boundedElastic()).toFuture().get();
		}  catch (ExecutionException e) {
	        if (e.getCause() instanceof WebClientResponseException) {
	            handleWebClientException(url, (WebClientResponseException) e.getCause());
	        } else {
	            handleWebClientException(url, e);
	        }
	        return null;
	    } catch (Exception e) {
	        handleWebClientException(url, e);
	        return null;
	    }
	}

	public static String postContentWebClient(String url, String jsonPayload) {
		try {
			return webClientSimple.post().uri(url).contentType(MediaType.APPLICATION_JSON).bodyValue(jsonPayload)
					.retrieve().bodyToMono(String.class).subscribeOn(Schedulers.boundedElastic()).toFuture().get();
		} catch (Exception e) {
			handleWebClientException(url, e);
			return null;
		}
	}

	public static String fetchRemoteText(String url) {
		try {
			return webClient.get().uri(url).retrieve().bodyToFlux(DataBuffer.class).map(dataBuffer -> {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				DataBufferUtils.release(dataBuffer);
				return new String(bytes, StandardCharsets.UTF_8);
			}).collect(Collectors.joining()).block();
		} catch (Exception e) {
			handleWebClientException(url, e);
			return null;
		}
	}

	public static void handleWebClientException(String url, Exception e) {
		if (e instanceof WebClientResponseException) {
			WebClientResponseException webEx = (WebClientResponseException) e;
			int status = webEx.getStatusCode().value();

			if (status == 400) {
				log.error("400 Bad Request for URL {}: {}", url, webEx.getMessage());
			} else if (status == 403) {
				log.error("403 Forbidden for URL {}: {}", url, webEx.getMessage());
			} else if (status == 404) {
				log.warn("404 Not Found for URL {}: {}", url, webEx.getMessage());
			} else if (status == 500) {
				log.error("500 Internal Server Error for URL {}: {}", url, webEx.getResponseBodyAsString());
			} else {
				log.error("HTTP error while fetching URL {}: status={}, message={}", url, status, webEx.getMessage(),
						webEx);
			}
		} else {
			log.error("Unexpected error while fetching URL {}: {}", url, e.getMessage(), e);
		}
	}

	private static WebClient initWebClient() {
		Konfiguration konfig = SpringApplicationContext.getBean(Konfiguration.class);
		String proxyHost = konfig.getProxyHost();
		Integer proxyPort = konfig.getProxyPort();

		WebClient.Builder builder = WebClient.builder()
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
						MediaType.TEXT_XML_VALUE, "application/gml+xml", MediaType.TEXT_PLAIN_VALUE,
						MediaType.ALL_VALUE)
				.filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
					return Mono.just(response.mutate().headers(headers -> {
						String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
						if (contentType != null) {
							if (contentType.contains("gml") || contentType.contains("xml")
									|| !contentType.contains("charset=")) {
								headers.set(HttpHeaders.CONTENT_TYPE, determineContentType(contentType));
							}
						}
					}).build());
				})).codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(500 * 1024 * 1024));
		if (!StringUtils.isAllBlank(proxyHost) && proxyPort != null) {
			builder.clientConnector(new ReactorClientHttpConnector(HttpClient.create()
					.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort))));
		}
		return builder.build();
	}

	private static String determineContentType(String originalContentType) {
		String lowerCaseType = originalContentType.toLowerCase();

		if (lowerCaseType.contains("json")) {
			return MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";
		} else if (lowerCaseType.contains("xml") || lowerCaseType.contains("gml")) {
			return MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8";
		} else if (lowerCaseType.contains("text")) {
			return MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8";
		}
		return originalContentType.contains("charset=") ? originalContentType : originalContentType + ";charset=UTF-8";
	}

	public static boolean isServiceOnline(String url) {
		try {
			webClient.get().uri(url).retrieve().toBodilessEntity().toString();
			return true;
		} catch (Exception e) {
			handleWebClientException(url, e);
			return false;
		}
	}

}
