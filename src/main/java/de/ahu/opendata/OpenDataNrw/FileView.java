package de.ahu.opendata.OpenDataNrw;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.vaadin.componentfactory.pdfviewer.PdfViewer;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.StreamResource;

import de.ahu.opendata.Utils.SpringApplicationContext;

@SuppressWarnings("serial")
public class FileView extends VerticalLayout {
	private Grid<String[]> grid = new Grid<>();
	private List<String[]> csvData = new ArrayList<>();
	private List<String> headers = new ArrayList<>();
	private Map<String, String> savedHeaders = new HashMap<>();
	private FormLayout buttonLayout = new FormLayout();
	private Button saveButton;
	private Button btnDownloadFile = new Button(new Icon(VaadinIcon.CLOUD_DOWNLOAD_O));

	private HeaderRow headerRow = grid.appendHeaderRow();
	private PdfViewer viewer;
	private OpenDataNrwService openDataNrwService;

	public FileView(InputStream fileInputStream, FileDTO file) {
		openDataNrwService = SpringApplicationContext.getBean(OpenDataNrwService.class);
		setSizeFull();
		String fileName = file.getUrl();
		try {
			if (fileName.endsWith(".zip")) {
				displayZip(fileInputStream);
			} else if (fileName.endsWith(".csv")) {
				displayCsv(fileInputStream);
			} else if (fileName.endsWith(".xlsx")) {
				displayXlsx(fileInputStream);
			} else if (fileName.endsWith(".txt")) {
				displayTxt(fileInputStream);
			} else if (fileName.endsWith(".pdf")) {
				displayDpf(fileInputStream, fileName);
			} else {
				throw new IllegalArgumentException("Unsupported file format: " + fileName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		btnDownloadFile.addThemeVariants(ButtonVariant.LUMO_ICON);
		saveButton = new Button("Save CSV", e -> saveFilteredCsv());
		saveButton.setEnabled(true);

		StreamResource resource = new StreamResource(
				file.getUrl() != null ? file.getUrl().substring(file.getUrl().lastIndexOf('/') + 1) : "download",
				() -> openDataNrwService.fetchFileContent(file.getUrl()));
		btnDownloadFile.setText(
				file.getUrl() != null ? file.getUrl().substring(file.getUrl().lastIndexOf('/') + 1) : file.getPath());
		Anchor downloadLink = new Anchor(resource, "");
		downloadLink.getElement().setAttribute("download", true);
		downloadLink.add(btnDownloadFile);

		add(downloadLink, grid);
	}

	private void displayZip(InputStream fileInputStream) throws IOException, CsvException {
		try (ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
			ZipEntry entry;
			while ((entry = zipInputStream.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}

				String entryName = entry.getName().toLowerCase();
				if (entryName.endsWith(".csv") || entryName.endsWith(".txt") || entryName.endsWith(".xlsx")) {

					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte[] buffer = new byte[1024];
					int bytesRead;
					while ((bytesRead = zipInputStream.read(buffer)) != -1) {
						baos.write(buffer, 0, bytesRead);
					}
					ByteArrayInputStream entryInputStream = new ByteArrayInputStream(baos.toByteArray());

					if (entryName.endsWith(".csv")) {
						displayCsv(entryInputStream);
					} else if (entryName.endsWith(".txt")) {
						displayTxt(entryInputStream);
					} else if (entryName.endsWith(".xlsx")) {
						displayXlsx(entryInputStream);
					}
					zipInputStream.closeEntry();
					return;
				}
				zipInputStream.closeEntry();
			}
			throw new IllegalArgumentException("No supported files (CSV, TXT, XLSX) found in the ZIP archive.");
		}
	}

	public void displayDpf(InputStream fileInputStream, String fileName) {
		byte[] content = readInputStreamToByteArray(fileInputStream);
		String fileNameWithExtension = FilenameUtils.getBaseName(fileName);
		StreamResource resource = new StreamResource(fileNameWithExtension, () -> new ByteArrayInputStream(content));
		grid.setVisible(false);
		btnDownloadFile.setVisible(false);
		add(createPdfViewer(resource));
	}

	private PdfViewer createPdfViewer(StreamResource resource) {
		this.viewer = new PdfViewer();
		viewer.setSrc(resource);
		viewer.setSizeFull();
		viewer.setAddDownloadButton(false);
		return viewer;
	}

	private byte[] readInputStreamToByteArray(InputStream inputStream) {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			return outputStream.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException("Error reading input stream", e);
		}
	}

	private void displayTxt(InputStream resourceAsStream) throws IOException {
		List<String[]> data = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split(";");
				if (columns.length > 1) {
					data.add(columns);
				}
			}
		}

		if (data.isEmpty())
			return;

		csvData = data;
		headers = new ArrayList<>(Arrays.asList(csvData.get(0)));

		if (csvData.size() > 1) {
			updateGrid();
		}
	}

	private void displayCsv(InputStream resourceAsStream) throws CsvException {
		CSVParser parser = new CSVParserBuilder().withSeparator(';').build();
		CSVReader reader = new CSVReaderBuilder(new InputStreamReader(resourceAsStream)).withCSVParser(parser).build();

		try {
			csvData = reader.readAll();
			if (csvData.isEmpty())
				return;

			headers = new ArrayList<>(Arrays.asList(csvData.get(0)));
			applySavedHeaders();
			updateGrid();
			updateColumnButtons();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void displayXlsx(InputStream fileInputStream) throws IOException {
		List<String[]> data = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(fileInputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			for (Row row : sheet) {
				List<String> rowData = new ArrayList<>();
				for (Cell cell : row) {
					rowData.add(cell.toString());
				}
				data.add(rowData.toArray(new String[0]));
			}
		}
		if (data.isEmpty())
			return;

		csvData = data;
		headers = new ArrayList<>(Arrays.asList(csvData.get(0)));
		updateGrid();
	}

	private void updateGrid() {
		grid.removeAllColumns();
		grid.getHeaderRows().clear();
		grid.getColumns().stream().forEach(col -> col.setResizable(true));
		for (int i = 0; i < headers.size(); i++) {
			int colIndex = i;

			String originalHeader = csvData.get(0)[colIndex];
			String newHeader = savedHeaders.getOrDefault(originalHeader, headers.get(i)); // Use saved mapping

			// Editable header text field
			TextField headerField = new TextField();
			headerField.setValue(newHeader);
			headerField.addBlurListener(event -> {
				headers.set(colIndex, headerField.getValue());
				savedHeaders.put(originalHeader, headerField.getValue());
				// saveHeadersToFile(); // Persist headers
				updateGrid();
			});
			Grid.Column<String[]> column = grid.addColumn(row -> (colIndex < row.length) ? row[colIndex] : "")
					.setHeader(headerField);

			headerRow.getCell(column).setComponent(headerField);
		}

		grid.setItems(csvData.stream().filter(row -> row.length >= headers.size()).toList());

	}

	private void updateColumnButtons() {
		buttonLayout.removeAll();

		for (String header : headers) {
			Button removeButton = new Button("Remove " + header, click -> {
				removeColumn(header);
			});
			buttonLayout.add(removeButton);
		}
	}

	private void removeColumn(String columnName) {
		int colIndex = headers.indexOf(columnName);
		if (colIndex != -1) {
			headers.remove(colIndex);

			for (int i = 0; i < csvData.size(); i++) {
				String[] row = csvData.get(i);
				if (row.length > colIndex) {
					List<String> updatedRow = new ArrayList<>(Arrays.asList(row));
					updatedRow.remove(colIndex);
					csvData.set(i, updatedRow.toArray(new String[0]));
				}
			}
			updateGrid();
			updateColumnButtons();
		}
	}

	private void saveFilteredCsv() {
		String fileName = "filtered_data_" + System.currentTimeMillis() + ".csv";
		try (CSVWriter writer = new CSVWriter(new FileWriter(fileName))) {
			csvData.set(0, headers.toArray(new String[0]));

			writer.writeAll(csvData);
			System.out.println("Saved new CSV: " + fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void applySavedHeaders() {
		for (int i = 0; i < headers.size(); i++) {
			String originalHeader = csvData.get(0)[i];
			if (savedHeaders.containsKey(originalHeader)) {
				headers.set(i, savedHeaders.get(originalHeader));
			}
		}
	}
}
