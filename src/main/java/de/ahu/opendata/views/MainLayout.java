package de.ahu.opendata.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.BoxShadow;
import com.vaadin.flow.theme.lumo.LumoUtility.BoxSizing;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexDirection;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.FontWeight;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Height;
import com.vaadin.flow.theme.lumo.LumoUtility.ListStyleType;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import com.vaadin.flow.theme.lumo.LumoUtility.Overflow;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import com.vaadin.flow.theme.lumo.LumoUtility.Whitespace;
import com.vaadin.flow.theme.lumo.LumoUtility.Width;

import de.ahu.opendata.Abonnement.GridAbonnement;
import de.ahu.opendata.GovData.GridGovData;
import de.ahu.opendata.OpenDataNrw.OpenDataNrwView;
import de.ahu.opendata.Pegeldienst.PegelstandView;
import de.ahu.opendata.Search.SearchGeoDataView;
import de.ahu.opendata.Wetterdienst.HistoricalWetterDatenView;
import de.ahu.opendata.Wetterdienst.WettervorhersageView;

/**
 * The main view is a top-level placeholder for other views.
 */
@Layout
@AnonymousAllowed
public class MainLayout extends AppLayout {

	public MainLayout() {
		addToNavbar(createHeaderContent());
	}

	private Component createHeaderContent() {
		Header header = new Header();
		header.addClassNames(BoxSizing.CONTENT, Display.FLEX, FlexDirection.COLUMN, Width.FULL);

		Nav navMain = new Nav();
		navMain.addClassNames(Display.FLEX, Overflow.AUTO, Padding.Horizontal.MEDIUM, Padding.Vertical.XSMALL,
				TextColor.PRIMARY, BoxShadow.MEDIUM, Width.FULL);
		UnorderedList navMainItemList = new UnorderedList();
		navMainItemList.addClassNames(Display.INLINE_FLEX, Gap.LARGE, ListStyleType.NONE, Margin.NONE, Padding.SMALL,
				TextColor.PRIMARY);
		navMain.add(navMainItemList);

		for (MenuItemInfo menuItem : createMainMenuItems()) {
			navMainItemList.add(menuItem);
		}

		HorizontalLayout navControls = new HorizontalLayout();
		navControls.setSpacing(false);
		navControls.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
		navControls.add(navMain);

		header.add(navControls);
		return header;
	}

	private MenuItemInfo[] createMainMenuItems() {

		return new MenuItemInfo[] { new MenuItemInfo(null, VaadinIcon.HOME.create(), StartView.class),
				new MenuItemInfo(null, VaadinIcon.SEARCH.create(), SearchGeoDataView.class),
				new MenuItemInfo("Wettervorhersage", VaadinIcon.TABLE.create(), WettervorhersageView.class),
				new MenuItemInfo("Historische Wetterdaten", VaadinIcon.SPLINE_CHART.create(),
						HistoricalWetterDatenView.class),
				new MenuItemInfo("Pegelständedaten", VaadinIcon.DROP.create(), PegelstandView.class),
				new MenuItemInfo("GovData-Das Datenportal", VaadinIcon.DATABASE.create(), GridGovData.class),
				new MenuItemInfo("OpenGeodata.NRW", VaadinIcon.MODAL_LIST.create(), OpenDataNrwView.class),
				new MenuItemInfo("Abonnement-Verwaltung", VaadinIcon.LINES_LIST.create(), GridAbonnement.class) };
	}

	public static class MenuItemInfo extends ListItem {

		private final Class<? extends Component> view;

		public MenuItemInfo(String menuTitle, Component icon, Class<? extends Component> view) {
			this.view = view;

			RouterLink link = new RouterLink();
			link.addClassNames(Display.FLEX, Gap.XSMALL, Height.MEDIUM, AlignItems.CENTER, Padding.Horizontal.SMALL,
					TextColor.BODY);
			link.setRoute(view);

			Span text = new Span(menuTitle);
			text.addClassNames(FontWeight.MEDIUM, FontSize.MEDIUM, Whitespace.NOWRAP);

			if (icon != null) {
				link.add(icon);
			}
			link.add(text);
			add(link);
		}

		public Class<?> getView() {
			return view;
		}

	}

}
