package de.ahu.opendata.OpenDataNrw;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;

import com.vaadin.flow.data.provider.hierarchy.TreeData;

import de.ahu.opendata.Utils.Utils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SearchOpenGeoDatenService {

	public Map<String, List<String>> clearFiles(List<FileDTO> zipFiles, Map<String, List<String>> selectedColumns) {
		Map<String, List<String>> mapTableWithColumns = new HashMap<>();
		Iterator<Map.Entry<String, List<String>>> iterator = selectedColumns.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, List<String>> et = iterator.next();

			for (FileDTO zipFile : zipFiles) {
				try {
					URL zipUrl = URI.create(zipFile.getUrl()).toURL();
					try (ZipInputStream zis = new ZipInputStream(zipUrl.openStream())) {
						ZipEntry entry;
						while ((entry = zis.getNextEntry()) != null) {
							if (!entry.isDirectory() && entry.getName().endsWith(".csv")
									|| !entry.isDirectory() && entry.getName().endsWith(".txt")) {
								String tableName = entry.getName();

								if (tableName.equals(et.getKey())) {
									mapTableWithColumns.put(et.getKey(), et.getValue());
								}
							}
						}
					}

				} catch (IOException e) {
					Utils.showHinweisBox("Fehler beim Entpacken der Datei: " + zipFile.getUrl());
					e.printStackTrace();
				}
			}
		}
		return mapTableWithColumns;
	}

	public void removeEmptyEntry(Map<String, List<String>> selectedColumns) {
		Iterator<Map.Entry<String, List<String>>> iterator = selectedColumns.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, List<String>> entry = iterator.next();
			if (entry.getValue().isEmpty()) {
				iterator.remove();
			}
		}
	}

	public ByteArrayOutputStream mergeSelectedColumns(List<String> reorderedHeaders,
			Map<String, List<String>> selectedColumns, Set<String> selectedFiles) {
		String stationFileName = null;
		String measurementFileName = null;
		for (String tableName : selectedColumns.keySet()) {
			if (tableName.contains("_stationen")) {
				stationFileName = tableName;
			} else if (tableName.contains("_messwerte")) {
				measurementFileName = tableName;
			} else {
				measurementFileName = tableName;
			}
		}

		String headerRow = String.join(";", reorderedHeaders) + "\n";
		Map<String, Map<String, Integer>> columnIndices = new HashMap<>(); // tableName -> (columnName -> index)
		Map<String, String[]> stationData = new HashMap<>(); // station_no -> row
		Map<String, List<String[]>> measurementData = new HashMap<>(); // station_no -> list of rows

		for (String zipFile : selectedFiles) {
			try {
				URL zipUrl = URI.create(zipFile.strip()).toURL();
				try (ZipInputStream zis = new ZipInputStream(zipUrl.openStream())) {
					ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						if (!entry.isDirectory()
								&& (entry.getName().endsWith(".csv") || entry.getName().endsWith(".txt"))) {
							String tableName = entry.getName();
							if (!tableName.equals(stationFileName) && !tableName.equals(measurementFileName)) {
								continue;
							}
							ByteArrayOutputStream baos = Utils.readByteArrayOutputStream(zis, entry.getSize());

							if (selectedColumns.get(tableName) == null || selectedColumns.get(tableName).isEmpty()) {
								continue;
							}
							String[] lines = baos.toString("UTF-8").split("\n");
							if (lines.length == 0) {
								continue;
							}

							String[] headers = lines[0].split(";");
							Map<String, Integer> headerIndexMap = new HashMap<>();
							for (int i = 0; i < headers.length; i++) {
								headerIndexMap.put(headers[i].trim(), i);
							}
							columnIndices.put(tableName, headerIndexMap);

							int stationNoIndex = headerIndexMap.getOrDefault("station_no", -1);

							for (int i = 1; i < lines.length; i++) {
								String[] row = lines[i].split(";");

								String key = stationNoIndex != -1 && row.length > stationNoIndex
										? row[stationNoIndex].trim()
										: "row_" + i;

								List<String> selectedValues = new ArrayList<>();
								for (String selectedCol : selectedColumns.get(tableName)) {
									int idx = headerIndexMap.getOrDefault(selectedCol, -1);
									selectedValues.add(idx != -1 && idx < row.length ? row[idx].trim() : "");
								}
								String[] selectedRow = selectedValues.toArray(new String[0]);

								if (tableName.equals(stationFileName)) {
									stationData.put(key, selectedRow);
								} else if (tableName.equals(measurementFileName)) {
									measurementData.computeIfAbsent(key, k -> new ArrayList<>()).add(selectedRow);
								}
							}
						}
					}
				}
			} catch (IOException e) {
				log.info("Fehler beim Lesen der Datei: " + zipFile);
				e.printStackTrace();
				return null;
			}
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
			writer.write(headerRow);
			if (stationFileName != null && measurementFileName != null) {
				for (Map.Entry<String, List<String[]>> entry : measurementData.entrySet()) {
					String key = entry.getKey();
					List<String[]> measurementRows = entry.getValue();
					String[] stationRow = stationData.get(key);

					for (String[] measurementRow : measurementRows) {
						List<String> rowValues = new ArrayList<>();
						for (String header : reorderedHeaders) {
							String value = "";
							if (stationRow != null) {
								List<String> stationCols = selectedColumns.get(stationFileName);
								if (stationCols.contains(header)) {
									int originalIndex = stationCols.indexOf(header);
									if (originalIndex >= 0 && originalIndex < stationRow.length) {
										value = stationRow[originalIndex];
									}
								}
							}
							if (value.isEmpty()) {
								List<String> measurementCols = selectedColumns.get(measurementFileName);
								if (measurementCols.contains(header) && !header.equalsIgnoreCase("station_no")) {
									int originalIndex = measurementCols.indexOf(header);
									if (originalIndex >= 0 && originalIndex < measurementRow.length) {
										value = measurementRow[originalIndex];
									}
								}
							}
							rowValues.add(value);
						}
						writer.write(String.join(";", rowValues) + "\n");
					}
				}
			} else {
				Map<String, List<String[]>> dataToProcess = stationFileName != null
						? stationData.entrySet().stream().collect(
								Collectors.toMap(Map.Entry::getKey, e -> Collections.singletonList(e.getValue())))
						: measurementData;
				String fileName = stationFileName != null ? stationFileName : measurementFileName;

				for (Map.Entry<String, List<String[]>> entry : dataToProcess.entrySet()) {
					List<String[]> rows = entry.getValue();
					for (String[] row : rows) {
						List<String> rowValues = new ArrayList<>();
						for (String header : reorderedHeaders) {
							String value = "";
							List<String> cols = selectedColumns.get(fileName);
							if (cols.contains(header)) {
								int originalIndex = cols.indexOf(header);
								if (originalIndex >= 0 && originalIndex < row.length) {
									value = row[originalIndex];
								}
							}
							rowValues.add(value);
						}
						writer.write(String.join(";", rowValues) + "\n");
					}
				}
			}

		}
		return baos;
	}

	public TreeData<FileDTO> fillTreeData(List<FileDTO> zipFiles, TreeData<FileDTO> treeData) {
		for (FileDTO zipFile : zipFiles) {
			try {
				URL zipUrl = URI.create(zipFile.getUrl()).toURL();

				FileDTO zipNode = new FileDTO(Utils.extractFileName(zipFile), zipFile.getUrl());
				treeData.addItem(null, zipNode);
				try (ZipInputStream zis = new ZipInputStream(zipUrl.openStream())) {
					ZipEntry entry;
					while ((entry = zis.getNextEntry()) != null) {
						if (!entry.isDirectory() && entry.getName().endsWith(".csv")
								|| !entry.isDirectory() && entry.getName().endsWith(".txt")) {
							ByteArrayOutputStream baos = Utils.readByteArrayOutputStream(zis, entry.getSize());

							FileDTO fileNode = new FileDTO(entry.getName(), null);
							fileNode.setContent(baos.toString("UTF-8"));

							treeData.addItem(zipNode, fileNode);
						}
					}
				}
			} catch (IOException e) {
				Utils.showHinweisBox("Fehler beim Entpacken der Datei: " + zipFile.getUrl());
				e.printStackTrace();
			}
		}
		return treeData;
	}

}
