package de.ahu.opendata.Wetterdienst;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParameterNode {
	private String name;
	private int indent;
	private List<ParameterNode> children = new ArrayList<>();

	public ParameterNode(String name, int indent) {
		this.name = name;
		this.indent = indent;
	}

}
