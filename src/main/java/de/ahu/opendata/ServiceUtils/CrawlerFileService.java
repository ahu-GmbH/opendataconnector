package de.ahu.opendata.ServiceUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import de.ahu.opendata.OpenDataNrw.FileDTO;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@NoArgsConstructor
@Slf4j
public class CrawlerFileService {

	public static final Map<String, List<FileDTO>> cacheCrawledData = new ConcurrentHashMap<>();
	private Set<String> visitedUrls;
	private int MAX_DEPTH;
	private String BASE_URL;
	private Set<FileDTO> foundDateiDtos = null;

	private static final Pattern FILE_PATTERN = Pattern.compile(".*(\\.csv|_CSV\\.zip|\\.xlsx)$");
	private static final Pattern METADATA_PATTERN = Pattern.compile(".*/datasets/iso/([a-f0-9\\-]+)$");
	private static final Pattern EXCLUDED_EXT_PATTERN = Pattern
			.compile(".*(\\.tif|\\.png|\\.jpg|\\.pdf|\\.xml|\\.json)$");
	private static final Map<String, String> FILE_TYPE_MAP = Map.of(".csv", "csv", ".zip", "zip", ".xlsx", "xlsx",
			".txt", "txt", ".pdf", "pdf");

	private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 60;
	private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

	public CrawlerFileService(String baseUrl, int maxDepth) {
		this.BASE_URL = baseUrl;
		this.MAX_DEPTH = maxDepth;
		this.visitedUrls = ConcurrentHashMap.newKeySet();
		this.foundDateiDtos = new HashSet<FileDTO>();
	}

	public Set<FileDTO> fetchFoundFiles() {
		return foundDateiDtos;
	}

	public void startCrawling(String url) {
		try {
			crawlFiles(url, 0, "zip");
		} finally {
			shutdown();
		}
	}

	public void crawlFiles(String url, int depth, String mimePattern) {
		if (depth > MAX_DEPTH || visitedUrls.contains(url) || !url.startsWith(BASE_URL)) {
			return;
		}

		visitedUrls.add(url);
		try {
			Document doc = Jsoup.connect(url)
					.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
					.ignoreContentType(true).followRedirects(true).timeout(30 * 1000).get();

			List<String> links = doc.select("a[href]").stream().map(element -> element.absUrl("href"))
					.filter(link -> isValidDirectory(link) && !visitedUrls.contains(link)).collect(Collectors.toList());

			String categoryTitle = Optional.ofNullable(doc.selectFirst("h3")).map(Element::text)
					.orElse("Untitled Category");

			Elements tables = doc.select("table.table-condensed");
			for (Element table : tables) {
				Elements tableRows = table.select("tbody tr");
				for (Element row : tableRows) {
					Elements columns = row.select("td");
					if (columns.size() >= 3) {
						Element linkElement = columns.get(0).selectFirst("a[href]");
						if (linkElement != null) {
							String fileUrl = linkElement.absUrl("href");

							if (fileUrl.matches(".*\\.(" + mimePattern + ")$")) {

								FileDTO fileDTO = new FileDTO();
								fileDTO.setUrl(fileUrl);
								fileDTO.setType(getFileType(fileUrl));
								fileDTO.setTitle(categoryTitle);
								fileDTO.setLastModified(columns.get(1).text());
								fileDTO.setFileSize(columns.get(2).text());

								String metadataUrl = Optional
										.ofNullable(doc.selectFirst("a[target=\"_blank\"][href*=\"geoportal.nrw\"]"))
										.map(e -> e.absUrl("href")).orElse(null);
								fileDTO.setMetadataId(extractMetadataId(metadataUrl));

								foundDateiDtos.add(fileDTO);
							}
						}
					}
				}
			}
			List<CompletableFuture<Void>> futures = links.stream().filter(link -> {
				try {
					return isValidDirectory(new URI(link).getPath());
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				return false;
			}).map(link -> CompletableFuture.runAsync(() -> crawlFiles(link, depth + 1, mimePattern), executor))
					.collect(Collectors.toList());

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		} catch (java.net.SocketTimeoutException e) {
			log.info("Timeout accessing: " + url + " - skipping");
		} catch (IOException e) {
			log.info("Failed to access: " + url + " - " + e.getMessage());
		}
	}

	private String extractMetadataId(String metadataUrl) {
		if (metadataUrl == null || metadataUrl.isEmpty()) {
			return null;
		}
		Matcher matcher = METADATA_PATTERN.matcher(metadataUrl);

		return matcher.find() ? matcher.group(1) : null;
	}

	private static boolean isValidDirectory(String url) {
		if (StringUtils.isBlank(url)) {
			return false;
		}
		try {
			String path = new URI(url).getPath();
			return !FILE_PATTERN.matcher(url).matches() && path != null && path.length() > 1
					&& !EXCLUDED_EXT_PATTERN.matcher(url).matches();
		} catch (URISyntaxException e) {
			return false;
		}
	}

	private static String getFileType(String url) {
		return FILE_TYPE_MAP.entrySet().stream().filter(entry -> url.endsWith(entry.getKey())).map(Map.Entry::getValue)
				.findFirst().orElse("unknown");
	}

	public void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}
