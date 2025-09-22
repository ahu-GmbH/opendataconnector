package de.ahu.opendata.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import lombok.extern.slf4j.Slf4j;

@Route(value = "", layout = MainLayout.class)
@AnonymousAllowed
@SuppressWarnings({ "serial" })
@Slf4j
public class StartView extends VerticalLayout {

	public StartView() {
		H1 willkommen = new H1("Opendata-Konnektor für Geodaten");
		willkommen.addClassNames(LumoUtility.FontSize.LARGE, Margin.Bottom.MEDIUM);
		HorizontalLayout hLayout = new HorizontalLayout();
		hLayout.addClassNames(LumoUtility.AlignItems.CENTER,LumoUtility.JustifyContent.CENTER);
		hLayout.setWidthFull();
		hLayout.add(willkommen);
		setSizeFull();
		add(hLayout);
	}
}
