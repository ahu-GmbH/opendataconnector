package de.ahu.opendata.ViewUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

@SuppressWarnings("serial")
public class ReformatCsvDialog extends VerticalLayout {
	private Grid<String[]> gridOpenNrw = null;

	public ReformatCsvDialog() {
		setHeightFull();
	}

	public Grid<String[]> createCsvPreviewGrid(String fileContentPath, String stationId) {
		if (gridOpenNrw != null) {
			gridOpenNrw.removeFromParent();
		}
		gridOpenNrw = new Grid<>();

		List<String[]> data = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(fileContentPath))) {
			String headerLine = reader.readLine();
			if (headerLine == null || headerLine.isEmpty()) {
				return gridOpenNrw;
			}

			String[] headers = headerLine.split(";");
			if (headers.length == 0) {
				return gridOpenNrw;
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String[] row = line.split(";");
				if (row.length > 0 && row[0].trim().equals(stationId)
						&& !Arrays.stream(row).skip(1).allMatch(String::isBlank)) {
					data.add(row);
				}
			}
			for (int i = 0; i < headers.length; i++) {
				final int columnIndex = i;
				final String headerName = headers[i].trim();
				gridOpenNrw.addColumn(row -> columnIndex < row.length ? row[columnIndex] : "").setHeader(headerName)
						.setResizable(true).setAutoWidth(true);
			}
			gridOpenNrw.setItems(data);
			gridOpenNrw.setSizeFull();
			add(gridOpenNrw);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return gridOpenNrw;
	}

}
