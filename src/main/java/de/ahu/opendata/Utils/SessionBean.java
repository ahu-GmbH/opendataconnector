package de.ahu.opendata.Utils;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.ahu.opendata.GovData.GovDataService;
import de.ahu.opendata.OpenDataNrw.OpenDataNrwService;
import de.ahu.opendata.Pegeldienst.WasserstandService;
import de.ahu.opendata.ServiceUtils.GeolocationService;
import de.ahu.opendata.Wetterdienst.WetterdienstService;
import lombok.Getter;

@SpringComponent
@UIScope
@Getter
public class SessionBean {

	@Autowired
	private GovDataService govvDataService;

	@Autowired
	private OpenDataNrwService openDataNrwService;

	@Autowired
	private WetterdienstService wetterdienstService;

	@Autowired
	private WasserstandService wasserstandService;

	@Autowired
	private GeolocationService geolocationService;

}
