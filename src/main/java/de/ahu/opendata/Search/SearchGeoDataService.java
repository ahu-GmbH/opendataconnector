package de.ahu.opendata.Search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Service;

import de.ahu.opendata.DataUtils.StationDTO.Measurement;
import de.ahu.opendata.OpenDataNrw.FileDTO;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterStationDTO;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SearchGeoDataService {

	private final CRSFactory crsFactory = new CRSFactory();
	private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();

	private static final int BUFFER_SIZE = 1024 * 1024;
	private static final ExecutorService ENTRY_EXECUTOR = Executors.newFixedThreadPool(50);
	private static final String CACHE_DIR = System.getProperty("user.dir") + "/src/main/resources/cache";
	private static final String INCREMENT_CACHE_DIR = System.getProperty("user.dir")
			+ "/src/main/resources/increment-cache";

	public void scheduleCache() {
		File cacheDir = new File(CACHE_DIR);
		if (!cacheDir.exists()) {
			boolean created = cacheDir.mkdirs();
			if (created) {
				log.info("Cache directory created: " + cacheDir.getAbsolutePath());
			} else {
				log.error("Failed to create cache directory: " + cacheDir.getAbsolutePath());
				throw new IllegalStateException("Cannot create cache directory: " + cacheDir.getAbsolutePath());
			}
		}
		scheduleCacheCleanup(CACHE_DIR);

		File incrementalCacheDir = new File(INCREMENT_CACHE_DIR);
		if (!incrementalCacheDir.exists()) {
			boolean created = incrementalCacheDir.mkdirs();
			if (created) {
				log.info("Cache directory created: " + incrementalCacheDir.getAbsolutePath());
			} else {
				log.error("Failed to create cache directory: " + incrementalCacheDir.getAbsolutePath());
				throw new IllegalStateException(
						"Cannot create cache directory: " + incrementalCacheDir.getAbsolutePath());
			}
		}
		scheduleCacheCleanup(INCREMENT_CACHE_DIR);
	}

	public List<FileDTO> extractContentZipFiles(Set<FileDTO> zipFiles) {
		List<FileDTO> extractedFiles = new ArrayList<>();
		if (zipFiles == null || zipFiles.isEmpty()) {
			return extractedFiles;
		}
		ExecutorService executor = Executors.newFixedThreadPool(50);
		List<Future<List<FileDTO>>> futures = new ArrayList<>();
		try {
			for (FileDTO zipFile : zipFiles) {
				if (zipFile == null || zipFile.getUrl() == null) {
					continue;
				}
				Future<List<FileDTO>> future = executor.submit(() -> processSingleZipFile(zipFile));
				futures.add(future);
			}

			for (Future<List<FileDTO>> future : futures) {
				try {
					extractedFiles.addAll(future.get(5, TimeUnit.MINUTES));
				} catch (Exception e) {
					log.error("Error processing ZIP file task: " + e.getMessage());
				}
			}
		} finally {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		return extractedFiles;
	}

	public List<FileDTO> incrementalUpdateDate(String url) {
		List<FileDTO> extractedFiles = cacheFiles(url, INCREMENT_CACHE_DIR);
		if (!extractedFiles.isEmpty()) {
			return extractedFiles;
		}
		File tempZipFile = Utils.downloadZipToFile(url);
		try (ZipFile zipFile = new ZipFile(tempZipFile, StandardCharsets.UTF_8)) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (shouldProcessEntry(entry)) {
					processEntry(zipFile, entry, url, new File(INCREMENT_CACHE_DIR),
							Utils.getFileName(url).replaceAll("[^a-zA-Z0-9.-]", "_") + "_");
				}
			}
		} catch (Exception e) {
			log.error("ZIP processing failed: " + url, e);
		} finally {
			deleteTempFile(tempZipFile);
		}
		return extractedFiles;
	}

	public List<FileDTO> processSingleZipFile(FileDTO zipFileDTO) {
		List<FileDTO> extractedFiles = cacheFiles(zipFileDTO.getUrl(), CACHE_DIR);
		if (!extractedFiles.isEmpty()) {
			return extractedFiles;
		}
		File tempZipFile = Utils.downloadZipToFile(zipFileDTO.getUrl());
		try (ZipFile zipFile = new ZipFile(tempZipFile, StandardCharsets.UTF_8)) {
			List<Future<FileDTO>> futures = new ArrayList<>();

			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (shouldProcessEntry(entry)) {
					futures.add(ENTRY_EXECUTOR
							.submit(() -> processEntry(zipFile, entry, zipFileDTO.getUrl(), new File(CACHE_DIR),
									Utils.getFileName(zipFileDTO.getUrl()).replaceAll("[^a-zA-Z0-9.-]", "_") + "_")));
				}
			}

			for (Future<FileDTO> future : futures) {
				try {
					extractedFiles.add(future.get(5, TimeUnit.MINUTES));
				} catch (Exception e) {
					log.error("Entry processing failed", e);
				}
			}
		} catch (Exception e) {
			log.error("ZIP processing failed: " + zipFileDTO.getUrl(), e);
		} finally {
			deleteTempFile(tempZipFile);
		}

		return extractedFiles;
	}

	private List<FileDTO> cacheFiles(String url, String cacheDirPath) {
		List<FileDTO> cachedFilesList = new ArrayList<>();
		File cacheDir = new File(cacheDirPath);
		if (!cacheDir.exists()) {
			boolean created = cacheDir.mkdirs();
			if (!created) {
				log.error("Failed to create cache directory: " + cacheDir.getAbsolutePath());
				return cachedFilesList;
			}
		}
		String cachePrefix = Utils.getFileName(url).replaceAll("[^a-zA-Z0-9.-]", "_") + "_";

		File[] cachedFiles = cacheDir.listFiles(
				(dir, name) -> name.startsWith(cachePrefix) && (name.endsWith(".csv") || name.endsWith(".txt")));
		if (cachedFiles != null && cachedFiles.length > 0) {
			for (File cachedFile : cachedFiles) {
				if (!Utils.isFileOutdated(cachedFile)) {
					log.info("Using cached entry file: " + cachedFile.getAbsolutePath());
					FileDTO dto = new FileDTO();
					dto.setFilePath(cachedFile.getPath());
					dto.setUrl(url);
					dto.setTitle(cachedFile.getName().substring(cachePrefix.length()));
					cachedFilesList.add(dto);
				}
			}
		}
		return cachedFilesList;
	}

	private void deleteTempFile(File tempZipFile) {
		if (tempZipFile != null && tempZipFile.exists()) {
			boolean deleted = tempZipFile.delete();
			if (!deleted) {
				log.warn("Temporary ZIP file could not be deleted: " + tempZipFile.getAbsolutePath());
			}
		}
	}

	private boolean shouldProcessEntry(ZipEntry entry) {
		return !entry.isDirectory() && (entry.getName().endsWith(".csv") || entry.getName().endsWith(".txt"));
	}

	private FileDTO processEntry(ZipFile zipFile, ZipEntry entry, String url, File cacheDir, String cachePrefix) {
		File cachedEntryFile = new File(cacheDir, cachePrefix + entry.getName().replaceAll("[^a-zA-Z0-9.-]", "_"));
		try (InputStream is = zipFile.getInputStream(entry);
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8),
						BUFFER_SIZE);
				BufferedWriter writer = new BufferedWriter(new FileWriter(cachedEntryFile, StandardCharsets.UTF_8))) {

			char[] buffer = new char[1024 * 1024];
			int charsRead;
			while ((charsRead = reader.read(buffer)) != -1) {
				writer.write(buffer, 0, charsRead);
			}

			log.info("Saved entry to cache: " + cachedEntryFile.getAbsolutePath());

			FileDTO dto = new FileDTO();
			dto.setContent(null); // Content is not stored in memory to avoid heap overflow
			dto.setUrl(url);
			dto.setTitle(entry.getName());
			dto.setFilePath(cachedEntryFile.getAbsolutePath());

			return dto;
		} catch (IOException e) {
			log.error("Failed processing entry: " + entry.getName(), e);
			return null;
		}
	}

	public FileDTO processSingleCsvFile(String fileName, String url) {
		FileDTO dto = new FileDTO();
		dto.setTitle(fileName);

		File cacheDir = new File(CACHE_DIR);

		String cachePrefix = Utils.getFileName(url).replaceAll("[^a-zA-Z0-9.-]", "_") + "_";

		File[] cachedFiles = cacheDir.listFiles(
				(dir, name) -> name.startsWith(cachePrefix) && (name.endsWith(".csv") || name.endsWith(".txt")));

		if (cachedFiles != null && cachedFiles.length > 0) {
			for (File cachedFile : cachedFiles) {
				if (cachedFile.getName().contains(fileName)) {
					dto.setFilePath(cachedFile.getAbsolutePath());
				}
			}
		}
		return dto;
	}

	public List<Measurement> extractValuesFromStation(List<FileDTO> extractedContentFiles, String stationId,
			String targetFile) {
		FileDTO valuesFile = extractedContentFiles.stream()
				.filter(file -> file.getTitle() != null && file.getTitle().contains(targetFile)).findFirst()
				.orElse(null);

		if (valuesFile == null || valuesFile.getFilePath() == null) {
			return Collections.emptyList();
		}

		List<Measurement> values = new ArrayList<>();
		DecimalFormat decimalFormat = new DecimalFormat();
		decimalFormat.setParseBigDecimal(true);
		decimalFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMAN));

		try (BufferedReader reader = new BufferedReader(new StringReader(valuesFile.getFilePath()))) {
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				String[] fields = line.split(";", -1);
				if (fields.length < 4 || !fields[0].equals(stationId)) {
					continue;
				}

				try {
					Double value = null;
					if (!fields[2].isEmpty()) {
						value = decimalFormat.parse(fields[2]).doubleValue();
					}
					values.add(new Measurement(fields[0], fields[1], value));
				} catch (ParseException e) {
					log.error("Skipping invalid numeric value in line: " + line);
				}
			}
		} catch (Exception e) {
			log.error("Error processing file content: " + e.getMessage());
		}

		return values;
	}

	public List<Measurement> extractGrundWasserStand(List<FileDTO> extractedContentFiles, String stationId,
			String targetFile) {
		FileDTO valuesFile = extractedContentFiles.stream()
				.filter(file -> file.getTitle() != null && file.getTitle().contains(targetFile)).findFirst()
				.orElse(null);

		if (valuesFile == null || valuesFile.getFilePath() == null) {
			return Collections.emptyList();
		}
		List<Measurement> values = new ArrayList<>();
		DecimalFormat decimalFormat = new DecimalFormat();
		decimalFormat.setParseBigDecimal(true);
		decimalFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMAN));

		try (BufferedReader reader = new BufferedReader(new FileReader(valuesFile.getFilePath()), BUFFER_SIZE)) {
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				String[] fields = line.split(";", -1);
				if (fields.length < 7 || !fields[1].equals(stationId)) {
					continue;
				}

				try {
					Double wasserstd_m = null;
					if (!fields[6].isEmpty()) {
						wasserstd_m = decimalFormat.parse(fields[6]).doubleValue();
					}
					values.add(new Measurement(fields[1], fields[3], wasserstd_m));
				} catch (ParseException e) {
					log.error("Skipping invalid wasserstd_m in line: " + line);
				}
			}
		} catch (Exception e) {
			log.error("Error processing file content: " + e.getMessage());
		}
		return values;
	}

	public List<String> extractGrundWasser(List<FileDTO> extractedContentFiles, String stationId, String targetFile) {
		FileDTO valuesFile = extractedContentFiles.stream()
				.filter(file -> file.getTitle() != null && file.getTitle().contains(targetFile)).findFirst()
				.orElse(null);

		if (valuesFile == null || valuesFile.getFilePath() == null) {
			return Collections.emptyList();
		}
		List<String> values = new ArrayList<>();
		DecimalFormat decimalFormat = new DecimalFormat();
		decimalFormat.setParseBigDecimal(true);
		decimalFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMAN));

		try (BufferedReader reader = new BufferedReader(new FileReader(valuesFile.getFilePath()), BUFFER_SIZE)) {
			String header = reader.readLine();
			if (header != null) {
				values.add(header);
			}

			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				String[] fields = line.split(";", -1);
				if (fields.length < 7 || !fields[1].equals(stationId)) {
					continue;
				}
				values.add(line);
			}
		} catch (Exception e) {
			log.error("Error processing file content: " + e.getMessage());
		}
		return values;
	}

	public List<WetterStationDTO> extractStationsFromFile(List<FileDTO> extractedContentFiles) {
		List<WetterStationDTO> stations = new ArrayList<>();
		FileDTO stationFile = extractedContentFiles.stream()
				.filter(file -> file.getTitle() != null && file.getTitle().contains("stationen.txt")).findFirst()
				.orElse(null);

		if (stationFile == null || stationFile.getFilePath() == null) {
			return stations;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(stationFile.getFilePath()))) {
			reader.readLine();
			String line;
			int lineNumber = 1;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				String[] fields = line.split(";", -1);
				if (fields.length < 4) {
					log.error("Skipping invalid line " + lineNumber + ": " + line);
					continue;
				}

				try {
					WetterStationDTO station = new WetterStationDTO();
					station.setLatitude(Double.parseDouble(fields[0].trim()));
					station.setLongitude(Double.parseDouble(fields[1].trim()));
					station.setName(fields[2]);
					station.setStationId(fields[3]);
					station.setProvider(fields[5]);
					stations.add(station);
				} catch (NumberFormatException e) {
					log.error("Error parsing line " + lineNumber + ": " + line);
				}
			}
		} catch (Exception e) {
			log.error("Error processing file content: " + e.getMessage());
		}

		return stations;
	}

	public List<WetterStationDTO> extractGrundWasserStationsFromFile(List<FileDTO> extractedContentFiles) {
		return extractedContentFiles.parallelStream()
				.filter(file -> file.getTitle() != null && file.getTitle().contains("messstelle"))
				.flatMap(file -> extractStationsFromSingleFile(file).stream()).collect(Collectors.toList());
	}

	private List<WetterStationDTO> extractStationsFromSingleFile(FileDTO stationFile) {
		List<WetterStationDTO> stations = new ArrayList<>();
		if (stationFile == null || stationFile.getFilePath() == null) {
			return stations;
		}

		CoordinateReferenceSystem srcCrs = crsFactory.createFromName("EPSG:25832");
		CoordinateReferenceSystem dstCrs = crsFactory.createFromName("EPSG:4326");
		CoordinateTransform transform = ctFactory.createTransform(srcCrs, dstCrs);

		try (BufferedReader reader = new BufferedReader(new FileReader(stationFile.getFilePath()))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return stations;
			}
			Map<String, Integer> indexMap = Utils.mapHeaderIndexes(headerLine);

			int idxId = indexMap.getOrDefault("messstelle_id", -1);
			int idxName = indexMap.getOrDefault("name", -1);
			int idxE32 = indexMap.getOrDefault("e32", -1);
			int idxN32 = indexMap.getOrDefault("n32", -1);
			int idxBetreiber = indexMap.getOrDefault("betreiber", -1);

			if (idxId == -1 || idxName == -1 || idxE32 == -1 || idxN32 == -1) {
				log.error("Missing required columns in file: " + stationFile.getTitle());
				return stations;
			}

			ProjCoordinate srcCoord = new ProjCoordinate();
			ProjCoordinate dstCoord = new ProjCoordinate();
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}

				String[] fields = Utils.fastSplit(line, ';');
				if (fields.length <= Math.max(idxN32, idxE32)) {
					continue;
				}

				String eastingStr = fields[idxE32];
				String northingStr = fields[idxN32];
				if (eastingStr.contains("XX") || northingStr.contains("XX")) {
					continue;
				}

				try {
					srcCoord.x = Double.parseDouble(eastingStr);
					srcCoord.y = Double.parseDouble(northingStr);
					transform.transform(srcCoord, dstCoord);

					WetterStationDTO station = new WetterStationDTO();
					station.setLatitude(dstCoord.y);
					station.setLongitude(dstCoord.x);
					station.setName(Utils.encodeUTF8(fields[idxName]));
					station.setStationId(fields[idxId]);
					station.setProvider(Utils
							.encodeUTF8(idxBetreiber >= 0 && idxBetreiber < fields.length ? fields[idxBetreiber] : ""));

					stations.add(station);
				} catch (Exception ex) {
					log.error("Skipping invalid coordinate in file " + stationFile.getTitle() + ": " + ex.getMessage());
				}
			}
		} catch (Exception e) {
			log.error("Error processing file " + stationFile.getTitle() + ": " + e.getMessage());
		}

		return stations;
	}

	private void scheduleCacheCleanup(String cachDir) {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> {
			File cacheDir = new File(cachDir);
			File[] files = cacheDir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (Utils.isFileOutdated(file)) {
						boolean deleted = file.delete();
						if (!deleted) {
							log.warn("Failed to delete outdated cache file: " + file.getAbsolutePath());
						}
					}
				}
			}
		}, 0, 24, TimeUnit.HOURS);
	}

}
