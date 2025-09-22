package de.ahu.opendata.ServiceUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.springframework.stereotype.Service;

import lombok.Getter;

@Service
public class ParameterPathFinderService {

	@Getter
	private static class Node {
		String name;
		List<Node> children;

		Node(String name) {
			this.name = name;
			this.children = new ArrayList<>();
		}
	}

	private Node root;

	public ParameterPathFinderService() {
		root = new Node("root");
		buildTreeFromFile();
	}

	private void buildTreeFromFile() {
		Stack<Node> stack = new Stack<>();
		stack.push(root);
		int prevIndent = -1;
		try (InputStream inputStream = this.getClass().getResourceAsStream("/parameter_list.txt");
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank())
					continue;

				int indent = getIndentLevel(line);
				String name = line.trim();

				Node node = new Node(name);
				while (indent <= prevIndent && !stack.isEmpty()) {
					stack.pop();
					prevIndent -= 1;
				}

				stack.peek().children.add(node);
				stack.push(node);
				prevIndent = indent;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int getIndentLevel(String line) {
		int count = 0;
		for (char c : line.toCharArray()) {
			if (c == ' ')
				count++;
			else
				break;
		}
		return count / 4;
	}

	public List<String> findAllParameterPaths(String parameter) {
		List<String> allPaths = new ArrayList<>();
		List<String> currentPath = new ArrayList<>();
		findParameter(root, parameter, currentPath, allPaths);
		return allPaths;
	}

	private void findParameter(Node node, String parameter, List<String> currentPath, List<String> allPaths) {
		// Skip root node in path
		if (!node.name.equals("root")) {
			currentPath.add(node.name);
		}

		// If parameter is found, add the current path to allPaths
		if (node.name.contains(parameter) && currentPath.size() == 3) {
			allPaths.add(String.join("/", currentPath));
		}

		// Recurse through children
		for (Node child : node.children) {
			findParameter(child, parameter, currentPath, allPaths);
		}

		// Backtrack by removing the current node from path
		if (!node.name.equals("root")) {
			currentPath.remove(currentPath.size() - 1);
		}
	}
}