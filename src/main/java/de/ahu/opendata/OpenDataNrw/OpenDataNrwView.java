package de.ahu.opendata.OpenDataNrw;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.router.Route;

import de.ahu.opendata.views.MainLayout;

@SuppressWarnings("serial")
@Route(value = "opendatanrw", layout = MainLayout.class)
public class OpenDataNrwView extends Div {

	public OpenDataNrwView() {

		GridOpenDataNrw gridOpenDataNrw = new GridOpenDataNrw();
		gridOpenDataNrw.setHeightFull();
		SearchOpenGeoDatenView processGeoDatenView = new SearchOpenGeoDatenView();
		processGeoDatenView.setHeightFull();

		TabSheet tabSheet = new TabSheet();
		tabSheet.addThemeVariants(TabSheetVariant.LUMO_TABS_EQUAL_WIDTH_TABS);
		tabSheet.add("Grid-OpenGeoData", gridOpenDataNrw);
		tabSheet.add("Suchprofil OpenGeoDataNrw", processGeoDatenView);
		tabSheet.setHeightFull();

		add(tabSheet);
		setSizeFull();

	}

}
