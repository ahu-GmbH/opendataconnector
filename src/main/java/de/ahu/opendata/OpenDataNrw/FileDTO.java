package de.ahu.opendata.OpenDataNrw;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FileDTO {
	private String url;
	private String type;
	private String path;
	private String metadataId;
	private String lastModified;
	private String fileSize;
	private String title;
	private String content;
	private String filePath;

	public FileDTO(String path, String type, String url) {
		this.path = path;
		this.type = type;
		this.url = url;
	}

	public FileDTO(String title, String url) {
		this.title = title;
		this.url = url;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		FileDTO fileDTO = (FileDTO) o;
		return Objects.equals(title, fileDTO.title);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, path);
	}

	public InputStream getInputStream() {
		return new ByteArrayInputStream(this.content.getBytes(StandardCharsets.UTF_8));
	}
}
