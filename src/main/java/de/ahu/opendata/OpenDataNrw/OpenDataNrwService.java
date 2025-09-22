package de.ahu.opendata.OpenDataNrw;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import de.ahu.opendata.Utils.Utils;
import de.ahu.opendata.Utils.WebUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Service
@Slf4j
public class OpenDataNrwService {
	private WebClient webClient;

	public OpenDataNrwService(@Value("${proxy.host}") String proxyHost, @Value("${proxy.port}") Integer proxyPort) {
		this.webClient = WebClient.builder()
				.exchangeStrategies(ExchangeStrategies.builder()
						.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(200 * 1024 * 1024)).build())
				.clientConnector(new ReactorClientHttpConnector(build(proxyHost, proxyPort))).build();
	}

	public HttpClient build(String proxyHost, Integer proxyPort) {
		if (proxyHost != null && proxyPort != null) {
			log.debug("HTTP-Client wird fuer Proxy konfiguriert. Proxy-Host: " + proxyHost + ", Port: "
					+ String.valueOf(proxyPort));
			return HttpClient.create()
					.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort));
		}
		return HttpClient.create();
	}

	public InputStream fetchXMLFromCatalog(String url) {
		String xmlContent = null;
		try {
			xmlContent = webClient.get().uri(url).retrieve().bodyToMono(String.class).block();
		} catch (Exception e) {
			WebUtils.handleWebClientException(url, e);
			return null;
		}
		if (xmlContent == null) {
			log.warn("Problem bei Fetchen des XML-Inhaltes: { }");
			return null;
		}
		String formattedXmlContent = xmlContent.replaceAll("xmlns=\"\"", "");
		return new ByteArrayInputStream(formattedXmlContent.getBytes(StandardCharsets.UTF_8));
	}

	public InputStream fetchFileContent(String url) {
		Flux<DataBuffer> fileContent = webClient.get().uri(url).retrieve().bodyToFlux(DataBuffer.class);
		byte[] fileBytes = DataBufferUtils.join(fileContent).map(dataBuffer -> {
			byte[] bytes = new byte[dataBuffer.readableByteCount()];
			dataBuffer.read(bytes);
			DataBufferUtils.release(dataBuffer);
			return bytes;
		}).block();
		return new ByteArrayInputStream(fileBytes);
	}

	public Map<String, String> fetchCategories(String url) {
		Map<String, String> categoriesMap = new HashMap<String, String>();
		String fetchData = WebUtils.fetchRemoteText(url);
		if (fetchData == null) {
			log.warn("Problem beim Fetchen der Daten");
			return categoriesMap;
		}
		JSONObject jsonObject = Utils.convertStringToJson(fetchData, JSONObject.class);
		JSONArray jsonFeatures = null;
		if (jsonObject.has("folders")) {
			jsonFeatures = jsonObject.getJSONArray("folders");
			for (int i = 0; i < jsonFeatures.length(); i++) {
				JSONObject feature = jsonFeatures.getJSONObject(i);
				String name = feature.has("name") ? feature.getString("name") : null;
				String title = feature.has("title") ? feature.getString("title") : null;
				categoriesMap.put(title, name);
			}
		}

		if (jsonObject.has("datasets")) {
			jsonFeatures = jsonObject.getJSONArray("datasets");
			return categoriesMap;
		}
		return categoriesMap;
	}

}
