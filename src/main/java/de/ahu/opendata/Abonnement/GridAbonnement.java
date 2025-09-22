package de.ahu.opendata.Abonnement;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;

import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.views.MainLayout;

@SuppressWarnings("serial")
@Route(value = "abo", layout = MainLayout.class)
public class GridAbonnement extends VerticalLayout {

	private AbonnementService abonnementService;
	private Grid<Abonnement> aboGrid = new Grid<>(Abonnement.class, false);

	private Select<String> ddFileTyp = new Select<String>();
	private TextField tfParameter = new TextField();
	private FormLayout formLayout = new FormLayout();
	private DatePicker dpStartDate = new DatePicker();
	private DatePicker dpEndDate = new DatePicker();
	private TextField tfStation = new TextField();

	private BeanValidationBinder<Abonnement> aboBinder = new BeanValidationBinder<>(Abonnement.class);

	private Button btnSpeichern = new Button("Speichern");
	private ListDataProvider<Abonnement> dataProvider;

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final String subscriptionUrl = "/api/subscription/data?id=";
	private final String subscriptionInrementalUrl = "/api/subscription/incremental_data?id=";

	public GridAbonnement() {
		this.abonnementService = SpringApplicationContext.getBean(AbonnementService.class);
		initGridAbonnement();
		scheduleDailyUpdate();
		setSizeFull();
	}

	public void initGridAbonnement() {
		List<Abonnement> abos = abonnementService.findAllAbonnements();
		abonnementService.updateStartDayOfAbonnement();
		dataProvider = new ListDataProvider<>(abos);
		aboGrid.setDataProvider(dataProvider);
		aboGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
		aboGrid.addClassNames(Margin.Right.MEDIUM);

		aboGrid.addColumn(Abonnement::getLabel).setHeader("Label").setSortable(true);
		aboGrid.addColumn(Abonnement::getUrl).setHeader("URL").setSortable(true).setAutoWidth(true);
		aboGrid.addColumn(Abonnement::getParameter).setHeader("Parameter/Datei").setSortable(true).setAutoWidth(true);
		aboGrid.addColumn(Abonnement::getDateiFormat).setHeader("Dateiformat").setSortable(true);
		aboGrid.addColumn(Abonnement::getLocationId).setHeader("Messstelle_Id").setSortable(true);
		aboGrid.addComponentColumn(abo -> {
			HorizontalLayout layout = new HorizontalLayout();
			layout.getThemeList().add("spacing");

			Button btnPrepare = new Button(new Icon(VaadinIcon.LINK));
			btnPrepare.setTooltipText("Abonnierte Daten abrufen");
			btnPrepare.addClickListener(event -> {
				Dialog dialogSub = new Dialog();

				dialogSub.setHeaderTitle("Abonnementtyp");

				HorizontalLayout buttonHl = new HorizontalLayout();
				buttonHl.setJustifyContentMode(JustifyContentMode.AROUND);
				buttonHl.setAlignItems(Alignment.STRETCH);

				Button btnFetchData = new Button("Alle Daten im angegebenen Zeitraum", new Icon(VaadinIcon.DOWNLOAD));
				btnFetchData.addClickListener(c -> {
					getUI().ifPresent(ui -> ui.getPage().open(subscriptionUrl + abo.getId(), "_blank"));
				});

				Button btnUpdateDate = new Button("Inkrementelle Updates", new Icon(VaadinIcon.DOWNLOAD_ALT));
				btnUpdateDate.addClickListener(c -> {
					getUI().ifPresent(ui -> ui.getPage().open(subscriptionInrementalUrl + abo.getId(), "_blank"));
				});

				buttonHl.add(btnFetchData, btnUpdateDate);

				dialogSub.add(buttonHl);
				dialogSub.open();
			});

			Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
			btnEdit.setTooltipText("Editieren");
			btnEdit.addClickListener(event -> {
				editAbonnement(abo);
			});

			Anchor downloadButton = new Anchor(subscriptionUrl + abo.getId(), "");
			downloadButton.getElement().setAttribute("download", "Abonnement-" + abo.getLabel() + ".csv");

			Button btnDownload = new Button(new Icon(VaadinIcon.DOWNLOAD));
			btnDownload.setTooltipText("Datensätze herunterladen");
			downloadButton.add(btnDownload);

			Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
			btnDelete.setTooltipText("Löschen");
			btnDelete.setClassName(LumoUtility.TextColor.ERROR);
			btnDelete.addClickListener(event -> {
				abonnementService.deleteAbonnementById(abo.getId());
				aboGrid.getDataProvider().refreshAll();
				aboGrid.setItems(abonnementService.findAllAbonnements());
				Utils.showSuccessBox("Abonnement erfolgreich gelöscht");
			});

			layout.add(downloadButton, btnPrepare, btnEdit, btnDelete);
			return layout;
		});

		aboGrid.setAllRowsVisible(true);

		Div container = new Div();
		container.add(new H4("Zentrale Abonnemen-Verwaltung"), aboGrid);
		container.addClassNames("p-l", "border", "rounded", "w-full");
		container.setWidthFull();

		add(container);
	}

	private Component editAbonnement(Abonnement abonnement) {
		initBinder(abonnement);
		Dialog editDialog = new Dialog();
		editDialog.open();
		editDialog.setHeaderTitle("Abonnement Information:");
		formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

		ddFileTyp.setLabel("Wähle bitte ein Dateiformat aus");
		ddFileTyp.setItems(List.of(".json", ".csv", ".txt", "ahuManagerformat"));
		ddFileTyp.setValue(abonnement.getDateiFormat());

		if (abonnement.getUrl().contains("opengeodata.nrw.de")) {
			tfParameter.setLabel("Datei");
		} else {
			tfParameter.setLabel("Parameter");
		}

		tfParameter.setValue(abonnement.getParameter() != null ? abonnement.getParameter() : "");
		tfParameter.setReadOnly(true);

		dpStartDate.setLabel("Startdatum");
		dpEndDate.setLabel("Enddatum");
		dpStartDate.setValue(abonnement.getStartDatum());
		dpEndDate.setValue(abonnement.getEndDatum());

		tfStation.setLabel("Stationnr.");
		tfStation.setValue(abonnement.getLocationId());
		tfStation.setReadOnly(true);

		formLayout.add(tfStation, tfParameter, dpStartDate, dpEndDate, ddFileTyp);
		formLayout.setColspan(ddFileTyp, 2);
		formLayout.setColspan(tfParameter, 2);
		formLayout.setColspan(tfStation, 2);

		btnSpeichern.getStyle().set("margin-top", "20px");
		btnSpeichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		btnSpeichern.addClickListener(click -> {
			if (aboBinder.writeBeanIfValid(new Abonnement())) {
				editDialog.close();
			}
			abonnementService.createOrUpdateAbonnement(aboBinder.getBean());
			editDialog.close();
			aboGrid.getDataProvider().refreshAll();
			aboGrid.setItems(abonnementService.findAllAbonnements());

			Utils.showSuccessBox("Abonnement erfolgreich gespeichert");
		});
		HorizontalLayout buttonLayout = new HorizontalLayout(btnSpeichern);
		buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		buttonLayout.setWidthFull();

		VerticalLayout dialogLayout = new VerticalLayout(formLayout, buttonLayout);
		dialogLayout.setPadding(true);
		dialogLayout.setSpacing(true);
		dialogLayout.setWidth("500px");

		editDialog.add(dialogLayout);
		editDialog.setWidth("550px");
		return editDialog;
	}

	private void initBinder(Abonnement abonnement) {
		aboBinder.setBean(abonnement);
		aboBinder.forField(ddFileTyp).asRequired("Bitte Dateiformat auswählen").bind(Abonnement::getDateiFormat,
				Abonnement::setDateiFormat);
		aboBinder.forField(tfParameter).asRequired("Bitte Parameter eingeben").bind(Abonnement::getParameter,
				Abonnement::setParameter);
		aboBinder.forField(tfStation).asRequired("Bitte Station eingeben").bind(Abonnement::getLocationId,
				Abonnement::setLocationId);
		aboBinder.forField(dpStartDate).asRequired("Bitte Startdatum eingeben").bind(Abonnement::getStartDatum,
				Abonnement::setStartDatum);
		aboBinder.forField(dpEndDate).asRequired("Bitte Enddatum eingeben").bind(Abonnement::getEndDatum,
				Abonnement::setEndDatum);

	}

	private void scheduleDailyUpdate() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime nextMidnight = now.truncatedTo(ChronoUnit.DAYS).plusDays(1);
		long initialDelay = ChronoUnit.SECONDS.between(now, nextMidnight);

		scheduler.scheduleAtFixedRate(() -> {
			try {
				UI.getCurrent().access(() -> {
					abonnementService.updateStartDayOfAbonnement();
					dataProvider.getItems().clear();
					dataProvider.getItems().addAll(abonnementService.findAllAbonnements());
					dataProvider.refreshAll();
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
	}

	@Override
	protected void onDetach(DetachEvent detachEvent) {
		// Shutdown scheduler when the view is detached
		scheduler.shutdown();
		super.onDetach(detachEvent);
	}

}
