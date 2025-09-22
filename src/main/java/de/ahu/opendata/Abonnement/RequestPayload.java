package de.ahu.opendata.Abonnement;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPayload {
	private List<String> relevanteFiles;
	private List<String> headers;
	private Map<String, List<String>> columns;
	private String format;
}
