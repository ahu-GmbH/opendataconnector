package de.ahu.opendata.Abonnement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.stream.Streams;

import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.shared.Registration;

import de.ahu.opendata.DataUtils.DataEvents;
import de.ahu.opendata.DataUtils.RestDTO;
import de.ahu.opendata.Utils.SpringApplicationContext;
import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Wetterdienst.WetterDatenDTO;

@SuppressWarnings("serial")
public class AbonnementDialogView extends VerticalLayout {
	private Registration eventListener;

	private Select<String> ddFileTyp = new Select<String>();
	private TextField tfStationName = new TextField();
	private TextField tfParameter = new TextField();
	private FormLayout formLayout = new FormLayout();
	private DatePicker dpStartDate = new DatePicker();
	private DatePicker dpEndDate = new DatePicker();
	private TextField tfStation = new TextField();
	private Dialog abonnementDialog;

	private BeanValidationBinder<Abonnement> aboBinder = new BeanValidationBinder<>(Abonnement.class);
	private Abonnement abonnement = new Abonnement();

	private Button btnSpeichern = new Button("Speichern");
	private String paramater = "";
	private String startDate = "";
	private String endDate = "";
	private String id = "";
	private String stationName = "";
	private String stationId = "";
	private AbonnementService abonnementService;

	private String description;
	private String baseUrl;
	private List<WetterDatenDTO> dtoList = new ArrayList<>();

	@SuppressWarnings("unchecked")
	public AbonnementDialogView(String route, String network) {
		super();
		this.description = route;
		this.abonnementService = SpringApplicationContext.getBean(AbonnementService.class);
		this.baseUrl = network;

		addAttachListener(attach -> {
			this.eventListener = ComponentUtil.addListener(UI.getCurrent(),
					(Class<DataEvents.DataSaveEvent<RestDTO>>) (Class<?>) DataEvents.DataSaveEvent.class,
					(ComponentEventListener<DataEvents.DataSaveEvent<RestDTO>>) event -> {
						RestDTO firstDto = event.getDtoEntity().getFirst();

						if (firstDto instanceof WetterDatenDTO wetterDaten) {
							paramater = wetterDaten.getParameter();
							startDate = wetterDaten.getDate();
							id = wetterDaten.getId();
							stationId = wetterDaten.getStationId();

							RestDTO lastDto = event.getDtoEntity().getLast();
							endDate = ((WetterDatenDTO) lastDto).getDate();

							dtoList.addAll(List.of((WetterDatenDTO) event.getDtoEntity().getFirst(),
									(WetterDatenDTO) event.getDtoEntity().getLast()));
						}

						initDialog();
					});

		});

		addDetachListener(detach -> {
			if (this.eventListener != null) {
				this.eventListener.remove();
			}
		});
	}

	private void initDialog() {
		Abonnement abo = abonnementService.checkAlreadySubscribed(dtoList);

		String showMessage = abo != null ? "Abonnement erfolgreich aktualisiert."
				: "Ein neues Abonnement erfolgreich erstellt.";

		initBinder();

		if (abonnementDialog != null) {
			abonnementDialog.removeFromParent();
		}
		abonnementDialog = new Dialog();
		abonnementDialog.open();
		abonnementDialog.setHeaderTitle("Abonnement Information:");
		formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));

		ddFileTyp.setLabel("Wähle bitte ein Dateiformat aus");
		ddFileTyp.setItems(List.of(".json", ".csv", ".txt", "ahuManagerformat"));
		ddFileTyp.setValue(abo != null ? abo.getDateiFormat() : null);

		tfParameter.setLabel("Parameter");
		if (paramater != null) {
			tfParameter.setValue(paramater);
		}

		dpStartDate.setLabel("Startdatum");
		dpEndDate.setLabel("Enddatum");

		if (startDate != null && endDate != null) {
			dpStartDate.setValue(Utils.parseGermanDate(startDate));
			dpEndDate.setValue(Utils.parseGermanDate(endDate));
		}

		tfStation.setLabel("Stationnr.");
		if (id != null) {
			tfStation.setValue(id);
		}

		tfStationName.setLabel("Stationbezeichnung");
		if (stationId != null) {
			tfStationName.setValue(stationId);
		}

		Streams.of(tfStationName, tfParameter, tfStation).forEach(tf -> tf.setReadOnly(true));

		formLayout.add(tfStationName, tfStation, tfParameter, dpStartDate, dpEndDate, ddFileTyp);
		formLayout.setColspan(ddFileTyp, 2);
		formLayout.setColspan(tfParameter, 2);
		formLayout.setColspan(tfStation, 2);

		btnSpeichern.getStyle().set("margin-top", "20px");
		btnSpeichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		btnSpeichern.addClickListener(click -> {

			List<String> ids = Arrays.asList(aboBinder.getBean().getLocationId().split(","));
			List<String> labels = Arrays.asList(aboBinder.getBean().getLabel().split(","));
			aboBinder.getBean().setLabel(stationId);

			for (int i = 0; i < ids.size(); i++) {
				Abonnement newAbo = new Abonnement();
				newAbo.setLocationId(ids.get(i));
				newAbo.setLabel(labels.get(i));

				if (description != "") {
					newAbo.setDescription(description + "-Dwd-Wetterdienst");
				}
				newAbo.setUrl(baseUrl);
				newAbo.setParameter(aboBinder.getBean().getParameter());
				newAbo.setDateiFormat(aboBinder.getBean().getDateiFormat());
				newAbo.setStartDatum(aboBinder.getBean().getStartDatum());
				newAbo.setEndDatum(aboBinder.getBean().getEndDatum());

				if (aboBinder.isValid()) {
					abonnementService.createOrUpdateAbonnement(newAbo);
					Utils.showSuccessBox(showMessage);
					abonnementDialog.close();
				} else {
					aboBinder.validate();
				}
			}

		});

		HorizontalLayout buttonLayout = new HorizontalLayout(btnSpeichern);
		buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
		buttonLayout.setWidthFull();

		VerticalLayout dialogLayout = new VerticalLayout(formLayout, buttonLayout);
		dialogLayout.setPadding(true);
		dialogLayout.setSpacing(true);
		dialogLayout.setWidth("500px");

		abonnementDialog.add(dialogLayout);
		abonnementDialog.setWidth("550px");
	}

	private void initBinder() {
		aboBinder.setBean(abonnement);
		aboBinder.forField(ddFileTyp).withValidator(value -> value != null, "Dateiformat ist erforderlich")
				.asRequired("Bitte Dateiformat auswählen").bind(Abonnement::getDateiFormat, Abonnement::setDateiFormat);
		aboBinder.forField(tfStationName).asRequired("Bitte Bezeichnung eingeben").bind(Abonnement::getLabel,
				Abonnement::setLabel);
		aboBinder.forField(tfParameter).asRequired("Bitte Parameter eingeben").bind(Abonnement::getParameter,
				Abonnement::setParameter);
		aboBinder.forField(tfStation).asRequired("Bitte Station eingeben").bind(Abonnement::getLocationId,
				Abonnement::setLocationId);
		aboBinder.forField(dpStartDate).asRequired("Bitte Startdatum eingeben").bind(Abonnement::getStartDatum,
				Abonnement::setStartDatum);
		aboBinder.forField(dpEndDate).asRequired("Bitte Enddatum eingeben").bind(Abonnement::getEndDatum,
				Abonnement::setEndDatum);

	}

}
